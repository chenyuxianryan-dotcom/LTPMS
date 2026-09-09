"""Agent 查询服务：/agent 端点的业务编排。

职责：技能加载 + 长期记忆读取 → 调用 run_agent → 成功后写回上下文摘要。
把「加载什么技能/记忆」与「Agent 循环怎么跑」解耦，便于测试与复用。
"""

from functools import lru_cache

from app.config import settings
from app.services import agent_loop, skill_loader
from app.services.memory_store import MemoryStore


@lru_cache(maxsize=1)
def _get_memory_store() -> MemoryStore:
    return MemoryStore(settings.memory_db_path)


async def run_agent_query(
    question: str,
    role: str,
    user_id: int,
    history: list[dict] | None = None,
    memory_store: MemoryStore | None = None,
    skills=None,
) -> dict:
    """执行一次 Agent 查询并持久化上下文摘要。

    memory_store / skills 可注入（测试用），默认从配置加载。
    """
    if memory_store is None:
        memory_store = _get_memory_store()
    if skills is None:
        skills = skill_loader.discover_skills(settings.skills_dir)

    memory = memory_store.list(user_id)
    result = await agent_loop.run_agent(
        question, role, user_id, history=history, skills=skills, memory=memory
    )

    # 只在成功产出答案时写回上下文摘要，失败不污染记忆
    if result["error"] is None:
        memory_store.set(user_id, "last_context", question[:200])

    return result
