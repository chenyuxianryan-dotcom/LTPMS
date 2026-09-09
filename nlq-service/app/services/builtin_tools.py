"""内置工具定义与注册。

把系统能力以「工具」形式暴露给 Agent。query_data 是首个内置工具，
复用 execute_query（NL2SQL 全链路，天然带权限注入与 SQL 安全校验）。
"""

from app.services.query_service import execute_query
from app.services.tool_registry import Tool, register_tool


async def _query_data_executor(question: str, role: str = "ADMIN", user_id: int = 0, **kwargs):
    """query_data 执行器：把 LLM 传来的 question 与鉴权上下文透传给 execute_query。"""
    return await execute_query(question, role, user_id)


QUERY_DATA_TOOL = Tool(
    name="query_data",
    description="对实验教学管理系统执行只读的自然语言查询，返回结构化表格数据",
    parameters={
        "type": "object",
        "properties": {
            "question": {
                "type": "string",
                "description": "用户的自然语言问题，将转换为只读 SELECT 查询",
            }
        },
        "required": ["question"],
    },
    executor=_query_data_executor,
)


def register_builtin_tools() -> None:
    """注册全部内置工具。后续 report_stats、导入草稿等在此追加。"""
    register_tool(QUERY_DATA_TOOL)
