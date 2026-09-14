"""人员档案取数层：八维人档的参数化 SQL 集中管理。

与 Java 侧 ReportMapper.xml 存在两份等价查询逻辑，改动任一侧时请同步核对
下方注释标注的 mapper 位置，并跑 tests/test_dossier_sql.py 的对账用例。

约定：
- 全部使用参数化占位符，禁止字符串拼接（姓名来自用户输入）。
- 全库无物理外键，关联均按命名约定：user.id ← teacher_id，
  schedule → course 走 (course_code, semester_id) 而非 course.id，
  schedule → laboratory 走 lab_code 字符串。
"""

from app.utils.db import db

ROLE_LABELS = {"ADMIN": "管理员", "TEACHER": "教师"}
WEEKDAY_LABELS = {1: "周一", 2: "周二", 3: "周三", 4: "周四", 5: "周五", 6: "周六", 7: "周日"}
WEEK_TYPE_LABELS = {"ALL": "每周", "ODD": "单周", "EVEN": "双周"}

# 行为足迹各子表的取样上限：档案要求"详尽"，但足迹是辅助证据，不必全量
FOOTPRINT_LIMIT = 20


def mask_phone(phone: str | None) -> str | None:
    """手机号打码：保留前 3 位与后 4 位。回喂给模型的一律是打码值。"""
    if not phone:
        return phone
    digits = str(phone).strip()
    if len(digits) < 7:
        return "***"
    return f"{digits[:3]}****{digits[-4:]}"


def _escape_like(value: str) -> str:
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def fmt(value):
    """datetime → 可读字符串；None 原样返回。"""
    if value is None:
        return None
    if hasattr(value, "strftime"):
        return value.strftime("%Y-%m-%d %H:%M")
    return value


async def _semester_id(semester: str | None) -> tuple[int | None, str | None]:
    """把学期名（2026-2027-1）或学年（2026-2027）解析为 id。未找到抛 LookupError。"""
    if not semester:
        return None, None
    rows = await db.execute(
        "SELECT id, semester_name FROM semester WHERE semester_name = %s OR academic_year = %s"
        " ORDER BY id DESC LIMIT 1",
        (semester, semester),
    )
    if not rows:
        raise LookupError(f"未找到学期「{semester}」，可用学期名形如 2026-2027-1")
    return rows[0]["id"], rows[0]["semester_name"]


async def find_users_by_name(name: str) -> list[dict]:
    """精确匹配 real_name 或 username（工号）。"""
    return await db.execute(
        "SELECT id, username, real_name, role, department, phone, status, created_at"
        " FROM user WHERE real_name = %s OR username = %s ORDER BY id",
        (name, name),
    )


async def find_users_like(name: str, limit: int = 10) -> list[dict]:
    """近似匹配，仅用于零命中时给出候选建议，不用于取数。"""
    pattern = f"%{_escape_like(name)}%"
    return await db.execute(
        "SELECT id, username, real_name, role, department, phone, status, created_at"
        " FROM user WHERE real_name LIKE %s OR username LIKE %s ORDER BY id LIMIT %s",
        (pattern, pattern, limit),
    )


async def find_manager_labs_by_name(name: str) -> list[dict]:
    """按 laboratory.manager 精确等值匹配。用于解析只有挂名、没有账号的人员。"""
    return await db.execute(
        "SELECT lab_code, lab_name, manager, device_count, status FROM laboratory"
        " WHERE manager = %s ORDER BY lab_code",
        (name,),
    )


async def get_user_by_ref(ref: str) -> dict | None:
    """person_ref 只接受工号（username）或数字 id，不接受姓名。"""
    ref = (ref or "").strip()
    if not ref:
        return None
    if ref.isdigit():
        rows = await db.execute(
            "SELECT id, username, real_name, role, department, phone, status, created_at,"
            " is_default_pwd, locked_until FROM user WHERE id = %s",
            (int(ref),),
        )
    else:
        rows = await db.execute(
            "SELECT id, username, real_name, role, department, phone, status, created_at,"
            " is_default_pwd, locked_until FROM user WHERE username = %s",
            (ref,),
        )
    return rows[0] if rows else None


async def identity(user_id: int) -> list[dict]:
    return await db.execute(
        "SELECT username, real_name, role, department, phone, status, is_default_pwd,"
        " created_at, locked_until FROM user WHERE id = %s",
        (user_id,),
    )


async def courses(user_id: int, semester: str | None = None) -> list[dict]:
    sem_id, _ = await _semester_id(semester)
    sql = (
        "SELECT c.course_code, c.course_name, c.total_hours, c.major, s.semester_name"
        " FROM course c LEFT JOIN semester s ON c.semester_id = s.id"
        " WHERE c.teacher_id = %s"
    )
    params: list = [user_id]
    if sem_id is not None:
        sql += " AND c.semester_id = %s"
        params.append(sem_id)
    sql += " ORDER BY s.semester_name DESC, c.course_code"
    return await db.execute(sql, tuple(params))


async def schedules(user_id: int, semester: str | None = None) -> list[dict]:
    """排课记录。laboratory 走 lab_code 字符串关联（ReportMapper.xml:16 同构）。"""
    sem_id, _ = await _semester_id(semester)
    sql = (
        "SELECT sc.id AS schedule_id, sc.course_code, sc.course_name, sc.lab_code,"
        " l.lab_name, sc.class_names, sc.student_count, sc.total_hours, sc.raw_schedule_text,"
        " s.semester_name"
        " FROM schedule sc"
        " LEFT JOIN laboratory l ON sc.lab_code = l.lab_code"
        " LEFT JOIN semester s ON sc.semester_id = s.id"
        " WHERE sc.teacher_id = %s"
    )
    params: list = [user_id]
    if sem_id is not None:
        sql += " AND sc.semester_id = %s"
        params.append(sem_id)
    sql += " ORDER BY s.semester_name DESC, sc.course_code"
    return await db.execute(sql, tuple(params))


