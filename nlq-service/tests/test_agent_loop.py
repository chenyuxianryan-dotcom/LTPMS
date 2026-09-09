"""Agent 循环（计划→调工具→观察→回答）的行为测试。

用注入的 fake chat_fn 与 fake 工具模拟 LLM 与工具，不真连 DeepSeek / DB。
重点覆盖：无工具直答、单次工具调用、未知工具容错、max_steps 熔断、LLM 异常、权限上下文注入。
"""

import pytest

from app.services import agent_loop
from app.services.tool_registry import Tool, register_tool, clear_registry


@pytest.fixture(autouse=True)
def _clean():
    clear_registry()
    yield
    clear_registry()


def _make_fake_chat(replies):
    """replies: 依次返回的 message dict 列表（模拟多轮 LLM）。"""
    calls = []

    async def chat_fn(messages, tools):
        calls.append({"messages": messages, "tools": tools})
        if not replies:
            raise AssertionError("fake chat_fn 无更多回复")
        return replies.pop(0)

    chat_fn.calls = calls
    return chat_fn


def _text_message(content):
    return {"role": "assistant", "content": content}


def _tool_call_message(name, arguments, call_id="call_1"):
    return {
        "role": "assistant",
        "content": None,
        "tool_calls": [
            {
                "id": call_id,
                "type": "function",
                "function": {"name": name, "arguments": arguments},
            }
        ],
    }


@pytest.mark.asyncio
async def test_agent_answers_without_tool():
    chat_fn = _make_fake_chat([_text_message("你好，我是阿实")])
    result = await agent_loop.run_agent("你好", "ADMIN", 1, chat_fn=chat_fn)

    assert result["answer"] == "你好，我是阿实"
    assert result["steps"] == []
    assert result["steps_used"] == 0
    assert result["error"] is None


@pytest.mark.asyncio
async def test_agent_calls_tool_then_answers():
    captured = {}

    async def fake_executor(question, role="ADMIN", user_id=0, **kwargs):
        captured["question"] = question
        captured["role"] = role
        captured["user_id"] = user_id
        return {"success": True, "rows": [[3]], "row_count": 1}

    register_tool(
        Tool(
            name="query_data",
            description="查询数据",
            parameters={"type": "object", "properties": {"question": {"type": "string"}}, "required": ["question"]},
            executor=fake_executor,
        )
    )

    chat_fn = _make_fake_chat(
        [
            _tool_call_message("query_data", '{"question": "有多少实验室"}'),
            _text_message("共有 3 间实验室"),
        ]
    )
    result = await agent_loop.run_agent("有多少实验室", "TEACHER", 42, chat_fn=chat_fn)

    assert result["answer"] == "共有 3 间实验室"
    assert result["steps_used"] == 1
    assert len(result["steps"]) == 1
    step = result["steps"][0]
    assert step["tool"] == "query_data"
    assert step["arguments"] == {"question": "有多少实验室"}
    # 权限上下文必须由运行时注入，不能由 LLM 伪造
    assert captured["role"] == "TEACHER"
    assert captured["user_id"] == 42
    assert result["error"] is None


@pytest.mark.asyncio
async def test_agent_injects_role_user_id_ignoring_llm_fake():
    captured = {}

    async def fake_executor(question, role="ADMIN", user_id=0, **kwargs):
        captured["role"] = role
        captured["user_id"] = user_id
        return {"success": True}

    register_tool(
        Tool(
            name="query_data",
            description="查询数据",
            parameters={"type": "object", "properties": {"question": {"type": "string"}}, "required": ["question"]},
            executor=fake_executor,
        )
    )

    # LLM 恶意传入 role=ADMIN, user_id=0 想越权，运行时必须覆盖为真实上下文
    chat_fn = _make_fake_chat(
        [
            _tool_call_message("query_data", '{"question": "q", "role": "ADMIN", "user_id": 0}'),
            _text_message("ok"),
        ]
    )
    await agent_loop.run_agent("q", "TEACHER", 7, chat_fn=chat_fn)

    assert captured["role"] == "TEACHER"
    assert captured["user_id"] == 7


