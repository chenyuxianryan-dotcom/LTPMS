"""
LTPMS 大量测试数据生成器

产出:
  outputs/test_data/data_lab_30.xlsx          - 实验室 30 条
  outputs/test_data/data_teacher_20.xlsx      - 教师 20 条
  outputs/test_data/data_schedule_120.xlsx    - 课表 120 条（导入后自动生成 ~50 门课程）
  outputs/test_data/data_experiment_*.xlsx    - 实验项目 按课程分裂为多份，每课 4-6 条
  outputs/test_data/data_seed.sql             - 历史学期 + query_history + notification
  scripts/parse_schedule_time.py              - 课表导入后跑，自动解析回填 schedule_time

数据一致性:
  - 教师工号 T001~T020 跨课表/实验引用
  - 实验室编号 36-101~38-502 跨课表引用
  - 课程代码(每门课唯一) 由课表导入后生成 course.id
"""
import os
import random
from openpyxl import Workbook

OUT_DIR = r"E:\projects\AI LTPMS\outputs\test_data"
SCRIPTS_DIR = r"E:\projects\AI LTPMS\scripts"
os.makedirs(OUT_DIR, exist_ok=True)
os.makedirs(SCRIPTS_DIR, exist_ok=True)

random.seed(42)

# 院系
DEPARTMENTS = [
    "计算机科学与技术学院", "软件学院", "网络空间安全学院",
    "电子信息工程学院", "物联网工程学院", "人工智能学院",
    "数据科学学院", "数学与统计学院", "物理学院", "化学学院",
]

# 姓氏 + 名字
SURNAMES = list("赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜")
GIVEN_NAMES = list("伟芳娜敏秀英华慧巧美娟静丽强磊军洋勇艳杰娟涛明超秀兰霞平刚桂英文俊倩冬梅春燕文博浩然子轩梓涵雨桐")

# 实验室：3 栋 × 5 层 × 2 间 = 30
BUILDINGS = ["36", "37", "38"]
LAB_TYPE_PARTS = ["计算机", "网络综合", "软件工程", "人工智能", "物联网",
                  "嵌入式", "信息安全", "数据科学", "电子电路", "通信原理"]
LAB_BUILDING_PREFIX = {"36": "基础", "37": "信息", "38": "创新"}

# 课程前缀 → 中文名 + 学科 dict_value
COURSE_PREFIXES = {
    "CS": ("计算机科学", "cs"),
    "SE": ("软件工程", "se"),
    "NET": ("网络工程", "network"),
    "AI": ("人工智能", "ai"),
    "DB": ("数据库", "data_science"),
    "IOT": ("物联网", "iot"),
    "ML": ("机器学习", "ai"),
    "OS": ("操作系统", "cs"),
    "DS": ("数据结构", "cs"),
    "ALG": ("算法设计", "cs"),
    "WEB": ("Web开发", "se"),
    "CRYPTO": ("密码学", "security"),
    "DSP": ("数字信号处理", "electronics"),
    "VLSI": ("集成电路设计", "electronics"),
    "EMB": ("嵌入式系统", "iot"),
    "DC": ("数据通信", "network"),
}

# 实验名称模板
EXP_TEMPLATES = [
    "基础环境搭建与配置",
    "核心算法实现与验证",
    "数据结构综合实践",
    "系统性能测试与优化",
    "综合项目设计与开发",
    "网络协议分析实验",
    "数据库设计与实现",
    "安全攻防演练",
]

# 字典 dict_value
CAT = ["basic", "comprehensive", "design", "innovation", "virtual"]
TYP = ["verification", "demonstration", "comprehensive", "design", "research"]
REQ = ["required", "elective"]
EXPERIMENTER = ["undergraduate", "graduate"]


def gen_teachers(n=20):
    return [
        [f"T{i:03d}", random.choice(SURNAMES) + random.choice(GIVEN_NAMES),
         random.choice(DEPARTMENTS), f"13{random.randint(100000000, 999999999)}"]
        for i in range(1, n + 1)
    ]


