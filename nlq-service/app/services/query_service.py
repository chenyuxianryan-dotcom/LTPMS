"""NL2SQL 查询的公共执行入口。

把原来散落在 /query 路由里的编排逻辑抽成单一可复用、可测试的函数，
供 HTTP 路由与 Agent 的 query_data 工具共用（单一事实源，避免重复）。
"""

import time

from app.services.deepseek_client import call_deepseek
from app.services.permission import PermissionRejectError, inject_permission
from app.services.prompt_builder import build_messages
from app.services.row_limiter import ensure_limit
from app.services.sql_validator import extract_sql, validate_sql
from app.utils.db import db


async def execute_query(question: str, role: str, user_id: int) -> dict:
    """执行一次自然语言查询，返回结构化结果。

    返回 dict 键与 QueryResponse 对齐：success / sql / columns / rows /
    row_count / execution_time / error。错误不向上抛，统一进 error 字段。
    """
    start = time.time()
    question = question.strip()

    try:
        messages = await build_messages(question, role)
        raw_sql = await call_deepseek(messages)
        sql = extract_sql(raw_sql)

        valid, msg = validate_sql(sql)
        if not valid:
            return _fail(f"该问题无法安全执行：{msg}")

        sql = inject_permission(sql, role, user_id)
        sql = ensure_limit(sql)

        rows = await db.execute(sql)
        columns = list(rows[0].keys()) if rows else []
        data_rows = [list(row.values()) for row in rows]

        return {
            "success": True,
            "sql": sql,
            "columns": columns,
            "rows": data_rows,
            "row_count": len(data_rows),
            "execution_time": int((time.time() - start) * 1000),
            "error": None,
        }

    except PermissionRejectError as e:
        return _fail(f"无法为该查询建立数据权限约束，已拒绝执行：{e}")
    except Exception as e:
        return _fail(f"查询执行失败：{e}")


def _fail(error: str) -> dict:
    return {
        "success": False,
        "sql": None,
        "columns": None,
        "rows": None,
        "row_count": None,
        "execution_time": None,
        "error": error,
    }
