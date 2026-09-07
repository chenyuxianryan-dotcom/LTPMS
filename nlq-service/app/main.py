from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import settings
from app.middleware.internal_auth import InternalAuthMiddleware
from app.routers import nlq
from app.utils.db import db


@asynccontextmanager
async def lifespan(app: FastAPI):
    await db.connect()
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
