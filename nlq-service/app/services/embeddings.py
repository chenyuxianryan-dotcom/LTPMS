"""文本向量化：DashScope text-embedding-v4（OpenAI 兼容端点）。

与 vision.py 同构：httpx 直连、不引入厂商 SDK。key 未配置或调用失败时抛
EmbeddingUnavailableError，由调用方（/embed 端点、知识库检索工具）转成可读文案，
避免异常穿透到 Agent 循环。

text_type 区分编码方向：document（入库文本）/ query（检索提问），
DashScope 侧按此优化向量方向，检索召回率显著优于混用同一编码。
"""

import httpx

from app.config import settings

# DashScope 单请求上限 10 条（北京地域），内部自动分批
_BATCH_SIZE = 10


class EmbeddingUnavailableError(Exception):
    """向量化服务不可用（未配置 / 网络失败 / 模型侧报错）"""


async def embed_texts(texts: list[str], text_type: str = "document") -> list[list[float]]:
    """批量向量化，返回与 texts 等长、顺序一致的向量列表（维度 = settings.embedding_dim）。"""
    if not settings.dashscope_api_key:
        raise EmbeddingUnavailableError("向量化服务未配置（DASHSCOPE_API_KEY 为空）")
    if not texts:
        return []

    vectors: list[list[float]] = []
    for i in range(0, len(texts), _BATCH_SIZE):
        vectors.extend(await _embed_batch(texts[i : i + _BATCH_SIZE], text_type))
    return vectors


async def _embed_batch(texts: list[str], text_type: str) -> list[list[float]]:
    payload = {
        "model": settings.embedding_model,
        "input": texts,
        "dimensions": settings.embedding_dim,
        "text_type": text_type,
        "encoding_format": "float",
    }
    headers = {
        "Authorization": f"Bearer {settings.dashscope_api_key}",
        "Content-Type": "application/json",
    }
    try:
        timeout = httpx.Timeout(settings.embedding_timeout)
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(settings.embedding_api_url, json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()
    except Exception as e:
        raise EmbeddingUnavailableError(f"向量化调用失败：{e}") from e

    # 兼容端点不保证返回顺序，按 index 排序后再取值
    items = sorted(data.get("data") or [], key=lambda d: d.get("index", 0))
    if len(items) != len(texts):
        raise EmbeddingUnavailableError("向量化返回条数与请求不一致")
    return [item["embedding"] for item in items]
