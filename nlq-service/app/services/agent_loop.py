"""Agent 循环：计划 → 调用工具 → 观察 → 回答，直到产出最终文本或触发熔断。

与 SQL 生成链路共用 chat_completion 底层；工具执行严格注入运行时权限上下文
（role / user_id 由服务端注入，绝不信任 LLM 传来的鉴权字段，防止越权）。

两项与人员档案相关的机制：
- 双份载荷：回喂模型的只取 model_view（脱敏摘要），display 全量行仅随 steps 上屏；
- 完成度断言：required_dimensions 里的维度必须被调用过，模型想提前收尾时追加
  一轮补齐提示；二次仍缺则在返回体写 missing_dimensions，由前端标灰。
"""

import json
import time

from app.services.deepseek_client import chat_completion
from app.services.person_tools import PERSON_DOSSIER_DIMENSIONS
from app.services.prompt_builder import AGENT_SYSTEM_PROMPT
from app.services.skill_loader import Skill, format_skills_prompt
from app.services.tool_registry import get_tool, tool_schemas

# 完成度补漏最多触发一次（决策 D4：补一轮，二次仍缺则显式声明）
_MAX_COMPLETION_NUDGES = 1


async def run_agent(
    question: str,
    role: str,
    user_id: int,
    history: list[dict] | None = None,
    max_steps: int = 3,
    chat_fn=None,
    skills: list[Skill] | None = None,
    memory: dict[str, str] | None = None,
    tool_allowlist: tuple[str, ...] | None = None,
    required_dimensions: tuple[str, ...] = (),
    forced_person_ref: str | None = None,
) -> dict:
    """执行一次多轮 Agent 对话，返回最终答案与工具调用轨迹。

    chat_fn: 可注入的底层调用（签名 async (messages, tools) -> message dict），
             默认走 chat_completion，测试注入 fake。
    skills:  可选的技能包列表，注入系统提示，赋予 Agent 领域行为模板。
    memory:  可选的当前用户长期记忆（键值对），注入系统提示，实现跨会话延续。
    tool_allowlist: 限定本轮可用的工具名；为空表示不限制（普通问答走全量）。
    required_dimensions: 必须被调用到的工具名清单，用于完成度断言。
             仅在 resolve_person 返回 exact（或本入口已锁定人员）时生效：
             denied / not_found / ambiguous / manager_only 时断言自动失效，
             不会把模型推去取本不该取的维度。
    forced_person_ref: 已锁定的人员引用（「我的档案」入口）。设置后跳过 resolve_person，
             且各维工具的 person_ref 参数由服务端强制覆写为该值，模型无从篡改。

    返回结构：answer / steps / steps_used / error / missing_dimensions。
    """
    if chat_fn is None:
        chat_fn = _default_chat

    system_content = AGENT_SYSTEM_PROMPT
    if skills:
        system_content += format_skills_prompt(skills)
    if forced_person_ref:
        system_content += _locked_person_note(forced_person_ref)
    if memory:
        system_content += _format_memory(memory)

    messages = [
        {"role": "system", "content": system_content},
    ]
    if history:
        messages.extend(history)
    messages.append({"role": "user", "content": question})

    steps: list[dict] = []
    called_tools: set[str] = set()
    tools = _select_tools(tool_allowlist)
    nudges_used = 0
    # 完成度断言只在人已唯一定位（exact）时才成立：denied / not_found / ambiguous /
    # manager_only 四种结局本就不该取满八维，断言必须随之失效，否则会把模型逼进死胡同。
    required = required_dimensions

    try:
        for _ in range(max_steps):
            message = await chat_fn(messages, tools)

            tool_calls = message.get("tool_calls") or []
            if not tool_calls:
                missing = _missing_dimensions(required, called_tools)
                if missing and nudges_used < _MAX_COMPLETION_NUDGES:
                    nudges_used += 1
                    messages.append(message)
                    messages.append({"role": "user", "content": _completion_nudge(missing)})
                    continue
                return {
                    "answer": (message.get("content") or "").strip(),
                    "steps": steps,
                    "steps_used": len(steps),
                    "error": None,
                    "missing_dimensions": missing,
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
                if forced_person_ref and name in PERSON_DOSSIER_DIMENSIONS:
                    exec_arguments["person_ref"] = forced_person_ref

                result = await _execute_tool(name, exec_arguments)
                called_tools.add(name)
                if name == "resolve_person" and _resolve_status(result) != "exact":
                    required = ()
                steps.append({"tool": name, "arguments": raw_arguments, "result": result})

                messages.append(
                    {
                        "role": "tool",
                        "tool_call_id": tc.get("id", ""),
                        "content": _model_payload(result),
                    }
                )

        # 达到 max_steps 仍未产出最终答案 → 熔断
        return {
            "answer": "",
            "steps": steps,
            "steps_used": len(steps),
            "error": f"达到最大工具调用轮数（{max_steps}），已终止",
            "missing_dimensions": _missing_dimensions(required, called_tools),
        }

    except Exception as e:
        return {
            "answer": "",
            "steps": steps,
            "steps_used": len(steps),
            "error": f"Agent 执行失败：{e}",
            "missing_dimensions": _missing_dimensions(required, called_tools),
        }


def _select_tools(allowlist: tuple[str, ...] | None) -> list[dict]:
    """按白名单裁剪工具集；白名单为空则下发全量（普通问答路径行为不变）。"""
    schemas = tool_schemas()
    if not allowlist:
        return schemas
    allowed = set(allowlist)
    return [s for s in schemas if s["function"]["name"] in allowed]


def _model_payload(result) -> str:
    """回喂模型的内容：优先 model_view（脱敏摘要），否则回退完整结果。

    人员档案工具的 display 全量行只随 steps 上屏，不进 LLM 上下文。
    """
    if isinstance(result, dict) and "model_view" in result:
        return json.dumps(result["model_view"], ensure_ascii=False, default=str)
    return json.dumps(result, ensure_ascii=False, default=str)


def _missing_dimensions(required: tuple[str, ...], called: set[str]) -> list[str]:
    return [name for name in required if name not in called]


def _resolve_status(result) -> str | None:
    """取 resolve_person 结果的定位状态（exact / ambiguous / manager_only / not_found / denied）。"""
    if not isinstance(result, dict):
        return None
    if result.get("status"):
        return str(result["status"])
    model_view = result.get("model_view")
    if isinstance(model_view, dict) and model_view.get("status"):
        return str(model_view["status"])
    return None


def _completion_nudge(missing: list[str]) -> str:
    return (
        "你还未获取以下维度：" + "、".join(missing) + "。"
        "请立刻在同一条回复里并行调用这些工具补齐，然后再汇总作答；"
        "不要重复调用已完成的工具，也不要省略任何维度。"
    )


def _locked_person_note(person_ref: str) -> str:
    return (
        f"\n\n【本次已锁定人员｜跳过第 1 步】person_ref = {person_ref}（当前登录用户本人）。"
        "resolve_person 不在本次工具集中，直接从第 2 步开始："
        "在同一条回复里并行调用全部八维工具，person_ref 一律传该值。"
    )


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
