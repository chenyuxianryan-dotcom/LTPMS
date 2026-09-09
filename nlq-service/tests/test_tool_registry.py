"""tool_registry 工具注册表行为测试 —— TDD 红绿循环起点。

面向公共接口：Tool 数据类、register_tool、get_tool、list_tools、tool_schemas、clear_registry。
描述「工具如何被注册、枚举、按名查找、导出为 LLM function calling schema」。
"""

import pytest

from app.services.tool_registry import (
    Tool,
    clear_registry,
    get_tool,
    list_tools,
    register_tool,
    tool_schemas,
)


async def _fake_executor(**kwargs):
    return {"ok": True}


def _make_tool(name="query_data", description="只读查询", parameters=None):
    return Tool(
        name=name,
        description=description,
        parameters=parameters or {"type": "object", "properties": {}},
        executor=_fake_executor,
    )


@pytest.fixture(autouse=True)
def _clean_registry():
    clear_registry()
    yield
    clear_registry()


class TestRegisterAndList:
    def test_registered_tool_is_listed(self):
        tool = _make_tool()
        register_tool(tool)
        assert list_tools() == [tool]

    def test_multiple_tools_are_listed(self):
        register_tool(_make_tool(name="query_data"))
        register_tool(_make_tool(name="report_stats", description="报表统计"))
        assert {t.name for t in list_tools()} == {"query_data", "report_stats"}


class TestGetTool:
    def test_get_by_name(self):
        register_tool(_make_tool(name="query_data", description="只读查询"))
        tool = get_tool("query_data")
        assert tool is not None
        assert tool.description == "只读查询"

    def test_unknown_name_returns_none(self):
        assert get_tool("nope") is None


class TestToolSchemas:
    def test_exports_openai_function_calling_format(self):
        register_tool(_make_tool(name="query_data", description="只读查询"))
        assert tool_schemas() == [
            {
                "type": "function",
                "function": {
                    "name": "query_data",
                    "description": "只读查询",
                    "parameters": {"type": "object", "properties": {}},
                },
            }
        ]


class TestDuplicateRegistration:
    def test_duplicate_name_raises(self):
        register_tool(_make_tool(name="query_data"))
        with pytest.raises(ValueError):
            register_tool(_make_tool(name="query_data", description="重复注册"))
