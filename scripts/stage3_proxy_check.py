"""阶段3 Java 代理链路验收：登录 → 经 8080 JWT 鉴权调 /nlq/archive 与 /nlq/narrative。"""

import asyncio
import json
import sys

import httpx

BASE = "http://127.0.0.1:8080/api/v1"


async def main() -> int:
    failures = 0
    async with httpx.AsyncClient(timeout=90) as client:
        r = await client.post(f"{BASE}/auth/login", json={"username": "admin", "password": "admin123"})
        login = r.json()
        token = (login.get("data") or {}).get("token") or login.get("token")
        if not token:
            print("登录失败:", json.dumps(login, ensure_ascii=False)[:200])
            return 1
        headers = {"Authorization": f"Bearer {token}"}
        print("[login] ok")

        r = await client.post(f"{BASE}/nlq/archive", headers=headers,
                              json={"entity_type": "laboratory", "entity_id": 1})
        arch = r.json()
        data = arch.get("data") or {}
        print(f"[proxy archive] code={arch.get('code')} success={data.get('success')} cached={data.get('cached')}")
        if data.get("text"):
            print("  档案节选:", data["text"][:80].replace("\n", " "))
        if arch.get("code") != 200 or not data.get("success"):
            failures += 1

        r = await client.post(f"{BASE}/nlq/narrative", headers=headers, json={"kind": "teaching_summary"})
        narr = r.json()
        ndata = narr.get("data") or {}
        print(f"[proxy narrative] code={narr.get('code')} success={ndata.get('success')} scope={ndata.get('scope')}")
        print("  numbers:", json.dumps(ndata.get("numbers") or {}, ensure_ascii=False))
        if narr.get("code") != 200 or not ndata.get("success"):
            failures += 1

    print("\nJava 代理链路:", "全部通过" if failures == 0 else f"{failures} 项失败")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
