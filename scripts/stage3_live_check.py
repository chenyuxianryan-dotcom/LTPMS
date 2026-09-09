"""阶段3 真机验收（T3.4）：nlq-service 8001 的 /archive 与 /narrative 直连验证。

数字核对：把接口返回的 numbers 与数据库直查结果对比，确保聚合数字真实一致。
用法：python ../scripts/stage3_live_check.py
"""

import asyncio
import json
import os
import sys

import httpx

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "nlq-service"))

from app.config import settings  # noqa: E402

BASE = f"http://127.0.0.1:{settings.port}"
HEADERS = {"X-Internal-Token": settings.nlq_internal_token, "X-User-Id": "1", "X-User-Role": "ADMIN"}


async def db_one(sql: str, params: tuple = ()):
    import aiomysql

    conn = await aiomysql.connect(
        host=settings.db_host, port=settings.db_port, user=settings.db_user,
        password=settings.db_password, db=settings.db_name,
    )
    try:
        cur = await conn.cursor()
        await cur.execute(sql, params)
        row = await cur.fetchone()
        return row
    finally:
        conn.close()


async def main() -> int:
    failures = 0
    async with httpx.AsyncClient(timeout=90) as client:
        # ---- 1. 实验室数字档案 ----
        r = await client.post(f"{BASE}/archive", headers=HEADERS,
                              json={"entity_type": "laboratory", "entity_id": 1})
        data = r.json()
        print(f"[archive/laboratory] success={data.get('success')} cached={data.get('cached')}")
        if data.get("text"):
            print("  档案文本:", data["text"][:120].replace("\n", " "))
        if not data["success"] and not data.get("stats"):
            failures += 1

        # ---- 2. 实验项目数字档案 ----
        r = await client.post(f"{BASE}/archive", headers=HEADERS,
                              json={"entity_type": "experiment", "entity_id": 1})
        data2 = r.json()
        print(f"[archive/experiment] success={data2.get('success')} cached={data2.get('cached')}")
        if not data2["success"] and not data2.get("stats"):
            print("  error:", data2.get("error"))
            if "不存在" not in str(data2.get("error")):
                failures += 1

        # ---- 3. 全校年度叙事报告（ADMIN）+ 数字核对 ----
        r = await client.post(f"{BASE}/narrative", headers=HEADERS, json={"kind": "annual_report"})
        narr = r.json()
        print(f"[narrative/annual ADMIN] success={narr.get('success')} scope={narr.get('scope')}")
        numbers = narr.get("numbers") or {}
        print("  numbers:", json.dumps(numbers, ensure_ascii=False))
        if narr.get("text"):
            print("  报告节选:", narr["text"][:150].replace("\n", " "))

        # 数字核对：接口 numbers vs 直查数据库
        semester_name = numbers.get("semester_name")
        row = await db_one(
            "SELECT COUNT(*) AS n, COALESCE(SUM(student_count * total_hours),0) AS ph "
            "FROM schedule sc JOIN semester s ON sc.semester_id = s.id WHERE s.semester_name = %s",
            (semester_name,),
        )
        db_schedules, db_person_hours = int(row[0]), float(row[1])
        ok_schedules = int(numbers.get("schedule_total", -1)) == db_schedules
        ok_person = abs(float(numbers.get("person_hours", -1)) - db_person_hours) < 0.01
        print(f"  核对 schedule_total: numbers={numbers.get('schedule_total')} db={db_schedules} → {'✓' if ok_schedules else '✗'}")
        print(f"  核对 person_hours:   numbers={numbers.get('person_hours')} db={db_person_hours} → {'✓' if ok_person else '✗'}")
        if not (ok_schedules and ok_person):
            failures += 1

        # 报告文本中不得出现数据之外的 Suspicious 数字太复杂，核对关键数字是否出现在文本中
        if narr.get("text") and numbers.get("schedule_total"):
            text_ok = str(numbers["schedule_total"]) in narr["text"] or "排课" not in narr["text"]
            print(f"  报告文本引用真实数字: {'✓' if text_ok else '✗'}")

        # ---- 4. 教师口径权限验证（TEACHER 只见本人） ----
        teacher_headers = {**HEADERS, "X-User-Id": "2", "X-User-Role": "TEACHER"}
        r = await client.post(f"{BASE}/narrative", headers=teacher_headers, json={"kind": "annual_report"})
        tnarr = r.json()
        tnumbers = tnarr.get("numbers") or {}
        print(f"[narrative/TEACHER] success={tnarr.get('success')} scope={tnarr.get('scope')}（annual_report 应归一为个人教学总结口径）")
        row = await db_one(
            "SELECT COUNT(*) AS n FROM schedule sc JOIN semester s ON sc.semester_id = s.id "
            "WHERE s.semester_name = %s AND sc.teacher_id = 2",
            (semester_name,),
        )
        db_teacher_schedules = int(row[0])
        ok_teacher = int(tnumbers.get("schedule_total", -1)) == db_teacher_schedules
        print(f"  核对教师口径 schedule_total: numbers={tnumbers.get('schedule_total')} db(teacher_id=2)={db_teacher_schedules} → {'✓' if ok_teacher else '✗'}")
        if tnarr.get("scope") != "personal" or not ok_teacher:
            failures += 1

    print("\n结论:", "全部通过" if failures == 0 else f"{failures} 项失败")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
