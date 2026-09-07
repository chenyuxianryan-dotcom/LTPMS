from fastapi import Request, HTTPException
from starlette.middleware.base import BaseHTTPMiddleware

from app.config import settings


class InternalAuthMiddleware(BaseHTTPMiddleware):
    """校验 X-Internal-Token，确保只有 Java 后端可调用本服务"""

    async def dispatch(self, request: Request, call_next):
        if request.url.path in ["/docs", "/openapi.json", "/redoc"]:
            return await call_next(request)

        token = request.headers.get("X-Internal-Token")
        if not token or token != settings.nlq_internal_token:
            raise HTTPException(status_code=403, detail="内部令牌校验失败")

        return await call_next(request)
