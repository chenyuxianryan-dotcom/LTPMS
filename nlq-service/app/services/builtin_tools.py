"""内置工具定义与注册。

把系统能力以「工具」形式暴露给 Agent。query_data 是首个内置工具，
复用 execute_query（NL2SQL 全链路，天然带权限注入与 SQL 安全校验）；
generate_text 是共享文本生成工具（材料清单/档案/报告共用，防臃肿）。
"""

from app.services.query_service import execute_query
from app.services.text_generation import generate_text
from app.services.tool_registry import Tool, register_tool


async def _query_data_executor(question: str, role: str = "ADMIN", user_id: int = 0, **kwargs):
    """query_data 执行器：把 LLM 传来的 question 与鉴权上下文透传给 execute_query。"""
    return await execute_query(question, role, user_id)


async def _generate_text_executor(prompt: str, **kwargs):
    """generate_text 执行器：按 prompt 生成文本，忽略鉴权上下文（纯文本生成）。"""
    return await generate_text(prompt)


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

GENERATE_TEXT_TOOL = Tool(
    name="generate_text",
    description="生成一段文本（材料清单、数字档案、报告叙事等）",
    parameters={
        "type": "object",
        "properties": {
            "prompt": {
                "type": "string",
                "description": "文本生成需求描述，包含必要的上下文信息",
            }
        },
        "required": ["prompt"],
    },
    executor=_generate_text_executor,
)


def register_builtin_tools() -> None:
    """注册全部内置工具。后续 report_stats、导入草稿等在此追加。"""
    register_tool(QUERY_DATA_TOOL)
    register_tool(GENERATE_TEXT_TOOL)

