"""
回填 schedule_time 表
- 从 schedule.raw_schedule_text 解析周次/节次
- 使用 raw_schedule_text 字段做正则解析
- 调用: python scripts/parse_schedule_time.py
"""
import re
import sys
import pymysql

DB = dict(host="127.0.0.1", port=3307, user="ltpms", password="ltpms",
          database="ltpms_db", charset="utf8mb4")

DAY_MAP = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "日": 7}


def parse(raw):
    """解析 "周一第1-4节(1-16周)" -> (day, start, end, week_range, week_type)"""
    m_day = re.search(r"周([一二三四五六日])", raw)
    day = DAY_MAP.get(m_day.group(1), 1) if m_day else 1
    m_sec = re.search(r"第(\d+)-(\d+)节", raw)
    start = int(m_sec.group(1)) if m_sec else 1
    end = int(m_sec.group(2)) if m_sec else 2
    m_week = re.search(r"\(([^)]+周)\)", raw)
    week_text = m_week.group(1).replace("周", "") if m_week else "1-16"
    week_type = "ALL"
    if "单周" in week_text:
        week_type, week_text = "ODD", week_text.replace("单周", "")
    elif "双周" in week_text:
        week_type, week_text = "EVEN", week_text.replace("双周", "")
    return day, start, end, week_text, week_type


def main():
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, raw_schedule_text FROM schedule WHERE raw_schedule_text IS NOT NULL")
            rows = cur.fetchall()
            print(f"待解析 schedule 条数: {len(rows)}")
            cur.execute("TRUNCATE TABLE schedule_time")
            inserted = 0
            for sid, raw in rows:
                day, start, end, week, wtype = parse(raw)
                cur.execute(
                    "INSERT INTO schedule_time (schedule_id, day_of_week, start_section, end_section, week_range, week_type) "
                    "VALUES (%s,%s,%s,%s,%s,%s)",
                    (sid, day, start, end, week, wtype),
                )
                inserted += 1
            conn.commit()
            print(f"回填完成: schedule_time 新增 {inserted} 行")
    finally:
        conn.close()


if __name__ == "__main__":
    try:
        main()
    except pymysql.err.OperationalError as e:
        print("数据库连接失败:", e, file=sys.stderr)
        print("请先 pip install pymysql 或检查 MySQL 是否启动", file=sys.stderr)
        sys.exit(1)
