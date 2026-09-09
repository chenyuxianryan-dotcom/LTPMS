import httpx

from app.config import settings


async def chat_completion(
    messages: list[dict],
    tools: list[dict] | None = None,
    temperature: float = 0.1,
) -> dict:
    """调用 DeepSeek chat/completions，返回 choices[0].message（可能含 tool_calls）。

    这是与 LLM 交互的单一底层入口：SQL 生成与 Agent 循环共用，避免重复拼请求。
    """
    headers = {
        "Authorization": f"Bearer {settings.deepseek_api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": settings.deepseek_model,
        "messages": messages,
        "temperature": temperature,
    }
    if tools:
        payload["tools"] = tools

    timeout = httpx.Timeout(settings.deepseek_timeout)
    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.post(settings.deepseek_api_url, json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()
        return data["choices"][0]["message"]


async def call_deepseek(messages: list[dict]) -> str:
    """兼容旧接口：只取文本 content（供 SQL 生成链路使用）。"""
    message = await chat_completion(messages)
    return (message.get("content") or "").strip()
