"""deepseek_client function calling 升级的特征化测试。

覆盖：chat_completion 返回 message（含 tool_calls）、tools 透传、call_deepseek 兼容。
不真连 DeepSeek，通过 monkeypatch 替换 httpx.AsyncClient。
"""

import pytest

from app.config import settings
from app.services import deepseek_client


def _install_fake(monkeypatch, return_payload):
    """把 deepseek_client.httpx.AsyncClient 替换为捕获请求参数的假客户端。"""
    captured = {}

    class _Resp:
        def raise_for_status(self):
            pass

        def json(self):
            return return_payload

    class _FakeClient:
        def __init__(self, **kwargs):
            captured["kwargs"] = kwargs

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

        async def post(self, url, json=None, headers=None):
            captured["url"] = url
            captured["json"] = json
            captured["headers"] = headers
            return _Resp()

    monkeypatch.setattr(deepseek_client.httpx, "AsyncClient", _FakeClient)
    return captured


def test_chat_completion_returns_message(monkeypatch):
    _install_fake(
        monkeypatch,
        {"choices": [{"message": {"role": "assistant", "content": "你好"}}]},
    )
    msg = _run(monkeypatch, deepseek_client.chat_completion, [{"role": "user", "content": "hi"}])
    assert msg == {"role": "assistant", "content": "你好"}


def test_chat_completion_passes_tools(monkeypatch):
    captured = _install_fake(
        monkeypatch,
        {"choices": [{"message": {"role": "assistant", "content": "ok"}}]},
    )
    tools = [{"type": "function", "function": {"name": "query_data"}}]
    _run(monkeypatch, deepseek_client.chat_completion, [{"role": "user", "content": "hi"}], tools=tools)

    assert "tools" in captured["json"]
    assert captured["json"]["tools"] == tools
    assert captured["json"]["model"] == settings.deepseek_model


def test_chat_completion_returns_tool_calls(monkeypatch):
    payload = {
        "choices": [
            {
                "message": {
                    "role": "assistant",
                    "content": None,
                    "tool_calls": [
                        {
                            "id": "call_1",
                            "type": "function",
                            "function": {"name": "query_data", "arguments": '{"question": "有多少实验室"}'},
                        }
                    ],
                }
            }
        ]
    }
    _install_fake(monkeypatch, payload)
    msg = _run(monkeypatch, deepseek_client.chat_completion, [{"role": "user", "content": "hi"}])
    assert msg["tool_calls"][0]["function"]["name"] == "query_data"


def test_call_deepseek_returns_content_string(monkeypatch):
    _install_fake(
        monkeypatch,
        {"choices": [{"message": {"role": "assistant", "content": "  SELECT 1  "}}]},
    )
    result = _run(monkeypatch, deepseek_client.call_deepseek, [{"role": "user", "content": "hi"}])
    assert result == "SELECT 1"


def test_call_deepseek_none_content_returns_empty(monkeypatch):
    _install_fake(
        monkeypatch,
        {"choices": [{"message": {"role": "assistant", "content": None}}]},
    )
    result = _run(monkeypatch, deepseek_client.call_deepseek, [{"role": "user", "content": "hi"}])
    assert result == ""


def _run(monkeypatch, fn, *args, **kwargs):
    import asyncio

    return asyncio.run(fn(*args, **kwargs))
