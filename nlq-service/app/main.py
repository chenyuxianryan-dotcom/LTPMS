from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import settings
from app.middleware.internal_auth import InternalAuthMiddleware
from app.routers import nlq
from app.services.builtin_tools import register_builtin_tools
from app.services.knowledge_tools import register_knowledge_tools
from app.services.person_tools import register_person_tools
from app.utils.db import db


@asynccontextmanager
async def lifespan(app: FastAPI):
    await db.connect()
    # 启动时注册内置工具，否则 Agent 循环 tool_schemas() 为空、模型无法调用工具
    register_builtin_tools()
    register_person_tools()
    register_knowledge_tools()
    yield
    await db.disconnect()


app = FastAPI(
    title="LTPMS NL2SQL Service",
    description="基于 DeepSeek 的自然语言转 SQL 微服务",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(InternalAuthMiddleware)
app.include_router(nlq.router, prefix="", tags=["nlq"])


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=settings.host, port=settings.port)
