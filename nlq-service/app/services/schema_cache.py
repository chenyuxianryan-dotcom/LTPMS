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

        lines = ["-- 数据库表结构", ""]
        for table in tables:
            lines.append(f"CREATE TABLE {table['name']} (")
            lines.append(f"  {table['column']} {table['type']}")
            lines.append(");")

        self._schema_text = "\n".join(lines)

        dict_lines = ["-- 字典映射", ""]
        for d in dicts:
            dict_lines.append(f"-- {d['dict_type']}: {d['dict_label']} = {d['dict_value']}")
        self._dict_text = "\n".join(dict_lines)

        self._last_refresh = datetime.now()

    async def _load_tables(self):
        sql = """
        SELECT TABLE_NAME as name, COLUMN_NAME as column, COLUMN_TYPE as type
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
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


schema_cache = SchemaCache()
