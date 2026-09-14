"""LTPMS 学期与开放管理 端到端验收探针（S1~S6 + 数据智能回归）

前置：MySQL 3307 / 后端 8080 / NLQ 8001 已启动，且已执行 scripts/seed_lab_openings.py。
调用：python outputs/acceptance_open_check.py
"""
import json
import sys
import urllib.error
import urllib.request

API = "http://127.0.0.1:8080/api/v1"
PASS, FAIL = [], []


def call(method, path, token=None, body=None, timeout=60, expect_http=200):
    url = API + path
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            return resp.status, payload
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append(name)
    print(f"[{'PASS' if ok else 'FAIL'}] {name}{(' | ' + str(detail)) if detail else ''}")


def login(username, password):
    _, r = call("POST", "/auth/login", body={"username": username, "password": password})
    assert r["code"] == 200, r
    return r["data"]["token"]


admin = login("admin", "admin123")
teacher = login("T003", "Ltpms@T003")
print("登录 OK：admin + T003\n")

# ---------- S1 学年卡片与校历 ----------
_, r = call("GET", "/semesters/2/calendar", admin)
cal = r["data"]
check("S1 周历已配置且基准=2026-09-07", cal["configured"] and cal["week1StartDate"] == "2026-09-07", cal["week1StartDate"])
check("S1 总周数=20", cal["totalWeeks"] == 20, cal["totalWeeks"])
w4 = next((w for w in cal["weeks"] if w["week"] == 4), None)
check("S1 第4周=09-28~10-04 且标记国庆节",
      w4 and w4["startDate"] == "2026-09-28" and w4["endDate"] == "2026-10-04" and "国庆节" in w4["holidayNames"],
      w4)

_, r = call("GET", "/holidays?semesterId=2", admin)
check("S1 学期 2 节假日 8 天", len(r["data"]) == 8, len(r["data"]))

# ---------- S3 占用视图（种子态） ----------
_, r = call("GET", "/lab-openings/overview?semesterId=2", admin)
labs = {item["labCode"]: item for item in r["data"]["labs"]}
check("S3 总览覆盖全部 39 间实验室", len(r["data"]["labs"]) == 39, len(r["data"]["labs"]))
planned = [code for code, item in labs.items() if item["planned"]]
check("S3 已配置 13 间 / 未开放 26 间", len(planned) == 13 and 39 - len(planned) == 26, len(planned))
check("S3 36-201 矛盾 1 处", labs["36-201"]["conflictCount"] == 1, labs["36-201"]["conflictCount"])
check("S3 37-102 矛盾 1 处", labs["37-102"]["conflictCount"] == 1, labs["37-102"]["conflictCount"])
check("S3 36-401 无矛盾", labs["36-401"]["conflictCount"] == 0, labs["36-401"]["conflictCount"])
conflict_total = sum(item["conflictCount"] for item in labs.values())
check("S3 矛盾合计 2 处", conflict_total == 2, conflict_total)

_, r = call("GET", "/lab-openings/grid?semesterId=2&labCode=36-401&week=4", admin)
grid = r["data"]
cells = {(c["dayOfWeek"], c["section"]): c["state"] for c in grid["cells"]}
check("S3 网格 84 格", len(grid["cells"]) == 84, len(grid["cells"]))
check("S3 周一 1-8 有课", all(cells[(1, s)] == "OCCUPIED" for s in range(1, 9)))
check("S3 周二 1-10 开放", all(cells[(2, s)] == "OPEN" for s in range(1, 11)))
check("S3 周三闭馆（09-30 例外）", all(cells[(3, s)] == "CLOSED" for s in range(1, 13)))
check("S3 周四~周日闭馆（国庆）", all(cells[(d, s)] == "CLOSED" for d in range(4, 8) for s in range(1, 13)))
closed = {item["date"]: item["type"] for item in grid["closedDates"]}
check("S3 闭馆日 = 09-30 例外 + 10-01~04 节假日",
      closed.get("2026-09-30") == "EXCEPTION" and all(closed.get(f"2026-10-0{i}") == "HOLIDAY" for i in range(1, 5)),
      closed)

