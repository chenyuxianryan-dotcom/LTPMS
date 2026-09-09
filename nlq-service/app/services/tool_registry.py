"""工具注册表：Agent 工具的统一定义与注册中心。

工具四元组：名称 / 描述 / 参数 JSON Schema / 执行器（异步可调用）。
统一接口使新增工具零侵入注册，并为 LLM function calling 导出 schema。
"""

from dataclasses import dataclass, field
from typing import Any, Awaitable, Callable


@dataclass(frozen=True)
class Tool:
    name: str
    description: str
    parameters: dict
    executor: Callable[..., Awaitable[Any]] = field(repr=False, compare=False)


_registry: dict[str, Tool] = {}


def register_tool(tool: Tool) -> None:
    """注册工具；同名重复注册抛错，防止静默覆盖。"""
    if tool.name in _registry:
        raise ValueError(f"工具已注册: {tool.name}")
    _registry[tool.name] = tool


def get_tool(name: str) -> Tool | None:
    """按名查找工具，未注册返回 None。"""
    return _registry.get(name)


def list_tools() -> list[Tool]:
    """枚举全部已注册工具。"""
    return list(_registry.values())


def tool_schemas() -> list[dict]:
    """导出为 OpenAI function calling 的 tools 参数格式。"""
    return [
        {
            "type": "function",
            "function": {
                "name": t.name,
                "description": t.description,
                "parameters": t.parameters,
            },
        }
        for t in _registry.values()
    ]


def clear_registry() -> None:
    """清空注册表（测试隔离 / 重载用）。"""
    _registry.clear()
