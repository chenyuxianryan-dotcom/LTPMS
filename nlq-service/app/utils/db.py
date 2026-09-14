import aiomysql
from app.config import settings


class Database:
    def __init__(self):
        self.pool = None

    async def connect(self):
        self.pool = await aiomysql.create_pool(
            host=settings.db_host,
            port=settings.db_port,
            user=settings.db_user,
            password=settings.db_password,
            db=settings.db_name,
            charset="utf8mb4",
            autocommit=True,
            minsize=1,
            maxsize=10,
        )

    async def disconnect(self):
        if self.pool:
            self.pool.close()
            await self.pool.wait_closed()

    async def execute(self, sql: str, params: tuple = ()):
        async with self.pool.acquire() as conn:
            async with conn.cursor(aiomysql.DictCursor) as cur:
                await cur.execute(sql, params)
                return await cur.fetchall()

    async def execute_with_columns(self, sql: str, params: tuple = ()):
        """与 execute 相同，但额外返回列名（0 行结果也能保留列结构）"""
        async with self.pool.acquire() as conn:
            async with conn.cursor(aiomysql.DictCursor) as cur:
                await cur.execute(sql, params)
                rows = await cur.fetchall()
                columns = [d[0] for d in (cur.description or ())]
                return columns, rows


db = Database()
