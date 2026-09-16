"""LTPMS 教师课程管理优化 批次1 端到端验收探针（T1~T9）

前置：MySQL 3307 / 后端 8080 已启动（新代码），已执行 upgrade_20260916_course_revision.sql。
调用：python outputs/acceptance_course_mgmt_b1.py

覆盖：导入收紧TEACHER / 内联时间槽 / mine周过滤 / 编辑落修订 / 等值跳过 /
      冲突预览与42004 / 确认覆盖与管理员通知 / 管理员改课通知教师 /
      上传进度与催办 / 课程聚合编辑与修订标记
"""
import json
import sys

import requests

API = "http://127.0.0.1:8080/api/v1"
PASS, FAIL = [], []


def call(method, path, token=None, body=None, files=None, params=None, timeout=60):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    if files is None:
        headers["Content-Type"] = "application/json; charset=utf-8"
    try:
        resp = requests.request(method, API + path, headers=headers, params=params,
                                json=body if files is None else None, files=files, timeout=timeout)
        return resp.status_code, resp.json()
    except Exception as exc:  # noqa: BLE001
        return -1, {"raw": str(exc)}


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

SEM = 2
ROWS = [
    {"courseCode": "DB202", "courseName": "数据库原理实验", "totalHours": 32,
     "teacherNo": "", "labCode": "37-202", "classNames": "2023级软件工程1班",
     "studentCount": 42, "rawScheduleText": "周四第1-4节(1-16周)"},
    {"courseCode": "DB202", "courseName": "数据库原理实验", "totalHours": 32,
     "teacherNo": "T003", "labCode": "36-401", "classNames": "2023级软件工程2班",
     "studentCount": 40, "rawScheduleText": "周五第3-4节(2-8周)"},
]

# ---------- T1 导入接口收紧 TEACHER + 内联生成时间槽 ----------
_, r = call("POST", "/schedules/import/rows", admin, body={"rows": ROWS}, params={"semesterId": SEM})
check("T1a 管理员导入被拒（code=403）", r.get("code") == 403, r)

_, r = call("POST", "/schedules/import/rows", teacher, body={"rows": ROWS}, params={"semesterId": SEM})
check("T1b 教师结构化导入成功 2 行", r.get("code") == 200 and r["data"]["successCount"] == 2, r)

_, r = call("GET", "/schedules/mine", teacher, params={"semesterId": SEM})
items = r["data"]
check("T1c 教师课表 2 条且带时间槽", len(items) == 2 and all(it["slots"] for it in items), len(items))
slot_map = {it["rawScheduleText"]: it["slots"][0] for it in items}
sA = slot_map.get("周四第1-4节(1-16周)")
check("T1d 时间槽解析正确（周四 1-4 节 1-16 全周，16 周）",
      sA and sA["dayOfWeek"] == 4 and sA["startSection"] == 1 and sA["endSection"] == 4
      and sA["weekRange"] == "1-16" and sA["weekType"] == "ALL" and len(sA["weeks"]) == 16, sA)
check("T1e 实验室名称联查（37-202 → 大数据实验室）",
      any(it["labName"] == "大数据实验室" for it in items), [it["labName"] for it in items])

# ---------- T2 mine 周过滤与管理员视角 ----------
_, r = call("GET", "/schedules/mine", teacher, params={"semesterId": SEM, "week": 1})
check("T2a week=1 仅 1 条（2-8 周课程被过滤）", r["code"] == 200 and len(r["data"]) == 1, len(r["data"]))
_, r = call("GET", "/schedules/mine", teacher, params={"semesterId": SEM, "week": 3})
check("T2b week=3 共 2 条", r["code"] == 200 and len(r["data"]) == 2, len(r["data"]))
_, r = call("GET", "/schedules/mine", admin, params={"semesterId": SEM, "teacherId": 4})
check("T2c 管理员指定教师可查", r["code"] == 200 and len(r["data"]) == 2, r.get("code"))
_, r = call("GET", "/schedules/mine", admin, params={"semesterId": SEM})
check("T2d 管理员未指定教师被拒（code=400）", r.get("code") == 400, r)

row_a = next(it for it in items if it["rawScheduleText"] == "周四第1-4节(1-16周)")
row_b = next(it for it in items if it["rawScheduleText"] == "周五第3-4节(2-8周)")

# ---------- T3 编辑落修订 + 等值跳过 ----------
_, r = call("PUT", f"/schedules/{row_a['id']}", teacher, body={"labCode": "36-401"})
check("T3a 教师编辑本人课表成功", r.get("code") == 200, r)
_, r = call("GET", f"/schedules/{row_a['id']}/revisions", teacher)
revs = r["data"]
check("T3b 修订明细 1 条（labCode 37-202→36-401，TEACHER，王强）",
      len(revs) == 1 and revs[0]["fieldName"] == "labCode" and revs[0]["oldValue"] == "37-202"
      and revs[0]["newValue"] == "36-401" and revs[0]["sourceRole"] == "TEACHER"
      and revs[0]["operatorName"] == "王强", revs)
_, r = call("PUT", f"/schedules/{row_a['id']}", teacher, body={"labCode": "36-401"})
_, r = call("GET", f"/schedules/{row_a['id']}/revisions", teacher)
check("T3c 等值提交不落新修订（仍 1 条）", len(r["data"]) == 1, len(r["data"]))
_, r = call("PUT", f"/schedules/{row_a['id']}", teacher, body={"studentCount": 50})
_, r = call("GET", "/schedules/revisions/flags", teacher,
            params={"ids": f"{row_a['id']},{row_b['id']}"})
