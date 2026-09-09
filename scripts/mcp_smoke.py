"""MCP Server 只读冒烟验证（T3.4 验收）。

以 MCP 客户端身份经 stdio 连接 LTPMS MCP Server，枚举工具并逐一真实调用，
验证「外部 AI 工具可查 LTPMS 数据」。需 MySQL(3307) 可用；nlq-service 无需启动。

用法（在 nlq-service 目录下）：
  python ../scripts/mcp_smoke.py [--lab-code 36-401]
"""

import argparse
import asyncio
import json
import os
import sys

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

NLQ_SERVICE_DIR = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "nlq-service"))
PYTHON = sys.executable

TOOLCASES = [
    ("query_laboratories", {}),
    ("query_laboratories", {"status": 1, "limit": 5}),
    ("query_courses", {"limit": 5}),
    ("query_experiments", {"limit": 5}),
    # 验收用例：36-401 这学期排了哪些课（未传学期 → 自动解析当前学期）
    ("query_schedules", {}),
    ("query_schedules", {"limit": 5}),
]


def _shorten(value, width=160):
    text = json.dumps(value, ensure_ascii=False, default=str)
    return text if len(text) <= width else text[:width] + "…"


async def run(lab_code: str) -> int:
    server = StdioServerParameters(
        command=PYTHON,
        args=["-m", "mcp_server.server", "--transport", "stdio"],
        cwd=NLQ_SERVICE_DIR,
    )
    failures = 0
    async with stdio_client(server) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = await session.list_tools()
            names = [t.name for t in tools.tools]
            print(f"[1/3] 工具枚举: {names}")
            expected = {"query_laboratories", "query_courses", "query_experiments", "query_schedules"}
            missing = expected - set(names)
            if missing:
                print(f"  ✗ 缺少工具: {missing}")
                failures += 1

            print(f"[2/3] 工具调用（lab_code={lab_code}）:")
            # 实验室编号动态注入课表用例
            cases = []
            for tool, args in TOOLCASES:
                if tool == "query_schedules" and lab_code:
                    args = {**args, "lab_code": lab_code}
                cases.append((tool, args))

            for i, (tool, args) in enumerate(cases, 1):
                result = await session.call_tool(tool, args)
                texts = [c.text for c in result.content if c.type == "text"]
                payload = json.loads(texts[0]) if texts else {}
                status = "✓" if payload.get("success") else "✗"
                if not payload.get("success"):
                    failures += 1
                print(f"  {i}. {status} {tool}{args} → count={payload.get('count')} "
                      f"{_shorten(payload.get('rows', [])[:2])} {payload.get('error') or ''}")

            print("[3/3] 结论: " + ("全部通过" if failures == 0 else f"{failures} 个用例失败"))
    return 1 if failures else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--lab-code", default="36-401")
    sys.exit(asyncio.run(run(parser.parse_args().lab_code)))
