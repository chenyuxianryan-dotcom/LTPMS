"""builtin_tools 内置工具注册行为测试。

描述 query_data 工具的 schema 与 executor 委托行为（executor 应把
question/role/user_id 透传给 execute_query），并锁定「应用启动时必须注册工具」的 wiring。
"""

import pytest

from app.services import builtin_tools
from app.services.tool_registry import clear_registry, get_tool, tool_schemas


class TestQueryDataTool:
    def test_schema_declares_question(self):
        t = builtin_tools.QUERY_DATA_TOOL
        assert t.name == "query_data"
        assert "question" in t.parameters["properties"]
        assert "question" in t.parameters["required"]

    def test_register_builtin_tools(self):
        clear_registry()
        builtin_tools.register_builtin_tools()
        assert get_tool("query_data") is builtin_tools.QUERY_DATA_TOOL

    async def test_executor_delegates_to_execute_query(self, monkeypatch):
        calls = {}

        async def fake_execute(question, role, user_id):
            calls["question"] = question
            calls["role"] = role
            calls["user_id"] = user_id
            return {"success": True, "rows": [["36-401"]]}

        monkeypatch.setattr(builtin_tools, "execute_query", fake_execute)

        result = await builtin_tools.QUERY_DATA_TOOL.executor(
            question="列出实验室", role="TEACHER", user_id=5
        )

        assert result["success"] is True
        assert calls == {"question": "列出实验室", "role": "TEACHER", "user_id": 5}


def test_app_lifespan_registers_builtin_tools(monkeypatch):
    """回归：此前 register_builtin_tools 只在测试里调用，应用启动从未注册，
    导致运行时 tool_schemas() 为空、模型无法调用工具。锁定 lifespan 的注册 wiring。"""
    import app.main as main_mod

    clear_registry()

    called = {}

    async def fake_connect():
        called["connect"] = True

    async def fake_disconnect():
        called["disconnect"] = True

    monkeypatch.setattr(main_mod.db, "connect", fake_connect)
    monkeypatch.setattr(main_mod.db, "disconnect", fake_disconnect)

    async def _run():
        async with main_mod.lifespan(main_mod.app):
            names = [s["function"]["name"] for s in tool_schemas()]
            assert "query_data" in names, "启动后 query_data 工具应已注册"

    import asyncio

    asyncio.run(_run())
    assert called == {"connect": True, "disconnect": True}
