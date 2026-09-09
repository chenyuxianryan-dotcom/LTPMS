from fastapi import APIRouter, Header, Request
from pydantic import BaseModel

from app.services.query_service import execute_query
from app.services.schema_cache import schema_cache

router = APIRouter()


class QueryRequest(BaseModel):
    question: str


class QueryResponse(BaseModel):
    success: bool
    sql: str | None = None
    columns: list[str] | None = None
    rows: list[list] | None = None
    suggestions: list[str] | None = None
    row_count: int | None = None
    execution_time: int | None = None
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


def generate_suggestions(question: str, columns: list[str] | None = None) -> list[str]:
    return [
        "统计各实验室的实验课程数量",
        "列出本学期所有实验项目",
        "按教师统计实验人时数",
    ]
