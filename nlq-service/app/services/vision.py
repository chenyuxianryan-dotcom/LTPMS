"""统一视觉识别通道：图片理解（意图路由 + 摘要 + 结构化提取）走 DeepSeek deepseek-flash。

与 NL2SQL/Agent 共用同一账号、端点与模型（模型统一决策）；智谱 GLM-4V 保留为
VISION_PROVIDER=zhipu 的回退开关（统一通道异常时可临时切回，一个发布周期内）。
key 未配置时视觉能力优雅降级，不影响查询类能力。
schedule 路由输出的行结构与 Java 侧课表导入入参对齐（camelCase）。
"""

import json
import logging
import re

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

ROUTE_SCHEDULE = "schedule"
ROUTE_DOCUMENT = "document"
ROUTE_NOTICE = "notice"
ROUTE_UNKNOWN = "unknown"
ROUTES = (ROUTE_SCHEDULE, ROUTE_DOCUMENT, ROUTE_NOTICE, ROUTE_UNKNOWN)

# 提取文本回喂上限：超长页面截断，避免挤爆后续作答的上下文
_MAX_DOCUMENT_CHARS = 6000

_CLASSIFY_PROMPT = """你是高校实验教学项目管理系统的图片理解模块。用户上传了一张图片，可能还附着文字。

请完成四件事，只返回一个 JSON 对象（不要解释、不要 Markdown 围栏）：

1. route 意图路由，取值之一：
   - "schedule"：课程表、考试安排表、排课表等表格型图片（需要转成结构化课程行）
   - "document"：实验指导书、教材、制度文件等资料页面（需要提取文字，供校内知识库检索）
   - "notice"：通知、黑板板书、聊天或系统截图等信息型图片（需要转述要点）
   - "unknown"：模糊、与文档无关、内容不足以可靠识别

2. 结构化提取（不相关的字段留空：scheduleRows 给 []、documentText 给 ""、notice 给 {}）：
   - schedule：逐行提取课程，行字段 courseCode(课程号)、courseName(课程名)、
     totalHours(总学时)、teacherNo(教师工号)、labCode(实验室编号)、
     classNames(上课班级)、studentCount(选课人数)、rawScheduleText(上课时间文本)；
     rawScheduleText 统一写成「周X第A-B节」形式（如 "周一第1-2节"），多个时段用 "; " 分隔，
     有周次信息时写成 "周一第1-2节(1-16周)"；分不出节次时保留原文
     无法识别的字段填 null
   - document：提取页面可读文字（保留标题与章节层级）放 documentText
   - notice：提取 title(标题)、time(时间)、place(地点)、items(要点字符串数组) 放 notice

3. digest：一句中文说明你看到的图片内容（类型 + 关键信息，如"这是一张 3 行 8 列的实验课表"），
   看不清时 digest 写清原因。

4. suggestedQuestions：最多 3 条用户可能接着问的中文短问题。

JSON 结构：
{"route":"...","confidence":"high|low","digest":"...",
 "scheduleRows":[{"courseCode":null,"courseName":null,"totalHours":null,"teacherNo":null,
   "labCode":null,"classNames":null,"studentCount":null,"rawScheduleText":null}],
 "documentText":"","notice":{"title":"","time":"","place":"","items":[]},
 "suggestedQuestions":[""]}"""

# 行字段：camelCase 契约（前端草稿卡/课表导入共用）↔ 模型偶发输出的 snake_case
_ROW_KEYS = {
    "courseCode": "course_code",
    "courseName": "course_name",
    "totalHours": "total_hours",
    "teacherNo": "teacher_no",
    "labCode": "lab_code",
    "classNames": "class_names",
    "studentCount": "student_count",
    "rawScheduleText": "raw_schedule_text",
}


def _configured() -> bool:
    if settings.vision_provider == "zhipu":
        return bool(settings.zhipu_api_key)
    return bool(settings.deepseek_api_key)


