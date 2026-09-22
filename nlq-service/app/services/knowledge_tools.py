"""知识库检索工具：把「制度与流程 / 课程资料」带出处地接进 Agent。

设计要点：
- 索引驻内存、TTL 60s 过期重建：文档上传后 ≤1 分钟即可被检索到，无需外部刷新通知；
- 可见范围按角色过滤（制度=全员；课程资料=ADMIN 全量 / 教师本人任教课程 / 学生已选课程），
  与「③ 学生侧」的服务端身份注入口径一致——身份一律来自服务端注入的 role/user_id；
- 双份载荷：model_view（标题 + 摘要 + 出处）回喂模型，display（段落全文 + 出处字段）只上屏；
- 打分 = 余弦相似度 + 关键词重合度加权（课程号、实验室编号等精确词靠关键词兜底）。
"""

import time

import numpy as np

from app.config import settings
from app.services.embeddings import EmbeddingUnavailableError, embed_texts
from app.services.tool_registry import Tool, register_tool
from app.utils.db import db

_INDEX_TTL_SECONDS = 60
_MAX_TOP_K = 10
_KEYWORD_WEIGHT = 0.15
_SNIPPET_CHARS = 200

_SOURCE_LABELS = {"POLICY": "制度与流程", "COURSE": "课程资料"}

# 全量加载（不做权限过滤，返回时按可见范围裁剪）：一份索引快速切换不同角色的提问
_ALL_SQL = """
SELECT c.id, c.document_id, c.seq, c.heading, c.content, c.embedding,
       d.title, d.category, d.course_id, co.course_name
FROM knowledge_chunk c
JOIN knowledge_document d ON d.id = c.document_id
LEFT JOIN course co ON co.id = d.course_id
WHERE d.deleted = 0
ORDER BY c.document_id, c.seq
"""

_TEACHER_COURSE_SQL = "SELECT id FROM course WHERE teacher_id = %s"

_STUDENT_COURSE_SQL = """
SELECT cs.course_id FROM course_student cs
JOIN student s ON s.id = cs.student_id
WHERE s.user_id = %s
"""


class KnowledgeIndex:
    """带 TTL 的分段索引快照：一次加载（段落 + 向量矩阵），可见性在返回前裁剪。"""

    def __init__(self, ttl_seconds: int = _INDEX_TTL_SECONDS):
        self._ttl = ttl_seconds
        self._loaded_at = 0.0
        self._rows: list[dict] = []
        self._matrix: np.ndarray | None = None

    async def snapshot(self) -> tuple[list[dict], np.ndarray]:
        if self._matrix is None or (time.time() - self._loaded_at) > self._ttl:
            await self._reload()
        return self._rows, self._matrix

    async def _reload(self) -> None:
        rows = await db.execute(_ALL_SQL)
        dim = settings.embedding_dim
        kept: list[dict] = []
        vectors: list[np.ndarray] = []
        for row in rows:
            blob = row.pop("embedding", None)
            if not blob or len(blob) != dim * 4:
                # 维度不符（历史向量 / 脏数据）直接跳过，避免检索期崩在形状不匹配
                continue
            kept.append(row)
            vectors.append(np.frombuffer(blob, dtype="<f4"))
        self._rows = kept
        self._matrix = np.vstack(vectors) if vectors else np.zeros((0, dim), dtype="<f4")
        self._loaded_at = time.time()


_index = KnowledgeIndex()
# 可见课程集合缓存：角色 × 用户 → 课程 id 集合（None = 全部可见）
_course_cache: dict[str, tuple[float, set[int] | None]] = {}


async def _visible_course_ids(role: str, user_id: int) -> set[int] | None:
    """课程资料的可见课程集合；None 表示全部可见（ADMIN）。制度类不受此限制。

    与索引同 TTL 的短缓存：课程-人员关系变化（选课/换教师）一分钟内生效。
    """
    if role == "ADMIN":
        return None
    key = f"{role}:{user_id}"
    cached = _course_cache.get(key)
    if cached and (time.time() - cached[0]) <= _INDEX_TTL_SECONDS:
        return cached[1]

    if role == "TEACHER":
        rows = await db.execute(_TEACHER_COURSE_SQL, (user_id,))
    else:
        rows = await db.execute(_STUDENT_COURSE_SQL, (user_id,))
    ids = {int(r["id"] if "id" in r else r["course_id"]) for r in rows}
    _course_cache[key] = (time.time(), ids)
    return ids