async def class_times(user_id: int, semester: str | None = None) -> list[dict]:
    """课节时间明细：user → schedule → schedule_time 二跳。"""
    sem_id, _ = await _semester_id(semester)
    sql = (
        "SELECT sc.course_code, sc.course_name, st.day_of_week, st.start_section,"
        " st.end_section, st.week_range, st.week_type, s.semester_name"
        " FROM schedule sc"
        " JOIN schedule_time st ON st.schedule_id = sc.id"
        " LEFT JOIN semester s ON sc.semester_id = s.id"
        " WHERE sc.teacher_id = %s"
    )
    params: list = [user_id]
    if sem_id is not None:
        sql += " AND sc.semester_id = %s"
        params.append(sem_id)
    sql += " ORDER BY s.semester_name DESC, sc.course_code, st.day_of_week, st.start_section"
    return await db.execute(sql, tuple(params))


async def experiments(user_id: int, semester: str | None = None) -> list[dict]:
    """实验项目：user → course → experiment 二跳（ReportMapper.xml:17-19 同构）。"""
    sem_id, _ = await _semester_id(semester)
    sql = (
        "SELECT c.course_code, c.course_name, e.experiment_code, e.experiment_name,"
        " e.category, e.type, e.subject, e.requirement, e.experimenter_type,"
        " e.group_size, e.hours, e.sort_order, s.semester_name"
        " FROM course c"
        " JOIN experiment e ON e.course_id = c.id"
        " LEFT JOIN semester s ON c.semester_id = s.id"
        " WHERE c.teacher_id = %s"
    )
    params: list = [user_id]
    if sem_id is not None:
        sql += " AND c.semester_id = %s"
        params.append(sem_id)
    sql += " ORDER BY s.semester_name DESC, c.course_code, e.sort_order"
    return await db.execute(sql, tuple(params))


async def managed_labs(real_name: str) -> list[dict]:
    """管理的实验室。laboratory 无 manager_id，只能按姓名精确等值匹配。"""
    return await find_manager_labs_by_name(real_name)


async def footprints(user_id: int) -> dict:
    """行为足迹三子表（ADMIN only，权限由工具层把关）。"""
    history = await db.execute(
        "SELECT question, success, role, created_at FROM query_history"
        " WHERE user_id = %s ORDER BY created_at DESC LIMIT %s",
        (user_id, FOOTPRINT_LIMIT),
    )
    notifications = await db.execute(
        "SELECT title, type, is_read, created_at FROM notification"
        " WHERE user_id = %s ORDER BY created_at DESC LIMIT %s",
        (user_id, FOOTPRINT_LIMIT),
    )
    operations = await db.execute(
        "SELECT action, target_type, target_id, ip, created_at FROM operation_log"
        " WHERE user_id = %s ORDER BY created_at DESC LIMIT %s",
        (user_id, FOOTPRINT_LIMIT),
    )
    return {"history": history, "notifications": notifications, "operations": operations}


async def workload(user_id: int, semester: str | None = None) -> dict:
    """派生洞察。分开查询避免多表 JOIN 的行扇出导致重复计数。"""
    sem_id, _ = await _semester_id(semester)
    sem_filter = " AND c.semester_id = %s" if sem_id is not None else ""
    sem_filter_s = " AND s.semester_id = %s" if sem_id is not None else ""
    args = (user_id, sem_id) if sem_id is not None else (user_id,)

    course_rows = await db.execute(
        "SELECT COUNT(*) AS course_count, COALESCE(SUM(c.total_hours), 0) AS total_hours"
        f" FROM course c WHERE c.teacher_id = %s{sem_filter}",
        args,
    )
    experiment_rows = await db.execute(
        "SELECT COUNT(*) AS experiment_count FROM course c"
        f" JOIN experiment e ON e.course_id = c.id WHERE c.teacher_id = %s{sem_filter}",
        args,
    )
    schedule_rows = await db.execute(
        "SELECT COUNT(*) AS schedule_count FROM schedule s WHERE s.teacher_id = %s"
        f"{sem_filter_s}",
        args,
    )
    # 人时数 = SUM(排课学时 × 班级人数)，语义是"学生学时"，不是教师工作量
    # （ReportMapper.xml:39 person-hours 同构）
    student_hours_rows = await db.execute(
        "SELECT COALESCE(SUM(s.total_hours * s.student_count), 0) AS student_hours"
        f" FROM schedule s WHERE s.teacher_id = %s{sem_filter_s}",
        args,
    )
    gap_rows = await db.execute(
        "SELECT c.course_code, c.course_name FROM course c"
        " WHERE c.teacher_id = %s AND NOT EXISTS"
        " (SELECT 1 FROM experiment e WHERE e.course_id = c.id)"
        f"{sem_filter}",
        args,
    )

    course = course_rows[0] if course_rows else {}
    return {
        "course_count": int(course.get("course_count") or 0),
        "total_hours": float(course.get("total_hours") or 0),
        "experiment_count": int((experiment_rows[0] or {}).get("experiment_count") or 0),
        "schedule_count": int((schedule_rows[0] or {}).get("schedule_count") or 0),
        "student_hours": float((student_hours_rows[0] or {}).get("student_hours") or 0),
        "syllabus_gap": gap_rows,
    }
