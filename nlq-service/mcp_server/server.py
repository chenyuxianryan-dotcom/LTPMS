"""LTPMS MCP Server 入口（V3）。

把系统数据以只读 MCP 工具暴露给外部 AI 工具（WorkBuddy / Claude 等）：
  - query_laboratories / query_courses / query_experiments / query_schedules

部署方式（任务清单 T3.1.2）：
  - stdio：外部客户端以子进程方式拉起（WorkBuddy/Claude Desktop 本地接入）
      python -m mcp_server.server --transport stdio
  - sse ：独立小服务，SSE 暴露（远程/调试）
      python -m mcp_server.server --transport sse

安全（T3.1.3）：
  - MCP_ENABLED=false 时拒绝启动（生产可配置禁用）
  - 复用 ltpms_readonly 只读账号，架构上无写路径
  - SSE 模式可设置 MCP_TOKEN 访问令牌；stdio 为本机子进程不校验
"""

import argparse
import sys

from mcp.server.fastmcp import FastMCP

from app.config import settings
from mcp_server import queries

mcp = FastMCP(
    "ltpms-readonly",
    instructions=(
        "LTPMS（实验教学项目管理系统）只读数据服务。"
        "可查询实验室、实验课程、实验项目与排课数据；未指定学期时默认当前学期。"
    ),
)


@mcp.tool()
async def query_laboratories(
    lab_code: str | None = None, status: int | None = None, limit: int = 20
) -> dict:
    """查询实验室列表（只读）。

    Args:
        lab_code: 实验室编号（如 36-401），可选
        status: 启用状态过滤，0=停用 1=启用，可选
        limit: 返回条数上限（默认 20）
    """
    return await queries.query_laboratories(lab_code=lab_code, status=status, limit=limit)


@mcp.tool()
async def query_courses(
    semester_name: str | None = None,
    course_code: str | None = None,
    course_name_keyword: str | None = None,
    limit: int = 20,
) -> dict:
    """查询实验课程列表（只读）。未指定学期时默认当前学期。

    Args:
        semester_name: 学期名称（如 2026-2027-1），可选
        course_code: 课程号精确匹配（如 CS101），可选
        course_name_keyword: 课程名关键字模糊匹配，可选
        limit: 返回条数上限（默认 20）
    """
    return await queries.query_courses(
        semester_name=semester_name,
        course_code=course_code,
        course_name_keyword=course_name_keyword,
        limit=limit,
    )


@mcp.tool()
async def query_experiments(
    course_code: str | None = None,
    experiment_name_keyword: str | None = None,
    semester_name: str | None = None,
    limit: int = 50,
) -> dict:
    """查询实验项目列表（只读）。未指定学期时默认当前学期。

    Args:
        course_code: 按课程号过滤，可选
        experiment_name_keyword: 实验名称关键字模糊匹配，可选
        semester_name: 学期名称，可选
        limit: 返回条数上限（默认 50）
    """
    return await queries.query_experiments(
        course_code=course_code,
        experiment_name_keyword=experiment_name_keyword,
        semester_name=semester_name,
        limit=limit,
    )


@mcp.tool()
async def query_schedules(
    lab_code: str | None = None,
    semester_name: str | None = None,
    course_code: str | None = None,
    limit: int = 50,
) -> dict:
    """查询排课/课表记录（只读）。未指定学期时默认当前学期。

    例如「36-401 这学期排了哪些课」→ 传 lab_code="36-401" 即可。

    Args:
        lab_code: 实验室编号（如 36-401），可选
        semester_name: 学期名称（如 2026-2027-1），可选
        course_code: 课程号精确匹配，可选
        limit: 返回条数上限（默认 50）
    """
    return await queries.query_schedules(
        lab_code=lab_code, semester_name=semester_name, course_code=course_code, limit=limit
    )


class _SseTokenMiddleware:
    """SSE 访问令牌校验：MCP_TOKEN 非空时，请求必须携带 X-MCP-Token 头。"""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http" and settings.mcp_token:
            headers = dict(scope.get("headers") or [])
            token = headers.get(b"x-mcp-token", b"").decode()
            if token != settings.mcp_token:
                await send(
                    {
                        "type": "http.response.start",
                        "status": 401,
                        "headers": [(b"content-type", b"text/plain")],
                    }
                )
                await send({"type": "http.response.body", "body": b"invalid mcp token"})
                return
        await self.app(scope, receive, send)


def build_sse_asgi():
    """构建带令牌校验的 SSE ASGI 应用（独立小服务部署用）。"""
    return _SseTokenMiddleware(mcp.sse_app())


def main() -> None:
    parser = argparse.ArgumentParser(description="LTPMS MCP Server（只读）")
    parser.add_argument("--transport", choices=["stdio", "sse"], default="stdio")
    args = parser.parse_args()

    # 安全开关：生产可配置禁用（T3.1.3）
    if not settings.mcp_enabled:
        print("MCP 服务已禁用（MCP_ENABLED=false），拒绝启动。", file=sys.stderr)
        sys.exit(0)

    if args.transport == "stdio":
        # stdio：本机子进程，复用 ltpms_readonly 连接
        import anyio

        from app.utils.db import db

        async def _run_stdio():
            await db.connect()
            try:
                await mcp.run_stdio_async()
            finally:
                await db.disconnect()

        anyio.run(_run_stdio)
    else:
        # sse：独立小服务；连接池与 uvicorn 事件循环绑定，经 lifespan 管理
        import uvicorn
        from starlette.applications import Starlette

        from app.utils.db import db

        async def _on_startup():
            await db.connect()

        async def _on_shutdown():
            await db.disconnect()

        app = Starlette(
            on_startup=[_on_startup],
            on_shutdown=[_on_shutdown],
        )
        # sse_app 内部已挂载 sse/message 路由，外包一层令牌校验中间件
        app.mount("/", build_sse_asgi())
        uvicorn.run(app, host=settings.mcp_host, port=settings.mcp_port)


if __name__ == "__main__":
    main()
