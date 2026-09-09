"""MCP SSE 传输验证（一次性）：客户端经 SSE 连接并调用工具。"""

import asyncio
import json
import sys

from mcp import ClientSession
from mcp.client.sse import sse_client


async def main() -> int:
    url = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8800/sse"
    async with sse_client(url) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = await session.list_tools()
            print("tools:", [t.name for t in tools.tools])
            result = await session.call_tool("query_laboratories", {"limit": 3})
            text = result.content[0].text
            payload = json.loads(text)
            print("call query_laboratories →", payload["success"], payload["count"])
            result = await session.call_tool("query_schedules", {"lab_code": "36-401"})
            payload = json.loads(result.content[0].text)
            print("call query_schedules(36-401) →", payload["success"], payload["count"])
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
