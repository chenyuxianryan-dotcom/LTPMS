import httpx

from app.config import settings

# 瞬时错误（超时 / 连接失败 / 5xx）才重试；4xx 与业务错误不重试
_TRANSIENT_STATUS = frozenset({500, 502, 503, 504})


async def chat_completion(
    messages: list[dict],
    tools: list[dict] | None = None,
    temperature: float = 0.1,
    timeout: float | None = None,
    max_retries: int = 1,
) -> dict:
    """调用 DeepSeek chat/completions，返回 choices[0].message（可能含 tool_calls）。

    这是与 LLM 交互的单一底层入口：SQL 生成与 Agent 循环共用，避免重复拼请求。
    timeout 为 per-call 覆盖（None 时用全局 settings.deepseek_timeout，默认 15s 不动）；
    瞬时错误（超时/连接失败/5xx）重试 ≤ max_retries 次。
    """
    headers = {
        "Authorization": f"Bearer {settings.deepseek_api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": settings.deepseek_model,
        "messages": messages,
        "temperature": temperature,
        # DeepSeek V4 默认 thinking 模式会多耗 reasoning token 且增加延迟；
        # NL2SQL / Agent 取结构化结果走 non-thinking 更稳更省
        "thinking": {"type": "disabled"},
    }
    if tools:
        payload["tools"] = tools

    effective_timeout = httpx.Timeout(settings.deepseek_timeout if timeout is None else timeout)
    attempt = 0
    while True:
        try:
            async with httpx.AsyncClient(timeout=effective_timeout) as client:
                response = await client.post(settings.deepseek_api_url, json=payload, headers=headers)
                response.raise_for_status()
                data = response.json()
                return data["choices"][0]["message"]
        except httpx.HTTPStatusError as exc:
            transient = exc.response.status_code in _TRANSIENT_STATUS
            if transient and attempt < max_retries:
                attempt += 1
                continue
            raise
        except (httpx.TimeoutException, httpx.ConnectError, httpx.ReadError):
            if attempt < max_retries:
                attempt += 1
                continue
            raise


async def call_deepseek(messages: list[dict], timeout: float | None = None) -> str:
    """兼容旧接口：只取文本 content（供 SQL 生成链路使用）。

    timeout 为 per-call 覆盖（报告等长文场景传更宽超时；None 时沿用全局 15s）。
    """
    message = await chat_completion(messages, timeout=timeout)
    return (message.get("content") or "").strip()