def gen_labs():
    managers = ["张志强", "李建国", "王德明", "陈卫华", "赵晓东",
                "刘海燕", "黄文涛", "周国梁", "杨春生", "吴国栋"]
    labs = []
    for b in BUILDINGS:
        for f in range(1, 6):
            for r in (1, 2):
                code = f"{b}-{f}{r:02d}"
                idx = len(labs) % len(LAB_TYPE_PARTS)
                name = f"{LAB_BUILDING_PREFIX[b]}{LAB_TYPE_PARTS[idx]}实验室"
                labs.append([code, name, random.choice(managers), random.randint(20, 60)])
    return labs


def gen_schedules(n=120, teachers=None, labs=None):
    """先造 50 门课程池，再让 120 条排课从池中重复引用（每课平均 2-3 条）"""
    n_courses = 50
    course_pool = []  # [(code, name, prefix), ...]
    for i in range(n_courses):
        prefix = random.choice(list(COURSE_PREFIXES.keys()))
        num = random.randint(101, 499)
        code = f"{prefix}{num}"
        course_pool.append((code, f"{COURSE_PREFIXES[prefix][0]}实验", prefix))

    rows = []
    days_cn = ["一", "二", "三", "四", "五", "六", "日"]
    for i in range(n):
        teacher = random.choice(teachers)
        lab = random.choice(labs)
        code, course_name, prefix = random.choice(course_pool)
        hours = random.choice([16, 24, 32, 48])
        class_year = random.choice([2023, 2024, 2025])
        class_num = random.randint(1, 8)
        class_name = f"{class_year}级{prefix}{class_num}班"
        students = random.randint(25, 50)
        day = random.randint(1, 5)
        start = random.choice([1, 3, 5, 7])
        end = min(start + random.choice([2, 4]) - 1, 9)
        wp = random.choice(["1-8", "1-16", "9-16", "1-12", "3-16", "1-8,10-16", "1-16单周", "1-16双周"])
        time_text = f"周{days_cn[day-1]}第{start}-{end}节({wp}周)"
        rows.append([code, course_name, hours, teacher[0], lab[0], class_name, students, time_text])
    return rows


def gen_experiments_by_schedule(schedules):
    by_course = {}
    for s in schedules:
        code = s[0]
        prefix = "".join(c for c in code if not c.isdigit())
        subject = COURSE_PREFIXES.get(prefix, ("计算机", "cs"))[1]
        n = random.randint(4, 6)
        exps = []
        for i in range(n):
            if i < len(EXP_TEMPLATES):
                name = f"实验{i + 1} {EXP_TEMPLATES[i]}"
            else:
                name = f"实验{i + 1} 综合设计与实现"
            exps.append([
                name,
                random.choice(CAT),
                random.choice(TYP),
                subject,
                random.choice(REQ),
                random.choice(EXPERIMENTER),
                random.choice([1, 2, 3, 4]),
                random.choice([2.0, 3.0, 4.0]),
            ])
        by_course[code] = exps
    return by_course


def write_lab_xlsx(labs):
    wb = Workbook()
    ws = wb.active
    ws.append(["实验室编号", "实验室名称", "负责人", "设备数"])
    for r in labs:
        ws.append(r)
    p = os.path.join(OUT_DIR, "data_lab_30.xlsx")
    wb.save(p)
    return p


def write_teacher_xlsx(teachers):
    wb = Workbook()
    ws = wb.active
    ws.append(["工号", "姓名", "院系", "联系电话"])
    for r in teachers:
        ws.append(r)
    p = os.path.join(OUT_DIR, "data_teacher_20.xlsx")
    wb.save(p)
    return p


