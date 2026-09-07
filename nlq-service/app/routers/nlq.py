import time

from fastapi import APIRouter, Header, Request
from pydantic import BaseModel

from app.services.deepseek_client import call_deepseek
from app.services.permission import PermissionRejectError, inject_permission
from app.services.prompt_builder import build_messages
from app.services.row_limiter import ensure_limit
from app.services.schema_cache import schema_cache
from app.services.sql_validator import extract_sql, validate_sql
from app.utils.db import db

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
    start = time.time()
    question = request.question.strip()

    try:
        messages = await build_messages(question, x_user_role)
        raw_sql = await call_deepseek(messages)
        sql = extract_sql(raw_sql)

        valid, msg = validate_sql(sql)
        if not valid:
            return QueryResponse(
                success=False,
                error=f"该问题无法安全执行：{msg}",
                suggestions=generate_suggestions(question),
            )

        sql = inject_permission(sql, x_user_role, x_user_id)
        sql = ensure_limit(sql)

        rows = await db.execute(sql)
        columns = list(rows[0].keys()) if rows else []
        data_rows = [list(row.values()) for row in rows]

        execution_time = int((time.time() - start) * 1000)
        return QueryResponse(
            success=True,
            sql=sql,
            columns=columns,
            rows=data_rows,
            row_count=len(data_rows),
            execution_time=execution_time,
            suggestions=generate_suggestions(question, columns),
        )

    except PermissionRejectError as e:
        return QueryResponse(
            success=False,
            error=f"无法为该查询建立数据权限约束，已拒绝执行：{str(e)}",
            suggestions=generate_suggestions(question),
        )
    except Exception as e:
        return QueryResponse(
            success=False,
            error=f"查询执行失败：{str(e)}",
            suggestions=generate_suggestions(question),
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
