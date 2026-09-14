"""
LTPMS 实验室开放管理种子数据（2026-2027 校历 + 开放计划 + 例外日）

用途：为「学期与实验室开放管理」功能提供可复现的验收数据（不进 init.sql）。
流程：
  1. 清理 2025-2026-2 / 2026-2027-1 两个学期的开放时段、例外日与节假日；
  2. 回填两学期 week1_start_date（第 1 周起始日）；
  3. 写入节假日：国庆 2026-10-01~07、元旦 2027-01-01；
  4. 写入开放计划：批量 10 间（工作日 1-8 节 / 1-16 周 / 全校）+ 微调
     （36-401 加周二 9-10 节、38-201 晚间与单周示例、37-101 周末、38-301 教师专用）；
  5. 故意留 2 处「有课但未开放」矛盾（36-201 缺周一、37-102 时段错开），供 S3/S5 验收；
  6. 上学期 2025-2026-2 留 2 间实验室小数据，供「从上学期复制」剧本（S2）。

调用：python scripts/seed_lab_openings.py
"""
import sys

import pymysql

DB = dict(host="127.0.0.1", port=3307, user="ltpms", password="ltpms",
          database="ltpms_db", charset="utf8mb4")

CURRENT = "2026-2027-1"
PREVIOUS = "2025-2026-2"

# 批量设置的 10 间实验室：工作日 1-8 节 / 1-16 周 / 全校
BATCH_LABS = ["36-101", "36-102", "36-401", "36-402", "37-101",
              "37-201", "37-202", "38-101", "38-105", "38-201"]

WORKDAYS = [1, 2, 3, 4, 5]

# (lab_code, day_of_week, start_section, end_section, week_range, week_type, audience)
EXTRA_SLOTS = [
    # S2：单间微调 —— 36-401 加周二 9-10 节
    ("36-401", 2, 9, 10, "1-16", "ALL", "全校"),
    # 晚间开放（本院）
    ("38-201", 1, 9, 10, "1-16", "ALL", "本院"),
    ("38-201", 2, 9, 10, "1-16", "ALL", "本院"),
    ("38-201", 3, 9, 10, "1-16", "ALL", "本院"),
    ("38-201", 4, 9, 10, "1-16", "ALL", "本院"),
    ("38-201", 5, 9, 10, "1-16", "ALL", "本院"),
    # 周末开放（指定专业）
    ("37-101", 6, 1, 8, "1-16", "ALL", "指定专业"),
    ("37-101", 7, 1, 8, "1-16", "ALL", "指定专业"),
    # 教师专用
    ("38-301", 1, 1, 4, "1-16", "ALL", "教师专用"),
    ("38-301", 2, 1, 4, "1-16", "ALL", "教师专用"),
    ("38-301", 3, 1, 4, "1-16", "ALL", "教师专用"),
    ("38-301", 4, 1, 4, "1-16", "ALL", "教师专用"),
    ("38-301", 5, 1, 4, "1-16", "ALL", "教师专用"),
    # 单周开放示例
    ("38-201", 6, 1, 4, "1-16", "ODD", "全校"),
]

# 故意矛盾 A：36-201 有课「周一 3-4 节」，但计划只开放周二/周四 → 周一有课未开放
CONFLICT_A = [("36-201", 2, 1, 8, "1-16", "ALL", "全校"),
              ("36-201", 4, 1, 8, "1-16", "ALL", "全校")]

# 故意矛盾 B：37-102 有课「周三 5-7 节」，但只开放周三 1-4 节 → 时段时间错开
CONFLICT_B = [("37-102", 3, 1, 4, "1-16", "ALL", "本院")]

# 上学期小数据（供「从上学期复制」）
PREVIOUS_SLOTS = [("36-401", d, 1, 8, "1-16", "ALL", "全校") for d in WORKDAYS] + \
                 [("36-402", d, 1, 8, "1-16", "ALL", "全校") for d in WORKDAYS]

