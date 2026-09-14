"""人名意图路由：判断提问是否指向系统中的某个具体人员。

实现方式刻意选择"内存姓名索引 + 最长子串匹配"而非分类模型或通用分词：
本系统全部人员姓名的取值域极小（user.real_name ∪ laboratory.manager ∪
user.username，量级几十），把整个取值域装进内存后做最长匹配，
既零外部依赖、又天然规避「刘思」误命中「刘思思」这类子串包含陷阱
——按长度降序匹配，长名必先于短名被消费。

命中后由调用方注入 person_dossier 技能并收紧工具集；未命中则完全不注入，
避免八维清单污染普通问答。
"""

import time

from app.utils.db import db

_CACHE_TTL_SECONDS = 300


class PersonNameIndex:
    def __init__(self, ttl_seconds: int = _CACHE_TTL_SECONDS):
        self._ttl = ttl_seconds
        self._loaded_at = 0.0
        self._names: list[str] = []

    async def refresh(self) -> None:
        rows = await db.execute(
            "SELECT real_name AS name FROM user WHERE real_name IS NOT NULL AND real_name <> ''"
            " UNION SELECT manager FROM laboratory WHERE manager IS NOT NULL AND manager <> ''"
            " UNION SELECT username FROM user WHERE username IS NOT NULL AND username <> ''"
        )
        # 去重后按长度降序：长名优先匹配，避免短名吞掉长名的前缀
        names = {r["name"] for r in rows if r.get("name")}
        self._names = sorted(names, key=len, reverse=True)
        self._loaded_at = time.time()

    async def ensure_fresh(self) -> None:
        if not self._names or (time.time() - self._loaded_at) > self._ttl:
            await self.refresh()

    def match(self, question: str) -> str | None:
        """返回提问中出现的、索引里最长的那个姓名/工号；无命中返回 None。"""
        if not question:
            return None
        for name in self._names:
            if name in question:
                return name
        return None

    def clear(self) -> None:
        self._names = []
        self._loaded_at = 0.0


person_name_index = PersonNameIndex()