# ---------- S4 利用率口径（手工推算 vs 接口） ----------
expect = {
    # code: (open, occupied, rate, 说明)
    "36-401": (672, 128, 19.0, "5×8×16 + 周二9-10(2×16)=672；周一8节×16=128"),
    "36-201": (256, 32, 12.5, "周二/周四 8节×16×2=256；周一3-4节×16=32"),
    "37-102": (64, 48, 75.0, "周三1-4节×16=64；周三5-7节×16=48"),
    "38-201": (832, 0, 0.0, "工作日(40×16)+晚间(10×16)+单周周六(4×8)=832"),
    "38-301": (320, 0, 0.0, "1-4节×5天×16=320（教师专用）"),
    "37-101": (896, 0, 0.0, "工作日(40×16)+周末(16×16)=896"),
}
for code, (open_, occ, rate, why) in expect.items():
    item = labs[code]
    ok = item["openSlotHours"] == open_ and item["occupiedSlotHours"] == occ and item["rate"] == rate
    check(f"S4 {code} 开放{open_}/占用{occ}/率{rate}", ok,
          f"实际 {item['openSlotHours']}/{item['occupiedSlotHours']}/{item['rate']}（{why}）")
check("S4 未配置实验室灰态（rate=null）", labs["35-509"]["rate"] is None and labs["35-509"]["planned"] is False,
      labs["35-509"])

_, r = call("GET", "/insight/utilization", admin)
util = {item["labCode"]: item for item in r["data"]}
check("S4 热力图含新字段并与总览同源",
      util["36-401"]["rate"] == 19.0 and util["36-401"]["planned"] and util["35-509"]["rate"] is None,
      util["36-401"])

# ---------- S5 异常预警 ----------
_, r = call("GET", "/insight/anomalies", admin)
not_open = [item for item in r["data"] if item["type"] == "NOT_OPEN"]
targets = sorted(item["target"] for item in not_open)
check("S5 NOT_OPEN 预警 2 条且与标红数据一致", targets == ["36-201", "37-102"], targets)

# ---------- 回归：数据智能四接口 + /insight/all ----------
for path, key in (("/insight/readiness", None), ("/insight/utilization", None), ("/insight/anomalies", None), ("/insight/conflicts", None)):
    status, payload = call("GET", path, admin)
    check(f"回归 {path} 200", status == 200 and payload["code"] == 200, status)

status, payload = call("GET", "/insight/all", admin)
keys = sorted((payload.get("data") or {}).keys())
check("回归 /insight/all 四键齐全", status == 200 and {"readiness", "utilization", "anomalies", "conflicts"} <= set(keys), keys)

# ---------- S6 教师侧（只读检查，先于写演练，保证种子态一致） ----------
_, r = call("GET", "/lab-openings/today", teacher)
today = r["data"]
check("S6 教师可见今日开放（2026-09-14 周一 / 第 2 周）",
      today.get("inSemester") is True and today.get("weekNo") == 2 and len(today["labs"]) == 11,
      f"weekNo={today.get('weekNo')} labs={len(today['labs'])}")
today_map = {item["labCode"]: item for item in today["labs"]}
check("S6 今日节次合并正确（相邻合并 + 开放对象聚合）",
      today_map.get("38-201", {}).get("sectionText") == "1-10节"
      and today_map.get("38-201", {}).get("audience") == "全校、本院"
      and today_map.get("38-301", {}).get("sectionText") == "1-4节"
      and today_map.get("38-301", {}).get("audience") == "教师专用",
      {k: (v["sectionText"], v["audience"]) for k, v in today_map.items()})

status, r = call("POST", "/nlq/agent", teacher, {"question": "今天有哪些实验室开放？只需列出实验室编号和开放节次"}, timeout=180)
answer = (r.get("data") or {}).get("answer") if isinstance(r, dict) else None
check("S6 教师 NLQ 开放类问题可答", status == 200 and isinstance(answer, str) and len(answer) > 0,
      (answer or str(r))[:200])

status, r = call("GET", "/lab-openings?semesterId=2", teacher)
check("S6 教师只读可查开放时段", status == 200 and len(r["data"]) == 67, len(r["data"]) if status == 200 else status)