# 例外日（临时闭馆）
EXCEPTIONS = [
    ("36-401", "2026-09-30", "2026-09-30", "设备检修"),
    ("37-202", "2026-10-15", "2026-10-16", "考试占用"),
    ("38-105", "2026-11-20", "2026-11-20", "设备校准"),
]

HOLIDAYS = [("2026-10-01", "国庆节"), ("2026-10-02", "国庆节"), ("2026-10-03", "国庆节"),
            ("2026-10-04", "国庆节"), ("2026-10-05", "国庆节"), ("2026-10-06", "国庆节"),
            ("2026-10-07", "国庆节"), ("2027-01-01", "元旦")]


def semester_id(cur, name):
    cur.execute("SELECT id, start_date, end_date FROM semester WHERE semester_name = %s", (name,))
    row = cur.fetchone()
    if not row:
        raise SystemExit(f"学期 {name} 不存在，请先初始化学期数据")
    return row


def insert_slots(cur, semester, slots, label):
    cur.executemany(
        "INSERT INTO lab_open_time (lab_code, semester_id, day_of_week, start_section, end_section,"
        " week_range, week_type, audience, enabled, remark) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 1, %s)",
        [(lab, semester, day, start, end, weeks, wtype, audience, f"种子数据：{label}")
         for lab, day, start, end, weeks, wtype, audience in slots],
    )
    return len(slots)


def main():
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur_id, cur_start, cur_end = semester_id(cur, CURRENT)
            prev_id, prev_start, prev_end = semester_id(cur, PREVIOUS)

            # 1) 清理两个学期的开放数据（脚本可重复执行）
            cur.execute("DELETE FROM lab_open_time WHERE semester_id IN (%s, %s)", (cur_id, prev_id))
            cur.execute("DELETE FROM lab_open_exception WHERE semester_id IN (%s, %s)", (cur_id, prev_id))
            cur.execute(
                "DELETE FROM holiday WHERE holiday_date BETWEEN %s AND %s OR holiday_date BETWEEN %s AND %s",
                (prev_start, prev_end, cur_start, cur_end),
            )
            print(f"清理：lab_open_time {cur.rowcount} 行")

            # 2) 回填周历基准（第 1 周起始日 = 开学日）
            cur.execute("UPDATE semester SET week1_start_date = start_date WHERE id IN (%s, %s)", (cur_id, prev_id))

            # 3) 节假日
            cur.executemany("INSERT INTO holiday (holiday_date, name) VALUES (%s, %s)", HOLIDAYS)

            # 4) 开放计划
            total = 0
            total += insert_slots(cur, cur_id, [(lab, d, 1, 8, "1-16", "ALL", "全校") for lab in BATCH_LABS for d in WORKDAYS], "批量工作日")
            total += insert_slots(cur, cur_id, EXTRA_SLOTS, "单间微调")
            total += insert_slots(cur, cur_id, CONFLICT_A, "矛盾示例A")
            total += insert_slots(cur, cur_id, CONFLICT_B, "矛盾示例B")
            prev_total = insert_slots(cur, prev_id, PREVIOUS_SLOTS, "上学期")

            # 5) 例外日
            cur.executemany(
                "INSERT INTO lab_open_exception (lab_code, semester_id, start_date, end_date, reason)"
                " VALUES (%s, %s, %s, %s, %s)",
                [(lab, cur_id, start, end, reason) for lab, start, end, reason in EXCEPTIONS],
            )

            conn.commit()

            print(f"学期 {CURRENT}（id={cur_id}）：开放时段 {total} 行，例外日 {len(EXCEPTIONS)} 条，节假日 {len(HOLIDAYS)} 天")
            print(f"学期 {PREVIOUS}（id={prev_id}）：开放时段 {prev_total} 行（供复制剧本）")
            print("种子数据写入完成。预期：13 间实验室已配置；2 处「有课但未开放」矛盾（36-201、37-102）。")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