def _bigram_containment(query: str, text: str) -> float:
    """查询的 2-gram 在段落文本中的出现比例：精确词（课程号/编号）的兜底信号。"""
    if len(query) < 2:
        return 1.0 if query and query in text else 0.0
    grams = {query[i : i + 2] for i in range(len(query) - 1)}
    hit = sum(1 for g in grams if g in text)
    return hit / len(grams)


def _snippet(content: str) -> str:
    text = content.strip().replace("\n", " ")
    return text if len(text) <= _SNIPPET_CHARS else text[:_SNIPPET_CHARS] + "…"


def _error(message: str) -> dict:
    return {"ok": False, "error": message}


# ---------------------------------------------------------------- knowledge_search


async def _knowledge_search_executor(
    query: str, role: str = "ADMIN", user_id: int = 0, top_k: int = 5, **kwargs
) -> dict:
    query = (query or "").strip()
    if not query:
        return _error("缺少 query：请输入要检索的问题或关键词")
    try:
        top_k = max(1, min(int(top_k or 5), _MAX_TOP_K))
    except (TypeError, ValueError):
        top_k = 5

    try:
        query_vector = (await embed_texts([query], text_type="query"))[0]
    except EmbeddingUnavailableError as e:
        return _error(f"{e}，本次无法检索校内资料")

    rows, matrix = await _index.snapshot()
    if not rows:
        return _empty_result("知识库暂无已入库的文档")

    visible = await _visible_course_ids(role, user_id)
    allowed = [
        i
        for i, row in enumerate(rows)
        if row["category"] == "POLICY" or visible is None or row["course_id"] in visible
    ]
    if not allowed:
        return _empty_result("当前身份可见的知识库资料为空")

    sub = matrix[allowed]
    q = np.asarray(query_vector, dtype=np.float32)
    norms = np.linalg.norm(sub, axis=1) * float(np.linalg.norm(q)) + 1e-9
    sims = (sub @ q) / norms

    scored = []
    for pos, row_index in enumerate(allowed):
        row = rows[row_index]
        score = float(sims[pos]) + _KEYWORD_WEIGHT * _bigram_containment(query, row["content"])
        scored.append((score, row))
    scored.sort(key=lambda item: item[0], reverse=True)

    hits = scored[:top_k]
    blocks = [
        {
            "documentId": row["document_id"],
            "title": row["title"],
            "heading": row["heading"],
            "source": _SOURCE_LABELS.get(row["category"], row["category"]),
            "courseName": row["course_name"],
            "content": row["content"],
            "score": round(score, 4),
        }
        for score, row in hits
    ]
    matches = [
        {
            "source": block["source"],
            "title": block["title"],
            "heading": block["heading"],
            "snippet": _snippet(block["content"]),
        }
        for block in blocks
    ]
    if not blocks:
        return _empty_result("知识库里没有匹配的段落")

    return {
        "ok": True,
        "count": len(blocks),
        "model_view": {"count": len(blocks), "matches": matches},
        "display": {"blocks": blocks},
    }


def _empty_result(note: str) -> dict:
    return {
        "ok": True,
        "count": 0,
        "model_view": {"count": 0, "matches": [], "note": note},
        "display": {"blocks": []},
    }


KNOWLEDGE_SEARCH_TOOL = Tool(
    name="knowledge_search",
    description=(
        "在校内知识库（制度与流程 / 课程资料）中检索相关段落，返回带出处的原文片段。"
        "回答校内规定、办事流程、课程与实验资料类问题前必须先调用本工具，"
        "并依据返回结果作答（引用格式：《文档标题》· 段落标题）；"
        "检索无结果时如实说明「资料里没查到」，不得用记忆里的通用内容冒充校内规定。"
    ),
    parameters={
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "检索用的自然语言问题或关键词（如「实验课请假流程」「实验室安全规定」）",
            },
            "top_k": {
                "type": "integer",
                "description": "返回段落数，默认 5，最大 10",
            },
        },
        "required": ["query"],
    },
    executor=_knowledge_search_executor,
)


def register_knowledge_tools() -> None:
    register_tool(KNOWLEDGE_SEARCH_TOOL)