@pytest.mark.asyncio
async def test_agent_unknown_tool_graceful():
    chat_fn = _make_fake_chat(
        [
            _tool_call_message("nonexistent_tool", '{"x": 1}'),
            _text_message("抱歉，我无法使用该工具"),
        ]
    )
    result = await agent_loop.run_agent("hi", "ADMIN", 1, chat_fn=chat_fn)

    # 未知工具不 crash，LLM 收到错误反馈后可继续回答
    assert result["answer"] == "抱歉，我无法使用该工具"
    assert result["error"] is None


@pytest.mark.asyncio
async def test_agent_max_steps_breaker():
    # LLM 一直要求调工具，永不给出最终回答 → 达到 max_steps 熔断
    async def chat_fn(messages, tools):
        return _tool_call_message("query_data", '{"question": "q"}')

    async def fake_executor(question, role="ADMIN", user_id=0, **kwargs):
        return {"success": True}

    register_tool(
        Tool(
            name="query_data",
            description="查询数据",
            parameters={"type": "object", "properties": {"question": {"type": "string"}}, "required": ["question"]},
            executor=fake_executor,
        )
    )

    result = await agent_loop.run_agent("q", "ADMIN", 1, chat_fn=chat_fn, max_steps=3)

    assert result["steps_used"] == 3
    assert result["error"] is not None
    assert "轮数" in result["error"] or "步骤" in result["error"]


@pytest.mark.asyncio
async def test_agent_llm_error():
    async def chat_fn(messages, tools):
        raise RuntimeError("上游超时")

    result = await agent_loop.run_agent("hi", "ADMIN", 1, chat_fn=chat_fn)

    assert result["answer"] == ""
    assert result["error"] is not None
    assert "超时" in result["error"] or "RuntimeError" in result["error"] or "失败" in result["error"]


@pytest.mark.asyncio
async def test_agent_tool_executor_error_is_fed_back():
    async def fake_executor(question, role="ADMIN", user_id=0, **kwargs):
        raise RuntimeError("数据库连接失败")

    register_tool(
        Tool(
            name="query_data",
            description="查询数据",
            parameters={"type": "object", "properties": {"question": {"type": "string"}}, "required": ["question"]},
            executor=fake_executor,
        )
    )

    chat_fn = _make_fake_chat(
        [
            _tool_call_message("query_data", '{"question": "q"}'),
            _text_message("数据暂时查不到，请稍后再试"),
        ]
    )
    result = await agent_loop.run_agent("q", "ADMIN", 1, chat_fn=chat_fn)

    assert result["answer"] == "数据暂时查不到，请稍后再试"
    # 工具异常被包装成 step，不中断循环
    assert result["steps"][0]["result"]["error"] is not None
    assert result["error"] is None


@pytest.mark.asyncio
async def test_agent_injects_skills_into_system_prompt():
    from app.services.skill_loader import Skill

    chat_fn = _make_fake_chat([_text_message("好的")])
    skills = [
        Skill(name="experiment_stat", description="统计", prompt="统计实验项目", allowed_tools=("query_data",))
    ]

    await agent_loop.run_agent("hi", "ADMIN", 1, chat_fn=chat_fn, skills=skills)

    system = chat_fn.calls[0]["messages"][0]["content"]
    assert "experiment_stat" in system
    assert "统计实验项目" in system


@pytest.mark.asyncio
async def test_agent_injects_memory_into_system_prompt():
    chat_fn = _make_fake_chat([_text_message("好的")])

    await agent_loop.run_agent(
        "hi", "ADMIN", 1, chat_fn=chat_fn,
        memory={"last_context": "上次在调 CS101 的实验项目", "preference": "简洁"},
    )

    system = chat_fn.calls[0]["messages"][0]["content"]
    assert "上次在调 CS101 的实验项目" in system
    assert "简洁" in system
