"""Agent 循环：计划 → 调用工具 → 观察 → 回答，直到产出最终文本或触发熔断。

与 SQL 生成链路共用 chat_completion 底层；工具执行严格注入运行时权限上下文
（role / user_id 由服务端注入，绝不信任 LLM 传来的鉴权字段，防止越权）。
"""

import json
import time

from app.services.deepseek_client import chat_completion
from app.services.prompt_builder import AGENT_SYSTEM_PROMPT
from app.services.skill_loader import Skill, format_skills_prompt
from app.services.tool_registry import get_tool, tool_schemas


async def run_agent(
    question: str,
    role: str,
    user_id: int,
    history: list[dict] | None = None,
    max_steps: int = 3,
    chat_fn=None,
    skills: list[Skill] | None = None,
    memory: dict[str, str] | None = None,
) -> dict:
    """执行一次多轮 Agent 对话，返回最终答案与工具调用轨迹。

    chat_fn: 可注入的底层调用（签名 async (messages, tools) -> message dict），
             默认走 chat_completion，测试注入 fake。
    skills:  可选的技能包列表，注入系统提示，赋予 Agent 领域行为模板。
    memory:  可选的当前用户长期记忆（键值对），注入系统提示，实现跨会话延续。

    返回结构：answer / steps / steps_used / error。
    """
    if chat_fn is None:
        chat_fn = _default_chat

    system_content = AGENT_SYSTEM_PROMPT
    if skills:
        system_content += format_skills_prompt(skills)
    if memory:
        system_content += _format_memory(memory)

    messages = [
        {"role": "system", "content": system_content},
    ]
    if history:
        messages.extend(history)
    messages.append({"role": "user", "content": question})

    steps: list[dict] = []
    tools = tool_schemas()

    try:
        for _ in range(max_steps):
            message = await chat_fn(messages, tools)

            tool_calls = message.get("tool_calls") or []
            if not tool_calls:
                return {
                    "answer": (message.get("content") or "").strip(),
                    "steps": steps,
                    "steps_used": len(steps),
                    "error": None,
                }

            # 记录 assistant 的工具调用请求
            messages.append(message)

            for tc in tool_calls:
                name = tc["function"]["name"]
                try:
                    raw_arguments = json.loads(tc["function"]["arguments"] or "{}")
                except json.JSONDecodeError:
                    raw_arguments = {}

                # 安全关键：权限上下文由服务端注入并覆盖，LLM 传入的 role/user_id 一律丢弃。
                # 轨迹里只记录 LLM 原始参数，不泄露/混淆运行时上下文。
                exec_arguments = dict(raw_arguments)
                exec_arguments["role"] = role
                exec_arguments["user_id"] = user_id

                result = await _execute_tool(name, exec_arguments)
                steps.append({"tool": name, "arguments": raw_arguments, "result": result})

                messages.append(
                    {
                        "role": "tool",
                        "tool_call_id": tc.get("id", ""),
                        "content": json.dumps(result, ensure_ascii=False, default=str),
                    }
                )

        # 达到 max_steps 仍未产出最终答案 → 熔断
        return {
            "answer": "",
            "steps": steps,
            "steps_used": len(steps),
            "error": f"达到最大工具调用轮数（{max_steps}），已终止",
        }

    except Exception as e:
        return {
            "answer": "",
            "steps": steps,
            "steps_used": len(steps),
            "error": f"Agent 执行失败：{e}",
        }


async def _default_chat(messages, tools):
    return await chat_completion(messages, tools=tools)


def _format_memory(memory: dict[str, str]) -> str:
    lines = ["", "## 关于当前用户（长期记忆）"]
    for k, v in memory.items():
        lines.append(f"- {k}: {v}")
    return "\n".join(lines)


async def _execute_tool(name: str, arguments: dict) -> dict:
    """执行工具并统一包装返回；未知工具 / 执行异常不向上抛，转为结构结果反馈给 LLM。"""
    tool = get_tool(name)
    if tool is None:
        return {"error": f"未知工具：{name}"}

    try:
        result = await tool.executor(**arguments)
        if isinstance(result, dict):
            return result
        return {"result": result}
    except Exception as e:
        return {"error": f"工具 {name} 执行失败：{e}"}
