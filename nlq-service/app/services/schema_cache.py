import asyncio
from datetime import datetime, timedelta

from app.utils.db import db


class SchemaCache:
    """Schema 知识库缓存，TTL 5 分钟兜底"""

    def __init__(self, ttl_seconds: int = 300):
        self._schema_text: str = ""
        self._dict_text: str = ""
        self._last_refresh: datetime = datetime.min
        self._ttl = timedelta(seconds=ttl_seconds)
        self._lock = asyncio.Lock()

    async def get_schema_text(self) -> str:
        async with self._lock:
            if datetime.now() - self._last_refresh > self._ttl or not self._schema_text:
                await self._refresh()
            return self._schema_text

    async def get_dict_text(self) -> str:
        async with self._lock:
            if datetime.now() - self._last_refresh > self._ttl or not self._dict_text:
                await self._refresh()
            return self._dict_text

    async def _refresh(self):
        tables = await self._load_tables()
        dicts = await self._load_dicts()

        self._schema_text = render_schema(tables)

        dict_lines = ["-- 字典映射", ""]
        for d in dicts:
            dict_lines.append(f"-- {d['dict_type']}: {d['dict_label']} = {d['dict_value']}")
        self._dict_text = "\n".join(dict_lines)

        self._last_refresh = datetime.now()

    async def _load_tables(self):
        # knowledge_* 是 Agent 的知识库通道：只经 knowledge_search 检索（带出处引用
        # 与可见性过滤）。若把这两表交给 NL2SQL，模型会绕过该通道直接捞原文——
        # 既没有出处标注，也不套知识库 ACL（教师侧还会撞权限层被拒）。
        # 用 LEFT() 而非 LIKE 'knowledge%'：库层的 % 参数插值会把字面量 % 当占位符炸掉。
        sql = """
        SELECT TABLE_NAME as `name`, COLUMN_NAME as `column`, COLUMN_TYPE as `type`,
               COLUMN_KEY as `key`, COLUMN_COMMENT as `comment`
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND LEFT(TABLE_NAME, 9) <> 'knowledge'
        ORDER BY TABLE_NAME, ORDINAL_POSITION
        """
        return await db.execute(sql)

    async def _load_dicts(self):
        sql = """
        SELECT dict_type, dict_label, dict_value
        FROM dictionary
        WHERE status = 1
        ORDER BY dict_type, sort_order
        """
        return await db.execute(sql)


def render_schema(rows) -> str:
    """把 information_schema 列信息渲染成整表 DDL（含列注释与主键），便于模型理解"""
    blocks: list[str] = []
    current_table: str | None = None
    columns: list[str] = []
    primary_key = ""

    def flush() -> None:
        if current_table is None:
            return
        body = list(columns)
        if primary_key:
            body.append(f"  PRIMARY KEY ({primary_key})")
        blocks.append(f"CREATE TABLE {current_table} (\n" + ",\n".join(body) + "\n);")

    for row in rows:
        if row["name"] != current_table:
            flush()
            current_table = row["name"]
            columns = []
            primary_key = ""

        column = f"  {row['column']} {row['type']}"
        comment = row.get("comment")
        if comment:
            escaped = str(comment).replace("'", "''")
            column += f" COMMENT '{escaped}'"
        columns.append(column)

        if row.get("key") == "PRI":
            primary_key = f"`{row['column']}`"

    flush()
    return "-- 数据库表结构（含列注释与主键）\n\n" + "\n\n".join(blocks)


schema_cache = SchemaCache()
