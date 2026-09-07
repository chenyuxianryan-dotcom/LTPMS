import re

import sqlparse
from sqlparse.sql import Statement
from sqlparse.tokens import DML


DANGEROUS_FUNCTIONS = {"load_file", "into outfile", "sleep", "benchmark", "version"}


def validate_sql(sql: str) -> tuple[bool, str]:
    """校验 SQL 是否安全，返回 (是否通过, 错误信息)"""
    if not sql:
        return False, "生成的 SQL 为空"

    # 去除 markdown 代码块
    sql = re.sub(r"```sql?", "", sql)
    sql = sql.replace("```", "").strip()

    # 禁止注释
    if re.search(r"(--|/\*|\*/)", sql, re.IGNORECASE):
        return False, "SQL 中不能包含注释"

    # 禁止危险函数
    lower_sql = sql.lower()
    for func in DANGEROUS_FUNCTIONS:
        if func in lower_sql:
            return False, f"SQL 包含危险函数或关键字：{func}"

    # 解析并校验语句类型
    parsed = sqlparse.parse(sql)
    if not parsed:
        return False, "无法解析 SQL"

    for stmt in parsed:
        first_token = get_first_meaningful_token(stmt)
        if first_token is None:
            continue
        if first_token.ttype not in (DML, ) or first_token.value.upper() != "SELECT":
            return False, "仅支持 SELECT 查询"

    return True, sql


def get_first_meaningful_token(stmt: Statement):
    for token in stmt.tokens:
        if not token.is_whitespace:
            return token
    return None


def extract_sql(raw: str) -> str:
    raw = raw.strip()
    if raw.startswith("```"):
        raw = re.sub(r"```sql?", "", raw)
        raw = raw.replace("```", "").strip()
    return raw
