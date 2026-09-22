from fastapi import APIRouter, Header, Request
from pydantic import BaseModel

from app.services.agent_service import run_agent_query
from app.services.archive_service import ArchiveCache, generate_archive
from app.services.embeddings import EmbeddingUnavailableError, embed_texts
from app.services.narrative_service import generate_narrative
from app.services.query_service import execute_query
from app.services.schema_cache import schema_cache
from app.services.vision import recognize_schedule_photo

router = APIRouter()

# 数字档案缓存（data/archive_cache.db，TTL 24h；惰性单例，避免导入期副作用）
_archive_cache: ArchiveCache | None = None


def _get_archive_cache() -> ArchiveCache:
    global _archive_cache
    if _archive_cache is None:
        _archive_cache = ArchiveCache("data/archive_cache.db")
    return _archive_cache


class QueryRequest(BaseModel):
    question: str


class AgentRequest(BaseModel):
    question: str
    history: list[dict] | None = None
    # 「我的档案」入口：服务端锁定 person_ref 为当前登录用户
    self_dossier: bool = False


class AgentResponse(BaseModel):
    answer: str
    steps: list[dict]
    steps_used: int
    error: str | None = None
    missing_dimensions: list[str] = []
    routed_person_name: str | None = None


class QueryResponse(BaseModel):
    success: bool
    sql: str | None = None
    columns: list[str] | None = None
    rows: list[list] | None = None
    suggestions: list[str] | None = None
    row_count: int | None = None
    execution_time: int | None = None
    error: str | None = None
    # 机器可读的失败分类（当前只有 NLQ_UNSAFE：问题本身不是数据问题）
    error_code: str | None = None


class RecognizeRequest(BaseModel):
    image_base64: str


class RecognizeResponse(BaseModel):
    success: bool
    rows: list[dict]
    error: str | None = None


class ArchiveRequest(BaseModel):
    entity_type: str  # laboratory | experiment
    entity_id: int


class ArchiveResponse(BaseModel):
    success: bool
    text: str = ""
    cached: bool = False
    stats: dict | None = None
    error: str | None = None


class NarrativeRequest(BaseModel):
    kind: str  # annual_report | teaching_summary


class NarrativeResponse(BaseModel):
    success: bool
    text: str = ""
    numbers: dict = {}
    scope: str = ""
    error: str | None = None


class EmbedRequest(BaseModel):
    texts: list[str]
    # document=入库文本 / query=检索提问；编码方向不同，混用会掉召回
    text_type: str = "document"


class EmbedResponse(BaseModel):
    success: bool
    vectors: list[list[float]] = []
    dim: int = 0
    error: str | None = None


@router.post("/query", response_model=QueryResponse)
async def query(
    request: QueryRequest,
    req: Request,
    x_user_id: int = Header(..., alias="X-User-Id"),
    x_user_role: str = Header(..., alias="X-User-Role"),
):
    question = request.question.strip()
    result = await execute_query(question, x_user_role, x_user_id)
    return QueryResponse(
        success=result["success"],
        sql=result["sql"],
        columns=result["columns"],
        rows=result["rows"],
        row_count=result["row_count"],
        execution_time=result["execution_time"],
        error=result["error"],
        error_code=result.get("error_code"),
        suggestions=generate_suggestions(question, result["columns"]),
    )


@router.get("/schema")
async def schema():
    return {
        "schema": await schema_cache.get_schema_text(),
        "dictionary": await schema_cache.get_dict_text(),
    }


@router.post("/agent", response_model=AgentResponse)
async def agent(
    request: AgentRequest,
    x_user_id: int = Header(..., alias="X-User-Id"),
    x_user_role: str = Header(..., alias="X-User-Role"),
):
    """多轮 Agent 对话入口：加载技能与长期记忆，返回最终答案与工具调用轨迹。"""
    return await run_agent_query(
        request.question, x_user_role, x_user_id, history=request.history,
        self_dossier=request.self_dossier,
    )


@router.post("/recognize-schedule", response_model=RecognizeResponse)
async def recognize_schedule(request: RecognizeRequest):
    """课表照片识别：base64 图片 → 结构化课表行草稿（走 GLM-4V 通道）。"""
    result = await recognize_schedule_photo(request.image_base64)
    return RecognizeResponse(
        success=result["success"],
        rows=result["rows"],
        error=result["error"],
    )


@router.post("/archive", response_model=ArchiveResponse)
async def archive(request: ArchiveRequest):
    """实验台数字档案：聚合真实统计 → AI 生成档案文本（缓存 TTL 24h）。"""
    result = await generate_archive(request.entity_type, request.entity_id, cache=_get_archive_cache())
    return ArchiveResponse(
        success=result["success"],
        text=result["text"],
        cached=result["cached"],
        stats=result["stats"],
        error=result["error"],
    )


@router.post("/narrative", response_model=NarrativeResponse)
async def narrative(
    request: NarrativeRequest,
    x_user_id: int = Header(..., alias="X-User-Id"),
    x_user_role: str = Header(..., alias="X-User-Role"),
):
    """叙事报告/教学总结：报表数字 Python 聚合（权限分层），LLM 只做语言包装。"""
    result = await generate_narrative(request.kind, x_user_role, x_user_id)
    return NarrativeResponse(
        success=result["success"],
        text=result["text"],
        numbers=result["numbers"],
        scope=result["scope"],
        error=result["error"],
    )


@router.post("/embed", response_model=EmbedResponse)
async def embed(request: EmbedRequest):
    """批量文本向量化（内部端点，Java 侧文档入库专用）：最多 10 条/请求。"""
    if not request.texts:
        return EmbedResponse(success=False, error="texts 不能为空")
    if len(request.texts) > 10:
        return EmbedResponse(success=False, error="单次最多 10 条文本")
    try:
        vectors = await embed_texts(request.texts, request.text_type)
    except EmbeddingUnavailableError as e:
        return EmbedResponse(success=False, error=str(e))
    return EmbedResponse(success=True, vectors=vectors, dim=len(vectors[0]) if vectors else 0)


def generate_suggestions(question: str, columns: list[str] | None = None) -> list[str]:
    return [
        "统计各实验室的实验课程数量",
        "列出本学期所有实验项目",
        "按教师统计实验人时数",
    ]
