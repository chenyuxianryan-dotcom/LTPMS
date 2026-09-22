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

# 「这不是数据问题」的哨兵：知识类提问（制度/流程/资料/概念）不该硬凑 SQL。
# 命中即直接结束本轮并带上 44001，前端据此把问题转给阿实顾问（知识库检索带出处）。
NOT_A_DATA_SENTINEL = "NOT_A_DATA_QUESTION"
NOT_A_DATA_CODE = "NLQ_UNSAFE"


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
            if _is_not_a_data_question(raw_sql):
                # 不重试：模型已经明确这不是查数问题，修复回合只会逼它编表名
                return _fail("该问题不是数据查询问题", NOT_A_DATA_CODE)
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
                "error_code": None,
            }

        return _fail(last_error or "查询执行失败：未能生成可执行的 SQL")
    except Exception as e:
        return _fail(f"查询执行失败：{e}")


def _is_not_a_data_question(raw: str) -> bool:
    """模型是否自陈「这不是查数问题」。

    只认哨兵出现（不看有没有顺带写 SQL）：混写本身就是畸形输出，
    真按半截 SQL 硬跑反而更糟——转给阿实顾问，它仍然能调 query_data 把数捞回来。
    """
    return NOT_A_DATA_SENTINEL in (raw or "").upper()


def _fail(error: str, error_code: str | None = None) -> dict:
    return {
        "success": False,
        "sql": None,
        "columns": None,
        "rows": None,
        "row_count": None,
        "execution_time": None,
        "error": error,
        "error_code": error_code,
    }
