from fastapi import APIRouter, Header, Request
from pydantic import BaseModel

from app.services.agent_service import run_agent_query
from app.services.query_service import execute_query
from app.services.schema_cache import schema_cache
from app.services.vision import recognize_schedule_photo

router = APIRouter()


class QueryRequest(BaseModel):
    question: str


class AgentRequest(BaseModel):
    question: str
    history: list[dict] | None = None


class AgentResponse(BaseModel):
    answer: str
    steps: list[dict]
    steps_used: int
    error: str | None = None


class QueryResponse(BaseModel):
    success: bool
    sql: str | None = None
    columns: list[str] | None = None
    rows: list[list] | None = None
    suggestions: list[str] | None = None
    row_count: int | None = None
    execution_time: int | None = None
    error: str | None = None


class RecognizeRequest(BaseModel):
    image_base64: str


class RecognizeResponse(BaseModel):
    success: bool
    rows: list[dict]
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
        request.question, x_user_role, x_user_id, history=request.history
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


def generate_suggestions(question: str, columns: list[str] | None = None) -> list[str]:
    return [
        "统计各实验室的实验课程数量",
        "列出本学期所有实验项目",
        "按教师统计实验人时数",
    ]
