"""Agent 查询服务：/agent 端点的业务编排。

职责：人名意图路由 → 技能选择 → 长期记忆读取 → 调用 run_agent → 写回上下文摘要。
把「加载什么技能/记忆/工具集」与「Agent 循环怎么跑」解耦，便于测试与复用。

路由命中人员姓名时：只注入 person_dossier 技能并收紧工具集到人档工具，
避免八维清单污染普通问答，也阻断模型绕道 query_data 自己猜 SQL。
"""

from functools import lru_cache

from app.config import settings
from app.services import agent_loop, skill_loader
from app.services.dossier_sql import get_user_by_ref
from app.services.intent_router import person_name_index
from app.services.memory_store import MemoryStore
from app.services.person_tools import PERSON_DOSSIER_DIMENSIONS

# 6 轮 = resolve + 并行扇出 + 汇总 + 消歧/补漏余量（决策 D4）
AGENT_MAX_STEPS = 6


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
    name_index=None,
    self_dossier: bool = False,
) -> dict:
    """执行一次 Agent 查询并持久化上下文摘要。

    memory_store / skills / name_index 可注入（测试用），默认从配置加载。
    self_dossier：「我的档案」入口，person_ref 由服务端锁定为当前登录用户，
    跳过 resolve_person 一轮，也天然满足「教师只能查本人」的权限断言。
    """
    if memory_store is None:
        memory_store = _get_memory_store()
    if skills is None:
        skills = skill_loader.discover_skills(settings.skills_dir)
    if name_index is None:
        name_index = person_name_index

    forced_ref = None
    if self_dossier:
        selected, allowlist, required, routed_name, forced_ref = await _self_route(skills, user_id)
    else:
        selected, allowlist, required, routed_name = await _route(question, skills, name_index)

    memory = memory_store.list(user_id)
    result = await agent_loop.run_agent(
        question, role, user_id, history=history, max_steps=AGENT_MAX_STEPS,
        skills=selected, memory=memory,
        tool_allowlist=allowlist, required_dimensions=required,
        forced_person_ref=forced_ref,
    )
    result["routed_person_name"] = routed_name

    # 只在成功产出答案时写回上下文摘要，失败不污染记忆
    if result["error"] is None:
        memory_store.set(user_id, "last_context", question[:200])

    return result


async def _self_route(skills, user_id: int) -> tuple[list, tuple | None, tuple, str | None, str | None]:
    """「我的档案」：锁定 person_ref 为当前用户，工具集去掉 resolve_person。"""
    dossier = [s for s in skills if s.name == "person_dossier"]
    me = await get_user_by_ref(str(user_id))
    if me is None:
        return dossier, PERSON_DOSSIER_DIMENSIONS, PERSON_DOSSIER_DIMENSIONS, None, str(user_id)
    return dossier, PERSON_DOSSIER_DIMENSIONS, PERSON_DOSSIER_DIMENSIONS, me["real_name"], me["username"]


async def _route(question: str, skills, name_index) -> tuple[list, tuple | None, tuple, str | None]:
    """人名意图路由：命中则锁定人档技能与工具集，未命中则排除人档技能。"""
    matched = await _match_name(question, name_index)
    if not matched:
        return [s for s in skills if s.name != "person_dossier"], None, (), None

    dossier = [s for s in skills if s.name == "person_dossier"]
    allowlist = tuple(sorted({t for s in dossier for t in s.allowed_tools}))
    return dossier, allowlist or None, PERSON_DOSSIER_DIMENSIONS, matched


async def _match_name(question: str, name_index) -> str | None:
    """路由失败不应影响主流程：索引不可用时退化为普通问答。"""
    try:
        await name_index.ensure_fresh()
        return name_index.match(question)
    except Exception:
        return None
