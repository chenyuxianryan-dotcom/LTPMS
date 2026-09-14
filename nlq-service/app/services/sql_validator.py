import re

import sqlparse
from sqlparse.sql import Parenthesis, Statement, TokenList
from sqlparse.tokens import DML, Keyword, Wildcard


DANGEROUS_FUNCTIONS = {"load_file", "into outfile", "sleep", "benchmark", "version"}

# PII 黑名单：password 等敏感列永不允许进入查询通道（含 ADMIN），避免随结果回喂大模型
FORBIDDEN_COLUMNS = ("password",)


def _check_sensitive_columns(sql: str) -> tuple[bool, str]:
    for column in FORBIDDEN_COLUMNS:
        if re.search(rf"(?<![a-z_]){column}(?![a-z_])", sql, re.IGNORECASE):
            return False, f"禁止查询 {column} 等敏感字段"
    # 星号展开会连带带出未显式声明的列，涉及 user 表时要求逐列列出；
    # count(*) 等聚合不展开列，不算投影星号（否则「有多少名教师」这类问题永远无法执行）
    if _mentions_user_table(sql) and _has_projection_star(sql):
        return False, "涉及 user 表时禁止 SELECT *（会带出敏感字段），请显式列出所需列"
    return True, sql


def _mentions_user_table(sql: str) -> bool:
    return bool(re.search(r"(?:from|join)\s+`?user`?(?![a-z_])", sql, re.IGNORECASE))


def _has_projection_star(sql: str) -> bool:
    """投影中是否存在会展开列的星号（SELECT * / u.*）。
    COUNT(*)、SUM(a * b) 等函数括号内的星号不展开列，不算；
    子查询递归检查其自身投影"""
    try:
        statements = sqlparse.parse(sql)
    except Exception:
        return True
    return any(_select_has_star(stmt) for stmt in statements)


def _select_has_star(stmt) -> bool:
    in_projection = False
    for token in stmt.tokens:
        if token.ttype is DML and token.value.upper() == "SELECT":
            in_projection = True
        elif _star_in_scope(token, in_projection):
            return True
    return False


def _star_in_scope(token, in_projection: bool) -> bool:
    if isinstance(token, Parenthesis):
        inner = token.value[1:-1].strip()
        return any(_select_has_star(s) for s in sqlparse.parse(inner)) if inner else False
    if token.ttype is Wildcard:
        return in_projection
    if isinstance(token, TokenList):
        return any(_star_in_scope(child, in_projection) for child in token.tokens)
    return False


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

    # 禁止敏感字段（password / user 表星号）
    passed, message = _check_sensitive_columns(sql)
    if not passed:
        return False, message

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
