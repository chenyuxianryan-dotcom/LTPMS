"""Agent 服务编排（技能+记忆→run_agent→写回）与 /agent 端点的行为测试。"""

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.services import agent_loop, agent_service, skill_loader
from app.services.skill_loader import Skill


class _FakeStore:
    def __init__(self, initial=None):
        self.data = initial or {}
        self.written = {}

    def list(self, user_id):
        return self.data.get(user_id, {})

    def set(self, user_id, key, value):
        self.written[(user_id, key)] = value


@pytest.mark.asyncio
async def test_run_agent_query_orchestration(monkeypatch):
    captured = {}

    async def fake_run_agent(question, role, user_id, history=None, skills=None, memory=None, **kw):
        captured.update(
            question=question, role=role, user_id=user_id, history=history, skills=skills, memory=memory
        )
        return {"answer": "ok", "steps": [], "steps_used": 0, "error": None}

    monkeypatch.setattr(agent_loop, "run_agent", fake_run_agent)
    fake_skills = [Skill("experiment_stat", "统计", "统计实验项目")]
    monkeypatch.setattr(skill_loader, "discover_skills", lambda d: fake_skills)

    store = _FakeStore({1: {"preference": "简洁"}})
    result = await agent_service.run_agent_query("hi", "TEACHER", 1, memory_store=store)

    assert result["answer"] == "ok"
    assert captured["role"] == "TEACHER"
    assert captured["user_id"] == 1
    assert captured["skills"] == fake_skills
    assert captured["memory"] == {"preference": "简洁"}
    assert store.written[(1, "last_context")] == "hi"


@pytest.mark.asyncio
async def test_run_agent_query_does_not_write_memory_on_error(monkeypatch):
    async def fake_run_agent(question, role, user_id, history=None, skills=None, memory=None, **kw):
        return {"answer": "", "steps": [], "steps_used": 3, "error": "达到最大轮数"}

    monkeypatch.setattr(agent_loop, "run_agent", fake_run_agent)
    monkeypatch.setattr(skill_loader, "discover_skills", lambda d: [])

    store = _FakeStore()
    await agent_service.run_agent_query("hi", "ADMIN", 1, memory_store=store)
    assert (1, "last_context") not in store.written


@pytest.mark.asyncio
async def test_agent_endpoint_passthrough(monkeypatch):
    from app.routers import nlq as nlq_router

    captured = {}

    async def fake_run_agent_query(question, role, user_id, history=None, self_dossier=False):
        captured.update(
            question=question, role=role, user_id=user_id, history=history,
            self_dossier=self_dossier,
        )
        return {"answer": "你好", "steps": [], "steps_used": 0, "error": None}

    monkeypatch.setattr(nlq_router, "run_agent_query", fake_run_agent_query)

    app = FastAPI()
    app.include_router(nlq_router.router)

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.post(
            "/agent",
            json={"question": "q", "history": [{"role": "user", "content": "上文"}]},
            headers={"X-User-Id": "5", "X-User-Role": "TEACHER"},
        )

    assert resp.status_code == 200
    data = resp.json()
    assert data["answer"] == "你好"
    assert captured["user_id"] == 5
    assert captured["role"] == "TEACHER"
    assert captured["history"] == [{"role": "user", "content": "上文"}]
    assert captured["self_dossier"] is False


@pytest.mark.asyncio
async def test_agent_endpoint_passes_self_dossier(monkeypatch):
    """「我的档案」标志必须原样透传到业务层，由服务端负责锁定 person_ref。"""
    from app.routers import nlq as nlq_router

    captured = {}

    async def fake_run_agent_query(question, role, user_id, history=None, self_dossier=False):
        captured.update(self_dossier=self_dossier)
        return {"answer": "ok", "steps": [], "steps_used": 0, "error": None}

    monkeypatch.setattr(nlq_router, "run_agent_query", fake_run_agent_query)

    app = FastAPI()
    app.include_router(nlq_router.router)

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.post(
            "/agent",
            json={"question": "我的档案", "self_dossier": True},
            headers={"X-User-Id": "5", "X-User-Role": "TEACHER"},
        )

    assert resp.status_code == 200
    assert captured["self_dossier"] is True
