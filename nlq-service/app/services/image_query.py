"""智能查询附图链路：图片 → 意图路由 → 分路作答。

分路（PRD F3/F4/F5）：
- schedule：识别出的课程行参与作答（照片即数据源），并把行草稿交给前端确认写回；
- document：图内文字进知识库顾问链路（knowledge_search 带出处作答），
  与知识库结论分开标注来源；
- notice：转述要点 + 建议追问，不落任何数据；
- unknown / 低置信：兜底「没看清」，不猜内容（沿用「本校事实禁猜」口径）。

回答只依据识别产物与工具返回的真实内容；图片本体不落盘（仅内存中转）。
"""

import json

from app.services.agent_loop import run_agent
from app.services.deepseek_client import chat_completion
from app.services.vision import (
    ROUTE_DOCUMENT,
    ROUTE_NOTICE,
    ROUTE_SCHEDULE,
    ROUTE_UNKNOWN,
    analyze_image,
)

_FALLBACK_ERROR = "没有看清图片内容：请拍正、补光后重试，或直接用文字描述提问"
_SHOOT_TIPS = ["把表格拍正、避免阴影遮挡", "文字太小可拍局部特写", "或直接用文字描述你的问题"]

_SCHEDULE_ANSWER_PROMPT = (
    "你是高校实验教学项目管理系统的数据助手。以下是从用户上传的课表图片中识别出的课程记录（JSON）。"
    "只依据这些记录回答用户的问题：可以按周几/时段汇总上课情况、指出空档或冲突，"
    "但不得补充记录里没有的课程；记录里得不到答案时明确说明。回答用中文、简洁分点。"
)

_NOTICE_ANSWER_PROMPT = (
    "你是高校实验教学项目管理系统的信息助手。以下是从用户上传的图片中提取的通知要点（JSON）。"
    "只依据这些要点回答用户的问题，不要补充图片里没有的信息；无法回答时明确说明。回答用中文、简洁。"
)

_DOCUMENT_CONTEXT = (
    "【用户上传图片中提取的内容】\n{text}\n\n"
    "【用户问题】{question}\n\n"
    "请先调用 knowledge_search 检索校内资料再作答：引用知识库结论时标注《文档标题》· 段落标题；"
    "回答里要区分「来自你的图片」与「来自知识库」两部分；"
    "知识库没有相关依据时如实说明「资料里没查到」，只依据图片内容作答，不得编造校内规定。"
)


async def run_image_query(
    image_base64: str,
    question: str = "",
    role: str = "STUDENT",
    user_id: int = 0,
    force_route: str | None = None,
    image_mime: str = "image/jpeg",
) -> dict:
    """附图查询主入口：返回前端图片结果卡所需的全部字段。"""
    analysis = await analyze_image(image_base64, question, force_route, image_mime)
    if not analysis["success"]:
        # 通道/配置问题：不做拍摄建议（那是图片质量问题的话术）
        return _ok_result(False, ROUTE_UNKNOWN, "", low_confidence=False, error=analysis["error"])
    return await _dispatch(analysis, question, role, user_id)


async def _dispatch(analysis: dict, question: str, role: str, user_id: int) -> dict:
    route = analysis["route"]
    digest = analysis["digest"]
    suggestions = analysis["suggested_questions"]
    asked = question.strip()

    if analysis["low_confidence"] or route == ROUTE_UNKNOWN:
        return _fallback(_FALLBACK_ERROR, digest=digest)

    if route == ROUTE_SCHEDULE:
        rows = analysis["rows"]
        if not rows:
            return _fallback(_FALLBACK_ERROR, digest=digest)
        answer = await _answer_with_prompt(_SCHEDULE_ANSWER_PROMPT, {"rows": rows}, asked) if asked else ""
        return _ok(route, digest, answer=answer, rows=rows, suggestions=suggestions)

    if route == ROUTE_DOCUMENT:
        text = analysis["document_text"]
        if not text:
            return _fallback(_FALLBACK_ERROR, digest=digest)
        if not asked:
            # 纯图无配文：只提取 + 建议追问（最轻承诺出口，PRD F4 规则 4）
            return _ok(route, digest, document_text=text, suggestions=suggestions)
        answer, steps, error = await _answer_from_knowledge(text, asked, role, user_id)
        if error:
            return _fallback(error, digest=digest)
        return _ok(route, digest, answer=answer, document_text=text, steps=steps, suggestions=suggestions)

    # notice：转述要点；有提问再作答，无提问时只给提取结果与建议追问
    notice = analysis["notice"]
    if not _notice_has_content(notice):
        return _fallback(_FALLBACK_ERROR, digest=digest)
    answer = await _answer_with_prompt(_NOTICE_ANSWER_PROMPT, {"notice": notice}, asked) if asked else ""
    return _ok(route, digest, answer=answer, notice=notice, suggestions=suggestions)


async def _answer_with_prompt(system_prompt: str, payload: dict, question: str) -> str:
    """非检索类作答：识别产物 + 问题 → 文本回答；调用失败返回空串（由调用方保持空答案）。"""
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"{json.dumps(payload, ensure_ascii=False)}\n\n用户问题：{question}"},
    ]
    try:
        message = await chat_completion(messages)
        return (message.get("content") or "").strip()
    except Exception:  # noqa: BLE001 —— 作答失败不阻断草稿/提取结果上屏
        return ""


async def _answer_from_knowledge(text: str, question: str, role: str, user_id: int) -> tuple[str, list, str | None]:
    """资料页问答：图内文字进顾问链路（仅 knowledge_search 白名单），返回答案 + 出处轨迹。"""
    augmented = _DOCUMENT_CONTEXT.format(text=text, question=question)
    result = await run_agent(
        augmented,
        role,
        user_id,
        max_steps=2,
        tool_allowlist=("knowledge_search",),
    )
    if result.get("error"):
        return "", [], f"资料检索失败：{result['error']}"
    answer = (result.get("answer") or "").strip()
    if not answer:
        return "", [], "资料检索没有返回内容，请重试或改用文字提问"
    return answer, result.get("steps") or [], None


def _notice_has_content(notice: dict) -> bool:
    if not notice:
        return False
    items = notice.get("items")
    if isinstance(items, list) and any(str(i).strip() for i in items):
        return True
    return any(str(notice.get(key) or "").strip() for key in ("title", "time", "place"))


def _ok(route: str, digest: str, **extra) -> dict:
    return _ok_result(True, route, digest, **extra)


def _ok_result(success: bool, route: str, digest: str, **extra) -> dict:
    result = {
        "success": success,
        "route": route,
        "digest": digest,
        "low_confidence": False,
        "answer": "",
        "rows": [],
        "document_text": "",
        "notice": {},
        "steps": [],
        "suggestions": [],
        "error": None,
    }
    result.update(extra)
    return result


def _fallback(error: str, digest: str = "") -> dict:
    """「没看清」兜底：明确说明 + 拍摄建议 + 文字提问引导，不含任何猜测内容。"""
    return _ok_result(
        False,
        ROUTE_UNKNOWN,
        digest,
        low_confidence=True,
        suggestions=list(_SHOOT_TIPS),
        error=error,
    )
