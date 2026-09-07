import re

import sqlparse
from sqlparse.sql import Identifier, IdentifierList, Where
from sqlparse.tokens import Keyword


class PermissionRejectError(Exception):
    pass


# 白名单表 + 字段映射
PERMISSION_RULES = {
    "experiment": {
        "column": "experiment.teacher_id",
        "fallback": "course.teacher_id",
        "join": "JOIN course ON experiment.course_id = course.id",
    },
    "course": {
        "column": "course.teacher_id",
    },
    "schedule": {
        "column": "schedule.teacher_id",
    },
}

PUBLIC_TABLES = {"laboratory", "semester", "dictionary", "user"}


def inject_permission(sql: str, role: str, user_id: int) -> str:
    """为教师角色注入数据权限约束，管理员不修改"""
    if role == "ADMIN":
        return sql

    tables = extract_tables(sql)
    conditions = []

    for table in tables:
        if table in PUBLIC_TABLES:
            continue
        rule = PERMISSION_RULES.get(table)
        if not rule:
            # 未知受控表，拒绝执行
            raise PermissionRejectError(f"无法为表 {table} 建立数据权限约束")

        column = rule["column"]
        if table == "experiment":
            # experiment 无 teacher_id，通过 course 兜底
            if "course" not in tables:
                # 需要自动注入 JOIN
                sql = ensure_experiment_join(sql)
            column = rule.get("fallback", column)

        conditions.append(f"{column} = {user_id}")

    if not conditions:
        # 未涉及任何受控表，允许执行（如查询 laboratory/semester/dictionary）
        return sql

    return add_where_conditions(sql, conditions)


def extract_tables(sql: str) -> set[str]:
    """从 SQL 中提取表名（简单实现）"""
    tables = set()
    parsed = sqlparse.parse(sql)
    for stmt in parsed:
        tokens = list(stmt.flatten())
        i = 0
        while i < len(tokens):
            token = tokens[i]
            if token.ttype in (Keyword, ) and token.value.upper() in ("FROM", "JOIN"):
                i += 1
                while i < len(tokens) and tokens[i].is_whitespace:
                    i += 1
                if i < len(tokens):
                    name = tokens[i].value.strip().strip("`")
                    tables.add(name.split(".")[-1])
            i += 1
    return tables


def ensure_experiment_join(sql: str) -> str:
    """确保 experiment 表关联了 course 表"""
    if "JOIN course" in sql.lower():
        return sql
    # 在 FROM experiment 后追加 JOIN course
    sql = re.sub(
        r"(FROM\s+experiment\b)",
        r"\1 JOIN course ON experiment.course_id = course.id",
        sql,
        flags=re.IGNORECASE,
    )
    return sql


def add_where_conditions(sql: str, conditions: list[str]) -> str:
    """在 SQL 中追加 WHERE 条件"""
    condition_str = " AND ".join(conditions)

    parsed = sqlparse.parse(sql)[0]
    where_token = None
    for token in parsed.tokens:
        if isinstance(token, Where):
            where_token = token
            break

    if where_token is None:
        # 没有 WHERE，在末尾追加
        return f"{sql.rstrip(';')} WHERE {condition_str}"
    else:
        # 已有 WHERE，追加 AND
        where_text = str(where_token)
        new_where = f"{where_text} AND {condition_str}"
        return sql.replace(where_text, new_where, 1)