# ---------- S2 开放计划维护（真实 API 演练 + 清理；写操作会覆盖 36-401/402，跑完需重跑种子） ----------
status, r = call("POST", "/lab-openings", admin, {
    "labCode": "35-509", "semesterId": 2, "dayOfWeek": 6, "startSection": 1, "endSection": 4,
    "weekRange": "1-16", "weekType": "ALL", "audience": "全校", "enabled": 1, "remark": "验收演练",
})
check("S2 单条新增 200", status == 200 and r["code"] == 200, r)
_, r = call("GET", "/lab-openings?semesterId=2&labCode=35-509", admin)
created = r["data"]
check("S2 单条新增可见", len(created) == 1, created)
if created:
    _, r2 = call("PUT", f"/lab-openings/{created[0]['id']}", admin, {
        "labCode": "35-509", "semesterId": 2, "dayOfWeek": 6, "startSection": 1, "endSection": 6,
        "weekRange": "1-16", "weekType": "ALL", "audience": "全校", "enabled": 1, "remark": "验收演练-改",
    })
    _, r3 = call("GET", "/lab-openings?semesterId=2&labCode=35-509", admin)
    check("S2 单条修改生效", r3["data"] and r3["data"][0]["endSection"] == 6, r3["data"])
    call("DELETE", f"/lab-openings/{created[0]['id']}", admin)
_, r = call("GET", "/lab-openings?semesterId=2&labCode=35-509", admin)
check("S2 单条删除后清理干净", len(r["data"]) == 0, r["data"])

status, r = call("POST", "/lab-openings/batch", admin, {
    "semesterId": 2, "labCodes": ["36-301"], "audience": "本院", "remark": "验收批量",
    "slots": [{"dayOfWeek": 5, "startSection": 5, "endSection": 8, "weekRange": "1-16", "weekType": "ALL"}],
})
_, r2 = call("GET", "/lab-openings?semesterId=2&labCode=36-301", admin)
batch_rows = r2["data"]
check("S2 批量覆盖写入 1 条", status == 200 and r.get("data") == 1 and len(batch_rows) == 1, r.get("data"))
for row in batch_rows:
    call("DELETE", f"/lab-openings/{row['id']}", admin)

status, r1 = call("POST", "/lab-openings/copy", admin, {"fromSemesterId": 1, "toSemesterId": 2})
status, r2 = call("POST", "/lab-openings/copy", admin, {"fromSemesterId": 1, "toSemesterId": 2})
_, r3 = call("GET", "/lab-openings?semesterId=2&labCode=36-401", admin)
_, r4 = call("GET", "/lab-openings?semesterId=2&labCode=36-402", admin)
check("S2 从上学期复制 2 间（两次幂等）",
      r1.get("data") == 2 and r2.get("data") == 2 and len(r3["data"]) == 5 and len(r4["data"]) == 5,
      f"copy1={r1.get('data')} copy2={r2.get('data')} rows401={len(r3['data'])} rows402={len(r4['data'])}")

status, r = call("POST", "/lab-open-exceptions", admin, {
    "labCode": "36-101", "semesterId": 2, "startDate": "2026-12-01", "endDate": "2026-12-02", "reason": "验收演练",
})
_, r2 = call("GET", "/lab-open-exceptions?semesterId=2", admin)
new_exc = [item for item in r2["data"] if item["reason"] == "验收演练"]
check("S2 例外日新增可见", status == 200 and len(new_exc) == 1, len(new_exc))
for item in new_exc:
    call("DELETE", f"/lab-open-exceptions/{item['id']}", admin)

status, r = call("POST", "/holidays", admin, {"startDate": "2026-12-25", "endDate": "2026-12-25", "name": "验收演练"})
_, r2 = call("GET", "/holidays?semesterId=2", admin)
tmp = [item for item in r2["data"] if item["name"] == "验收演练"]
check("S2 节假日新增可见（返回 1 天）", status == 200 and r.get("data") == 1 and len(tmp) == 1, r.get("data"))
for item in tmp:
    call("DELETE", f"/holidays/{item['id']}", admin)

status, r = call("POST", "/holidays", admin, {"startDate": "2026-10-05", "endDate": "2026-10-06", "name": "重复区间"})
dup_rejected = status == 400 or (isinstance(r, dict) and r.get("code") == 400)
check("S2 区间全重复 → 拒绝并提示", dup_rejected, r if isinstance(r, str) else r.get("message"))
rows = [item for item in call("GET", "/holidays?semesterId=2", admin)[1]["data"] if item["name"] == "重复区间"]
for item in rows:
    call("DELETE", f"/holidays/{item['id']}", admin)

# ---------- 汇总 ----------
print(f"\n===== 验收结果：PASS {len(PASS)} / FAIL {len(FAIL)} =====")
for name in FAIL:
    print("FAILED:", name)
sys.exit(1 if FAIL else 0)
