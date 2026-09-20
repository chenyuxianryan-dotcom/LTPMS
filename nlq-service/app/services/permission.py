import re

import sqlparse
from sqlparse.sql import Identifier, IdentifierList, Where
from sqlparse.tokens import Keyword


class PermissionRejectError(Exception):
    pass


# 白名单表 + 字段映射
# requires：该表自身没有教师归属列时，声明需要补 JOIN 的关联表（fk=本表外键，pk=关联表主键）
PERMISSION_RULES = {
    "experiment": {
        "column": "course.teacher_id",
        "requires": {"table": "course", "fk": "course_id", "pk": "id"},
    },
    "course": {
        "column": "course.teacher_id",
    },
    "schedule": {
        "column": "schedule.teacher_id",
    },
    "schedule_time": {
        "column": "schedule.teacher_id",
        "requires": {"table": "schedule", "fk": "schedule_id", "pk": "id"},
    },
    "query_history": {
        "column": "query_history.user_id",
    },
    "operation_log": {
        "column": "operation_log.user_id",
    },
    # 课题项目一期白名单（规则②）：仅 lab_project* 业务表，教师归属统一经
    # lab_project.teacher_id 注入；lab_task_submission / lab_dataset_version 为二跳
    # 表（本机制仅支持单跳 requires），故意不登记 —— 教师查询时被安全拒绝。
    # student / course_student 一期不入白名单（规则②）。
    "lab_project": {
        "column": "lab_project.teacher_id",
    },
    "lab_project_member": {
        "column": "lab_project.teacher_id",
        "requires": {"table": "lab_project", "fk": "project_id", "pk": "id"},
    },
    "lab_project_task": {
        "column": "lab_project.teacher_id",
        "requires": {"table": "lab_project", "fk": "project_id", "pk": "id"},
    },
    "lab_dataset": {
        "column": "lab_project.teacher_id",
        "requires": {"table": "lab_project", "fk": "project_id", "pk": "id"},
    },
}

# 教师可无约束访问的白名单表。user 表不在其中：它持有 real_name / phone / password，
# 人员档案通道已对教师做「仅本人」断言，此处必须同步收紧，否则旧通道可绕过该边界。
# 开放管理三表（holiday / lab_open_time / lab_open_exception）为全校公开只读信息，教师可查询。
PUBLIC_TABLES = {"laboratory", "semester", "dictionary", "holiday", "lab_open_time", "lab_open_exception"}

# FROM/JOIN 之后紧邻出现时不应被当作别名的关键字
_ALIAS_STOPWORDS = {
    "where", "join", "left", "right", "inner", "outer", "cross", "natural",
    "on", "group", "order", "limit", "having", "union", "and", "or", "set", "as",
}


def inject_permission(sql: str, role: str, user_id: int) -> str:
    """为教师角色注入数据权限约束，管理员不修改"""
    # 模型可能照抄提示词里的占位符写法，先落成真实 id（下方仍统一追加权限条件）
    sql = sql.replace(":current_user_id", str(user_id))
    if role == "ADMIN":
        return sql

    tables = extract_tables(sql)
    conditions = []

    for table in sorted(tables):
        if table in PUBLIC_TABLES:
            continue
        rule = PERMISSION_RULES.get(table)
        if not rule:
            # 未知受控表，拒绝执行
            raise PermissionRejectError(f"无法为表 {table} 建立数据权限约束")

        required = rule.get("requires")
        if required and required["table"] not in tables:
            sql = ensure_join(sql, table, required)

        column_table, column_name = rule["column"].split(".", 1)
        ref = table_ref(sql, column_table)
        conditions.append(f"{ref}.{column_name} = {user_id}")

    conditions = list(dict.fromkeys(conditions))

    if not conditions:
        # 未涉及任何受控表，允许执行（如查询 laboratory/semester/dictionary）
        return sql

    return add_where_conditions(sql, conditions)


def table_ref(sql: str, table: str) -> str:
    """表在 SQL 中的引用名：有别名用别名（schedule sc → sc），否则用表名"""
    match = re.search(rf"(?:FROM|JOIN)\s+`?{table}`?(?![a-z_])", sql, re.IGNORECASE)
    if not match:
        return table
    alias, _ = _alias_after(sql, match.end())
    return alias or table


def ensure_join(sql: str, table: str, requires: dict) -> str:
    """模型漏写关联时补 JOIN，插在 FROM 项（含别名）之后"""
    dependency = requires["table"]
    if re.search(rf"\bJOIN\s+`?{dependency}`?(?![a-z_])", sql, re.IGNORECASE):
        return sql
    match = re.search(rf"FROM\s+`?{table}`?(?![a-z_])", sql, re.IGNORECASE)
    if not match:
        return sql
    alias, consumed = _alias_after(sql, match.end())
    insert_at = match.end() + consumed
    join_sql = f" JOIN {dependency} ON {alias or table}.{requires['fk']} = {dependency}.{requires['pk']}"
    return sql[:insert_at] + join_sql + sql[insert_at:]


def _alias_after(sql: str, pos: int) -> tuple[str, int]:
    """读取 pos 之后的可选别名（跳过 AS 与 WHERE/JOIN 等关键字），返回 (别名, 消耗长度)"""
    match = re.match(r"\s+(?:AS\s+)?([a-z_][a-z0-9_]*)", sql[pos:], re.IGNORECASE)
    if match and match.group(1).lower() not in _ALIAS_STOPWORDS:
        return match.group(1), match.end()
    return "", 0


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


def add_where_conditions(sql: str, conditions: list[str]) -> str:
    """在 SQL 中追加 WHERE 条件

    无 WHERE 时必须插在 GROUP BY / ORDER BY / LIMIT 等子句之前：
    模型常写「... ORDER BY x」结尾的无过滤查询，直接拼到末尾会产生语法错误。
    """
    condition_str = " AND ".join(conditions)

    parsed = sqlparse.parse(sql)[0]
    where_token = None
    offset = 0
    insert_at = None

    for token in parsed.tokens:
        if isinstance(token, Where):
            where_token = token
            break
        if _is_tail_keyword(token):
            insert_at = offset
            break
        offset += len(token.value)

    if where_token is not None:
        # 已有 WHERE，追加 AND。where_text 可能自带尾随空格（被 AND 前吸收），
        # 若不补回尾部空格，紧接的 ORDER BY / GROUP BY 会与条件粘连成非法 SQL。
        where_text = str(where_token)
        new_where = f"{where_text.rstrip()} AND {condition_str} "
        return sql.replace(where_text, new_where, 1)

    if insert_at is not None:
        return sql[:insert_at] + f"WHERE {condition_str} " + sql[insert_at:]

    return f"{sql.rstrip(';')} WHERE {condition_str}"


# 出现在末尾子句的关键字：WHERE 必须插在它们之前
_TAIL_KEYWORDS = ("GROUP BY", "ORDER BY", "HAVING", "LIMIT", "UNION")


def _is_tail_keyword(token) -> bool:
    return token.ttype in Keyword and str(token).strip().upper().startswith(_TAIL_KEYWORDS)