check("T3d 修订标记：row_a=2 条、row_b 不在结果", r["data"].get(str(row_a["id"])) == 2
      and str(row_b["id"]) not in r["data"], r["data"])

# ---------- T4 冲突预览 + 42004 + 确认覆盖 ----------
_, r = call("POST", "/schedules/import/rows/preview", teacher, body={"rows": ROWS}, params={"semesterId": SEM})
pv = r["data"]
check("T4a 预览：2 行有效、冲突 1 条、冲突课程含数据库原理实验",
      pv["totalCount"] == 2 and pv["validCount"] == 2 and pv["revisionConflicts"] == 1
      and any("数据库原理" in c for c in pv["conflictCourses"]), pv)
_, r = call("POST", "/schedules/import/rows", teacher, body={"rows": ROWS}, params={"semesterId": SEM})
check("T4b 未确认覆盖返回 42004", r.get("code") == 42004, r)
_, r = call("POST", "/schedules/import/rows", teacher, body={"rows": ROWS},
            params={"semesterId": SEM, "confirmOverwrite": "true"})
check("T4c 确认后覆盖成功", r.get("code") == 200 and r["data"]["successCount"] == 2, r)

_, r = call("GET", "/notifications", admin, params={"isRead": "false"})
notifs = r["data"]["records"]
check("T4d 管理员收到 REVISION_OVERWRITTEN 通知（含王强）",
      any(n["type"] == "REVISION_OVERWRITTEN" and "王强" in (n["content"] or "") for n in notifs),
      [(n["type"], n["title"]) for n in notifs])

# ---------- T5 管理员修订教师课程 → 教师收通知 ----------
_, r = call("GET", "/schedules/mine", admin, params={"semesterId": SEM, "teacherId": 4})
items = r["data"]
new_a = next(it for it in items if it["rawScheduleText"] == "周四第1-4节(1-16周)")
_, r = call("PUT", f"/schedules/{new_a['id']}", admin, body={"classNames": "2023级软件工程1班（已审）"})
check("T5a 管理员编辑教师课表成功", r.get("code") == 200, r)
_, r = call("GET", f"/schedules/{new_a['id']}/revisions", teacher)
check("T5b 教师可见 ADMIN 修订明细（含已审）",
      len(r["data"]) == 1 and r["data"][0]["sourceRole"] == "ADMIN"
      and "已审" in (r["data"][0]["newValue"] or ""), r["data"])
_, r = call("GET", "/notifications", teacher, params={"isRead": "false"})
check("T5c 教师收到 SCHEDULE_REVISED 通知（含已审）",
      any(n["type"] == "SCHEDULE_REVISED" and "已审" in (n["content"] or "") for n in r["data"]["records"]),
      [(n["type"], n["title"]) for n in r["data"]["records"]])

# ---------- T6 上传进度（管理员） ----------
_, r = call("GET", "/schedules/upload-progress", admin, params={"semesterId": SEM})
prog = {p["teacherId"]: p for p in r["data"]}
t003 = prog.get(4)
check("T6 上传进度：T003 已上传 2 条带时间", t003 and t003["uploaded"] is True
      and t003["scheduleCount"] == 2 and t003["latestUploadAt"], t003)

# ---------- T7 催办通知 ----------
_, r = call("POST", "/schedules/upload-remind", admin, body={"semesterId": SEM, "teacherIds": [4]})
check("T7a 催办接口返回发送数 1", r.get("code") == 200 and r["data"] == 1, r)
_, r = call("GET", "/notifications", teacher, params={"isRead": "false"})
check("T7b 教师收到 UPLOAD_REMIND 通知",
      any(n["type"] == "UPLOAD_REMIND" for n in r["data"]["records"]),
      [(n["type"], n["title"]) for n in r["data"]["records"]])

# ---------- T8 课程聚合编辑 + 修订 ----------
_, r = call("GET", "/courses", teacher, params={"semesterId": SEM})
db202 = next(c for c in r["data"] if c["courseCode"] == "DB202")
_, r = call("PUT", f"/courses/{db202['id']}", teacher, body={"major": "软件工程,计算机科学与技术"})
check("T8a 教师编辑课程聚合行成功", r.get("code") == 200, r)
_, r = call("GET", f"/courses/{db202['id']}/revisions", teacher)
check("T8b 课程修订明细 1 条（major）",
      len(r["data"]) == 1 and r["data"][0]["fieldName"] == "major", r["data"])
_, r = call("GET", "/courses/revisions/flags", teacher, params={"ids": str(db202["id"])})
check("T8c 课程修订标记", r["data"].get(str(db202["id"])) == 1, r["data"])

# ---------- T9 文件导入预览（multipart 机制） ----------
with open("outputs/03_课表导入.xlsx", "rb") as fh:
    _, r = call("POST", "/schedules/import/preview", teacher,
                files={"file": ("03_课表导入.xlsx", fh, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
                params={"semesterId": SEM})
check("T9 xlsx 解析预览可用（totalCount>0）", r.get("code") == 200 and r["data"]["totalCount"] > 0,
      r.get("data") or r)

print(f"\n===== 批次1 验收：{len(PASS)} 通过 / {len(FAIL)} 失败 =====")
if FAIL:
    print("失败项：", *FAIL, sep="\n  - ")
    sys.exit(1)
