"""builtin_tools 内置工具注册行为测试。

描述 query_data 工具的 schema 与 executor 委托行为（executor 应把
question/role/user_id 透传给 execute_query）。
"""

from app.services import builtin_tools
from app.services.tool_registry import clear_registry, get_tool


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
