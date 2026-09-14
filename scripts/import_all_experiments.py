"""
批量导入实验项目

流程: 登录 -> 拉取所有课程 -> 对每门课找到对应 xlsx -> POST /experiments/import
要求:
  - 课表已导入 (自动生成 course)
  - 当前学期激活
  - admin 已登录 (或 TEACHER 权限足够)

用法: python scripts/import_all_experiments.py
"""
import os
import sys
import json
import glob
import urllib.request
import urllib.parse
import mimetypes

BASE = "http://127.0.0.1:8080/api/v1"
DATA_DIR = r"E:\projects\AI LTPMS\outputs\test_data"
USERNAME = "admin"
PASSWORD = "admin123"


def http_login(u, p):
    data = json.dumps({"username": u, "password": p}).encode()
    req = urllib.request.Request(
        f"{BASE}/auth/login", data=data,
        headers={"Content-Type": "application/json"}, method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=15).read())["data"]["token"]


def http_get(path, token):
    req = urllib.request.Request(f"{BASE}{path}",
                                 headers={"Authorization": "Bearer " + token})
    return json.loads(urllib.request.urlopen(req, timeout=15).read())["data"]


def upload_xlsx(url, file_path, token, extra_form=None):
    """multipart/form-data 上传 xlsx，可附 courseId 等表单字段"""
    boundary = "----LTPMSBoundary123"
    body = []
    if extra_form:
        for k, v in extra_form.items():
            body.append(f"--{boundary}".encode())
            body.append(f'Content-Disposition: form-data; name="{k}"'.encode())
            body.append(b"")
            body.append(str(v).encode())
    fname = os.path.basename(file_path)
    ctype = mimetypes.guess_type(fname)[0] or "application/octet-stream"
    with open(file_path, "rb") as f:
        content = f.read()
    body.append(f"--{boundary}".encode())
    body.append(f'Content-Disposition: form-data; name="file"; filename="{fname}"'.encode())
    body.append(f"Content-Type: {ctype}".encode())
    body.append(b"")
    body.append(content)
    body.append(f"--{boundary}--".encode())
    body.append(b"")
    payload = b"\r\n".join(body)
    req = urllib.request.Request(
        url, data=payload, method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(len(payload)),
        })
    return json.loads(urllib.request.urlopen(req, timeout=30).read())


def main():
    token = http_login(USERNAME, PASSWORD)
    print(f"已登录: {USERNAME}")

    # 1. 拉取所有课程（按学期过滤=当前学期）
    sem = http_get("/semesters", token)
    current = next((s for s in sem if s.get("status") == "ACTIVE"), sem[0] if sem else None)
    if not current:
        print("找不到当前学期，请检查 semester 表"); sys.exit(1)
    sid = current["id"]
    print(f"当前学期: {current['semesterName']} (id={sid})")

    page = 1
    courses = []
    while True:
        batch = http_get(f"/courses?semesterId={sid}&page={page}&size=200", token)
        records = batch.get("records", [])
        courses.extend(records)
        if page * batch.get("size", 200) >= batch.get("total", 0):
            break
        page += 1
    print(f"本学期课程数: {len(courses)}")

    # 2. 构建 code -> courseId 映射
    code_to_id = {c["courseCode"]: c["id"] for c in courses}

    # 3. 匹配 xlsx
    xlsx_files = sorted(glob.glob(os.path.join(DATA_DIR, "data_experiment_*.xlsx")))
    print(f"实验 xlsx 数量: {len(xlsx_files)}")

    # 4. 逐份上传
    ok, fail, skip = 0, 0, 0
    for xpath in xlsx_files:
        # 解析文件名: data_experiment_CS101.xlsx -> CS101
        code = os.path.basename(xpath).replace("data_experiment_", "").replace(".xlsx", "")
        course_id = code_to_id.get(code)
        if not course_id:
            print(f"  [SKIP] {code}: 课程不存在")
            skip += 1
            continue
        try:
            r = upload_xlsx(
                f"{BASE}/experiments/import", xpath, token,
                extra_form={"courseId": course_id},
            )
            if r.get("code") == 200:
                success = r["data"].get("successCount", 0)
                error = r["data"].get("errorCount", 0)
                print(f"  [OK]   {code}: 成功 {success} 失败 {error}")
                ok += 1 if error == 0 else 0
                fail += 1 if error > 0 else 0
            else:
                print(f"  [FAIL] {code}: {r.get('message')}")
                fail += 1
        except Exception as e:
            print(f"  [ERR]  {code}: {e}")
            fail += 1

    print(f"\n=== 完成: 成功 {ok} 失败 {fail} 跳过 {skip} ===")


if __name__ == "__main__":
    main()
