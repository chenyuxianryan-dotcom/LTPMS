"""NL2SQL 查询的公共执行入口。

把原来散落在 /query 路由里的编排逻辑抽成单一可复用、可测试的函数，
供 HTTP 路由与 Agent 的 query_data 工具共用（单一事实源，避免重复）。
"""

import time

from app.services.deepseek_client import call_deepseek
from app.services.permission import PermissionRejectError, inject_permission
from app.services.prompt_builder import build_messages, build_repair_messages
from app.services.row_limiter import ensure_limit
from app.services.sql_validator import extract_sql, validate_sql
from app.utils.db import db

# 首答 + 一次带错误反馈的修复，避免小毛病直接判死刑
MAX_ATTEMPTS = 2


async def execute_query(question: str, role: str, user_id: int) -> dict:
    """执行一次自然语言查询，返回结构化结果。

    返回 dict 键与 QueryResponse 对齐：success / sql / columns / rows /
    row_count / execution_time / error。错误不向上抛，统一进 error 字段。
    校验拒绝或执行失败时，把失败原因回喂模型修复一次（上限由 MAX_ATTEMPTS 控制）。
    """
    start = time.time()
    question = question.strip()
    messages: list[dict] = []
    failed_sql = ""
    last_error = ""
    repair_feedback = ""

    try:
        for attempt in range(MAX_ATTEMPTS):
            if attempt == 0:
                messages = await build_messages(question, role)
            else:
                messages = build_repair_messages(messages, failed_sql, repair_feedback)

            raw_sql = await call_deepseek(messages)
            model_sql = extract_sql(raw_sql)
            failed_sql = model_sql

            valid, msg = validate_sql(model_sql)
            if not valid:
                last_error = f"该问题无法安全执行：{msg}"
                repair_feedback = f"SQL 校验未通过：{msg}"
                continue

            try:
                sql = inject_permission(model_sql, role, user_id)
            except PermissionRejectError as e:
                last_error = f"无法为该查询建立数据权限约束，已拒绝执行：{e}"
                repair_feedback = f"数据权限约束无法建立：{e}。请只使用表结构中存在的表。"
                continue

            sql = ensure_limit(sql)

            try:
                columns, rows = await db.execute_with_columns(sql)
            except Exception as e:
                last_error = f"查询执行失败：{e}"
                repair_feedback = f"MySQL 执行出错：{e}"
                continue

            data_rows = [[row.get(column) for column in columns] for row in rows]
            return {
                "success": True,
                "sql": sql,
                "columns": columns,
                "rows": data_rows,
                "row_count": len(data_rows),
                "execution_time": int((time.time() - start) * 1000),
                "error": None,
            }

        return _fail(last_error or "查询执行失败：未能生成可执行的 SQL")
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