async def _vision_call(prompt: str, image_base64: str, image_mime: str = "image/jpeg") -> str:
    """底层视觉调用：provider 决定端点/模型；deepseek 关思考省 token 降延迟。"""
    if settings.vision_provider == "zhipu":
        url, key, model = settings.zhipu_api_url, settings.zhipu_api_key, settings.zhipu_vision_model
        extra: dict = {}
    else:
        url, key, model = settings.deepseek_api_url, settings.deepseek_api_key, settings.deepseek_model
        extra = {"thinking": {"type": "disabled"}}

    payload = {
        "model": model,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{image_mime};base64,{image_base64}"},
                    },
                    {"type": "text", "text": prompt},
                ],
            }
        ],
        "temperature": 0.1,
        **extra,
    }
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    timeout = httpx.Timeout(60.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.post(url, json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()
    return (data["choices"][0]["message"].get("content") or "").strip()


async def analyze_image(
    image_base64: str,
    question: str = "",
    force_route: str | None = None,
    image_mime: str = "image/jpeg",
) -> dict:
    """一次视觉调用完成「意图路由 + 结构化提取 + 摘要 + 建议追问」。

    force_route：用户改道时指定的类型，跳过分类按指定类型提取。
    返回 {success, route, digest, low_confidence, rows, document_text, notice,
    suggested_questions, error}；调用失败不向上抛，统一进 error。
    """
    if not _configured():
        return _failed("识图服务未配置，请改用文字描述提问")

    prompt = _CLASSIFY_PROMPT
    if question.strip():
        prompt += f"\n\n用户附着的问题：{question.strip()}"
    if force_route in ROUTES:
        prompt += f'\n\n用户已指定按 "{force_route}" 类型处理这张图片，route 字段必须为 "{force_route}"。'

    try:
        content = await _vision_call(prompt, image_base64, image_mime)
    except Exception as e:  # noqa: BLE001 —— 网络/HTTP/超时一律降级，不打断请求
        logger.warning("视觉通道调用失败（provider=%s）：%s", settings.vision_provider, e)
        return _failed("识图服务暂时不可用，请稍后重试或改用文字描述提问")

    data = _parse_object(content)
    if data is None:
        return _failed("识图结果无法解析，请重试或改用文字描述提问")

    route = str(data.get("route") or "").strip().lower()
    if route not in ROUTES:
        route = ROUTE_UNKNOWN
    digest = str(data.get("digest") or "").strip()
    confidence = str(data.get("confidence") or "").strip().lower()
    rows = _normalize_rows(data.get("scheduleRows") or data.get("schedule_rows"))
    document_text = str(data.get("documentText") or data.get("document_text") or "").strip()
    notice = data.get("notice")
    notice = notice if isinstance(notice, dict) else {}
    suggestions = data.get("suggestedQuestions") or data.get("suggested_questions")
    suggestions = [str(s).strip() for s in suggestions if str(s).strip()][:3] if isinstance(suggestions, list) else []

    return {
        "success": True,
        "route": route,
        "digest": digest,
        "low_confidence": confidence == "low" or not digest,
        "rows": rows,
        "document_text": document_text[:_MAX_DOCUMENT_CHARS],
        "notice": notice,
        "suggested_questions": suggestions,
        "error": None,
    }


async def recognize_schedule_photo(image_base64: str, image_mime: str = "image/jpeg") -> dict:
    """课表照片 → 结构化课表行草稿（课表页拍照入口，与智能查询附图同一通道与解析）。

    返回 dict：success / rows（camelCase 课表行）/ error。
    """
    result = await analyze_image(image_base64, force_route=ROUTE_SCHEDULE, image_mime=image_mime)
    if not result["success"]:
        return {"success": False, "rows": [], "error": result["error"]}
    if not result["rows"]:
        return {"success": False, "rows": [], "error": "没有识别出课表行，请换一张更清晰的课表照片"}
    return {"success": True, "rows": result["rows"], "error": None}


def _normalize_rows(raw) -> list[dict]:
    if not isinstance(raw, list):
        return []
    rows = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        row = {camel: item.get(camel, item.get(snake)) for camel, snake in _ROW_KEYS.items()}
        # 整行全空视为识别噪声，丢弃
        if any(value not in (None, "") for value in row.values()):
            rows.append(row)
    return rows


def _parse_object(content: str) -> dict | None:
    """从模型返回文本中提取 JSON 对象，容错前后缀文字与代码块围栏。"""
    if not content:
        return None
    cleaned = re.sub(r"```(?:json)?", "", content).strip()
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    try:
        parsed = json.loads(cleaned[start : end + 1])
    except json.JSONDecodeError:
        return None
    return parsed if isinstance(parsed, dict) else None


def _failed(error: str) -> dict:
    return {
        "success": False,
        "route": ROUTE_UNKNOWN,
        "digest": "",
        "low_confidence": False,
        "rows": [],
        "document_text": "",
        "notice": {},
        "suggested_questions": [],
        "error": error,
    }