def write_schedule_xlsx(schedules):
    wb = Workbook()
    ws = wb.active
    ws.append(["课程号", "课程名", "总学时", "教师工号", "实验室编号", "上课班级", "选课人数", "上课时间"])
    for r in schedules:
        ws.append(r)
    p = os.path.join(OUT_DIR, "data_schedule_120.xlsx")
    wb.save(p)
    return p


def write_experiment_xlsxs(exps_by_course):
    paths = []
    for code, exps in exps_by_course.items():
        wb = Workbook()
        ws = wb.active
        ws.append(["实验名称", "实验类别", "实验类型", "所属学科", "实验要求", "实验者类别", "每组人数", "学时数"])
        for r in exps:
            ws.append(r)
        safe = code.replace("/", "_")
        p = os.path.join(OUT_DIR, f"data_experiment_{safe}.xlsx")
        wb.save(p)
        paths.append((code, p, len(exps)))
    return paths


def write_sql(teachers):
    """历史学期 + query_history + notification（schedule_time 单独脚本处理）"""
    lines = [
        "-- ============================================",
        "-- LTPMS 测试辅助数据",
        "-- 用法: mysql -h127.0.0.1 -P3307 -ultpms -pltpms ltpms_db < data_seed.sql",
        "-- 前提: 已导入 data_lab_30.xlsx + data_teacher_20.xlsx + data_schedule_120.xlsx",
        "-- schedule_time 回填: 导入课表后跑 scripts/parse_schedule_time.py",
        "-- ============================================",
        "SET NAMES utf8mb4;",
        "",
        "-- 1) 历史学期（让'上学期复制'有数据可拷）",
        "INSERT INTO semester (academic_year, semester_num, semester_name, start_date, end_date, status) VALUES",
        "('2025-2026', 2, '2025-2026-2', '2026-02-23', '2026-07-05', 'ENDED'),",
        "('2025-2026', 1, '2025-2026-1', '2025-09-08', '2026-01-18', 'ENDED')",
        "ON DUPLICATE KEY UPDATE start_date = VALUES(start_date);",
        "",
    ]

    # 2) query_history (200 条)
    lines.append("-- 2) query_history 查询历史（200 条，让教师/管理员最近查询卡片有数据）")
    lines.append("INSERT INTO query_history (user_id, question, generated_sql, result_summary, success, role, created_at) VALUES")
    qh_rows = []
    sample_questions = [
        "本学期开设了多少门实验课",
        "计算机实验室使用率是多少",
        "张伟老师本学期任课几门",
        "列出所有未排课的实验室",
        "实验项目超过5个的课程有哪些",
        "本学期学时总数最高的3门课",
        "哪些教师本学期没有任课",
        "统计各院系教师数量",
        "近一个月有排课的实验室",
        "实验项目按时长排序",
        "本学期新增的课程",
        "哪些实验室负责人是张志强",
        "查询本学期排课最多的老师",
        "实验项目未关联课程的",
        "选课人数最多的课程",
    ]
    for i in range(200):
        t = random.choice(teachers)
        q = random.choice(sample_questions)
        success = 1 if random.random() > 0.1 else 0
        # 时间分布在最近 30 天
        days_ago = random.randint(0, 29)
        from datetime import datetime, timedelta
        ts = (datetime.now() - timedelta(days=days_ago, seconds=random.randint(0, 86400))).strftime("%Y-%m-%d %H:%M:%S")
        qh_rows.append(
            f"(NULL, '{q}', NULL, '返回 {random.randint(1, 50)} 条', {success}, 'TEACHER', '{ts}')"
        )
    lines.append(",\n".join(qh_rows) + ";")
    lines.append("")

    # 3) notification (30 条)
    lines.append("-- 3) notification 站内通知（30 条）")
    lines.append("INSERT INTO notification (user_id, title, content, type, ref_url, is_read, created_at) VALUES")
    notif_rows = []
    titles = [
        "2026-2027-1 学期排课通知",
        "实验项目录入截止提醒",
        "本周三教学研讨会",
        "实验室设备维护公告",
        "新课表系统已上线",
        "期末实验材料清点",
        "下学期开课预登记",
        "系统维护通知",
        "教学评估启动",
        "教材征订通知",
    ]
    contents = [
        "请各位老师在本周五前完成本学期实验排课录入。",
        "实验项目库录入截止时间为下月 15 日，逾期将影响下学期排课。",
        "本周三下午 2 点在 A301 召开实验教学研讨会，请准时参加。",
        "本周五 36-301 实验室进行设备维护，期间不可预约。",
        "新版智能查询系统已上线，欢迎通过首页 NLQ 入口体验。",
        "请各实验室负责人完成本学期实验材料清点工作。",
        "下学期开课预登记系统已开放，请提前登记。",
        "系统将于本周日凌晨 2-4 点进行例行维护。",
        "本学期教学评估已启动，请老师们关注学生反馈。",
        "2026-2027 学年春季学期教材征订开始。",
    ]
    from datetime import datetime, timedelta
    for i in range(30):
        title = random.choice(titles)
        content = random.choice(contents)
        ntype = random.choice(["SEMESTER_REMIND", "SYSTEM"])
        ref = random.choice(["/dashboard", "/schedules", "/experiments", "/reports", ""])
        is_read = random.choice([0, 1])
        user_id = "NULL" if random.random() < 0.4 else f"({random.randint(2, 6)})"  # 60% 个人
        days_ago = random.randint(0, 14)
        ts = (datetime.now() - timedelta(days=days_ago, seconds=random.randint(0, 86400))).strftime("%Y-%m-%d %H:%M:%S")
        notif_rows.append(f"({user_id}, '{title}', '{content}', '{ntype}', '{ref}', {is_read}, '{ts}')")
    lines.append(",\n".join(notif_rows) + ";")
    lines.append("")

    p = os.path.join(OUT_DIR, "data_seed.sql")
    with open(p, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    return p


def write_parse_schedule_time_script():
    """导入课表后跑这个脚本，自动解析 raw_schedule_text 回填 schedule_time"""
    code = '''"""
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
    m_sec = re.search(r"第(\\d+)-(\\d+)节", raw)
    start = int(m_sec.group(1)) if m_sec else 1
    end = int(m_sec.group(2)) if m_sec else 2
    m_week = re.search(r"\\(([^)]+周)\\)", raw)
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
'''
    p = os.path.join(SCRIPTS_DIR, "parse_schedule_time.py")
    with open(p, "w", encoding="utf-8") as f:
        f.write(code)
    return p


def main():
    print(f"输出目录: {OUT_DIR}")
    teachers = gen_teachers(20)
    labs = gen_labs()
    schedules = gen_schedules(120, teachers, labs)
    exps = gen_experiments_by_schedule(schedules)

    paths = {
        "lab": write_lab_xlsx(labs),
        "teacher": write_teacher_xlsx(teachers),
        "schedule": write_schedule_xlsx(schedules),
        "sql": write_sql(teachers),
        "parse_script": write_parse_schedule_time_script(),
    }
    exp_paths = write_experiment_xlsxs(exps)
    paths["experiments"] = exp_paths

    print("\n=== 生成完成 ===")
    print(f"实验室 xlsx:        {paths['lab']}  ({len(labs)} 条)")
    print(f"教师 xlsx:          {paths['teacher']}  ({len(teachers)} 条)")
    print(f"课表 xlsx:          {paths['schedule']}  ({len(schedules)} 条 → 自动生成 ~{len(exps)} 门课程)")
    print(f"实验 xlsx 数量:     {len(exp_paths)} 份 (按课程分裂)")
    print(f"  示例:             {exp_paths[0][1]}")
    print(f"辅助 SQL:           {paths['sql']}  (历史学期 + 200 query_history + 30 notification)")
    print(f"回填脚本:           {paths['parse_script']}  (课表导入后跑)")
    return paths


if __name__ == "__main__":
    main()
