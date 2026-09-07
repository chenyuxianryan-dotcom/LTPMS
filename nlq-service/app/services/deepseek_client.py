import httpx

from app.config import settings


async def call_deepseek(messages: list[dict]) -> str:
    headers = {
        "Authorization": f"Bearer {settings.deepseek_api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": settings.deepseek_model,
        "messages": messages,
        "temperature": 0.1,
    }

    timeout = httpx.Timeout(settings.deepseek_timeout)
    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.post(settings.deepseek_api_url, json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()
        return data["choices"][0]["message"]["content"].strip()
