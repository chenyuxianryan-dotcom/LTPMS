"""叙事报告 + 教学总结（T3.3）：把枯燥报表变成 AI 生成的叙事文档。

数字全部由 Python 从数据库聚合（真实报表数字），LLM 只做语言包装、禁止编数字。
权限分层（沿用 aggregate_only 语义）：
  - TEACHER：只统计本人任教数据（强制 teacher_id 过滤），kind 强制为个人教学总结
  - ADMIN ：全校口径（年度叙事报告 / 全校教学总结均可）
"""

from app.services.text_generation import generate_text
from app.utils.db import db

_SQL_ACTIVE_SEMESTER = (
    "SELECT id, semester_name, academic_year FROM semester "
    "WHERE start_date <= CURDATE() AND end_date >= CURDATE() "
    "ORDER BY start_date DESC LIMIT 1"
)
_SQL_LAST_ENDED_SEMESTER = (
    "SELECT id, semester_name, academic_year FROM semester "
    "WHERE end_date < CURDATE() ORDER BY end_date DESC LIMIT 1"
)


async def _one(sql: str, params: tuple = ()):
    rows = await db.execute(sql, params)
    return rows[0] if rows else None


def _num(value) -> int | float:
    """Decimal/None → 可读数字。"""
    if value is None:
        return 0
    return float(value) if hasattr(value, "as_tuple") else int(value)


async def _resolve_semester() -> dict | None:
    row = await _one(_SQL_ACTIVE_SEMESTER)
    return row or await _one(_SQL_LAST_ENDED_SEMESTER)


async def _school_numbers(semester: dict | None) -> dict:
    """全校口径聚合（管理员）。"""
    numbers: dict = {"semester_name": semester["semester_name"] if semester else None,
                     "academic_year": semester["academic_year"] if semester else None}
    if not semester:
        return numbers
    semester_id = semester["id"]

    row = await _one("SELECT COUNT(*) AS n FROM laboratory")
    numbers["laboratory_total"] = _num(row["n"]) if row else 0

    row = await _one("SELECT COALESCE(SUM(device_count), 0) AS n FROM laboratory")
    numbers["device_total"] = _num(row["n"]) if row else 0

    row = await _one("SELECT COUNT(DISTINCT teacher_id) AS n FROM schedule WHERE semester_id = %s", (semester_id,))
    numbers["teacher_total"] = _num(row["n"]) if row else 0

    row = await _one("SELECT COUNT(*) AS n, COUNT(DISTINCT course_code) AS c FROM schedule WHERE semester_id = %s", (semester_id,))
    numbers["schedule_total"] = _num(row["n"]) if row else 0
    numbers["course_total"] = _num(row["c"]) if row else 0

    row = await _one(
        "SELECT COALESCE(SUM(student_count * total_hours), 0) AS n FROM schedule WHERE semester_id = %s",
        (semester_id,),
    )
    numbers["person_hours"] = _num(row["n"]) if row else 0

    row = await _one(
        "SELECT COUNT(*) AS n FROM experiment e JOIN course c ON e.course_id = c.id WHERE c.semester_id = %s",
        (semester_id,),
    )
    numbers["experiment_total"] = _num(row["n"]) if row else 0

    return numbers


async def _teacher_numbers(semester: dict | None, teacher_id: int) -> dict:
    """教师个人口径聚合（仅本人任教数据，aggregate_only 语义）。"""
    numbers: dict = {"semester_name": semester["semester_name"] if semester else None,
                     "academic_year": semester["academic_year"] if semester else None}
    if not semester:
        return numbers
    semester_id = semester["id"]

    row = await _one(
        "SELECT COUNT(*) AS n, COUNT(DISTINCT course_code) AS c FROM schedule "
        "WHERE semester_id = %s AND teacher_id = %s",
        (semester_id, teacher_id),
    )
    numbers["schedule_total"] = _num(row["n"]) if row else 0
    numbers["course_total"] = _num(row["c"]) if row else 0

    row = await _one(
        "SELECT COALESCE(SUM(student_count * total_hours), 0) AS n FROM schedule "
        "WHERE semester_id = %s AND teacher_id = %s",
        (semester_id, teacher_id),
    )
    numbers["person_hours"] = _num(row["n"]) if row else 0

    row = await _one(
        "SELECT COUNT(*) AS n FROM experiment e JOIN course c ON e.course_id = c.id "
        "WHERE c.semester_id = %s AND c.teacher_id = %s",
        (semester_id, teacher_id),
    )
    numbers["experiment_total"] = _num(row["n"]) if row else 0

    return numbers


_ANNUAL_PROMPT = (
    "你是高校实验教学中心的教学秘书，根据下面提供的真实统计数据撰写一份叙事化的"
    "全校实验教学年度报告（300字左右，3~4段：总体概况 / 亮点观察 / 改进建议）。\n\n"
    "【严格要求：文中出现的每一个数字都必须原样来自提供的数据，"
    "不得编造、修改、推算任何数据之外的数字；数据为空或为0的项不要提及】\n\n"
    "统计数据：\n{numbers}"
)

_SUMMARY_PROMPT = (
    "你是高校实验教学中心的教学秘书，根据下面提供的真实统计数据撰写一份教学总结"
    "（250字左右，2~3段：教学工作概况 / 数据小结 / 下学期建议）。\n\n"
    "【严格要求：文中出现的每一个数字都必须原样来自提供的数据，"
    "不得编造、修改、推算任何数据之外的数字；数据为空或为0的项不要提及】\n\n"
    "统计数据：\n{numbers}"
)


async def generate_narrative(kind: str, role: str, user_id: int) -> dict:
    """生成叙事报告 / 教学总结。

    kind: annual_report（年度叙事报告，全校）| teaching_summary（教学总结）
    权限：TEACHER 强制个人口径（kind 也归一为 teaching_summary）；
          ADMIN 全校口径，两种 kind 均可。
    返回 {success, text, numbers, scope, error}。
    """
    if role == "TEACHER":
        kind = "teaching_summary"
        scope = "personal"
    else:
        scope = "school"

    semester = await _resolve_semester()
    if scope == "personal":
        numbers = await _teacher_numbers(semester, user_id)
    else:
        numbers = await _school_numbers(semester)

    if kind == "annual_report":
        prompt = _ANNUAL_PROMPT.format(numbers=str(numbers))
    else:
        prompt = _SUMMARY_PROMPT.format(numbers=str(numbers))

    result = await generate_text(prompt)
    if not result["success"]:
        return {"success": False, "text": "", "numbers": numbers, "scope": scope, "error": result["error"]}
    return {"success": True, "text": result["text"], "numbers": numbers, "scope": scope, "error": None}
