"""MCP 只读数据查询层（V3）。

四个只读工具共用的数据访问：固定 SQL（参数化过滤，不拼自由文本）、
复用 ltpms_readonly 只读账号（app.utils.db 单例，MCP 独立进程内自建连接池）。
红线：MCP 层无任何写路径；行数上限受 settings.mcp_max_rows 约束。

注意：ltpms_readonly 是全校级只读，MCP 工具未按教师/管理员角色隔离——
仅用于本地答辩演示；若面向多用户需在 MCP 层接入权限注入（任务清单 T3.1.1 已标注）。
"""

import aiomysql

from app.config import settings
from app.utils.db import db

# ---- 学期解析：当前学期 → 最近结束的学期 → 最早的未来学期 ----

_SQL_ACTIVE_SEMESTER = (
    "SELECT id, semester_name, academic_year, semester_num FROM semester "
    "WHERE start_date <= CURDATE() AND end_date >= CURDATE() "
    "ORDER BY start_date DESC LIMIT 1"
)
_SQL_LAST_ENDED_SEMESTER = (
    "SELECT id, semester_name, academic_year, semester_num FROM semester "
    "WHERE end_date < CURDATE() ORDER BY end_date DESC LIMIT 1"
)
_SQL_NEXT_SEMESTER = (
    "SELECT id, semester_name, academic_year, semester_num FROM semester "
    "WHERE start_date > CURDATE() ORDER BY start_date ASC LIMIT 1"
)
_SQL_SEMESTER_BY_NAME = (
    "SELECT id, semester_name, academic_year, semester_num FROM semester "
    "WHERE semester_name = %s LIMIT 1"
)


def _serialize(value):
    """把 Decimal / date / datetime 等转为 JSON 可序列化类型。"""
    if value is None:
        return None
    if hasattr(value, "isoformat"):
        return value.isoformat(sep=" ") if hasattr(value, "hour") else value.isoformat()
    if hasattr(value, "as_tuple"):  # Decimal
        return float(value)
    return value


def _serialize_rows(rows: list[dict]) -> list[dict]:
    return [{k: _serialize(v) for k, v in row.items()} for row in rows]


def _clamp_limit(limit: int) -> int:
    try:
        limit = int(limit)
    except (TypeError, ValueError):
        return 20
    return max(1, min(limit, settings.mcp_max_rows))


async def _one(sql: str, params: tuple = ()) -> dict | None:
    rows = await db.execute(sql, params)
    return rows[0] if rows else None


async def resolve_semester(semester_name: str | None = None) -> dict | None:
    """解析目标学期：显式指定 → 按名查询；未指定 → 当前/最近结束/最早未来。"""
    if semester_name:
        return await _one(_SQL_SEMESTER_BY_NAME, (semester_name,))
    for sql in (_SQL_ACTIVE_SEMESTER, _SQL_LAST_ENDED_SEMESTER, _SQL_NEXT_SEMESTER):
        row = await _one(sql)
        if row:
            return row
    return None


def _error(message: str) -> dict:
    return {"success": False, "rows": [], "count": 0, "error": message}


def _ok(rows: list[dict]) -> dict:
    return {"success": True, "rows": _serialize_rows(rows), "count": len(rows), "error": None}


async def query_laboratories(
    lab_code: str | None = None, status: int | None = None, limit: int = 20
) -> dict:
    """查询实验室列表（只读）。全部过滤参数可选。"""
    if status is not None and status not in (0, 1):
        return _error("status 仅支持 0（停用）或 1（启用）")
    where, params = [], []
    if lab_code:
        where.append("lab_code = %s")
        params.append(lab_code)
    if status is not None:
        where.append("status = %s")
        params.append(status)
    sql = (
        "SELECT id, lab_code, lab_name, manager, device_count, status FROM laboratory"
        + (" WHERE " + " AND ".join(where) if where else "")
        + " ORDER BY lab_code LIMIT %s"
    )
    params.append(_clamp_limit(limit))
    return _ok(await db.execute(sql, tuple(params)))


async def query_courses(
    semester_name: str | None = None,
    course_code: str | None = None,
    course_name_keyword: str | None = None,
    limit: int = 20,
) -> dict:
    """查询实验课程列表（只读）。未指定学期时默认当前学期。"""
    semester = await resolve_semester(semester_name)
    if semester_name and semester is None:
        return _error(f"学期不存在: {semester_name}")
    where, params = ["c.semester_id = %s"], [semester["id"]]
    if course_code:
        where.append("c.course_code = %s")
        params.append(course_code)
    if course_name_keyword:
        where.append("c.course_name LIKE %s")
        params.append(f"%{course_name_keyword}%")
    sql = (
        "SELECT c.id, c.course_code, c.course_name, c.total_hours, c.major, "
        "s.semester_name, u.real_name AS teacher_name "
        "FROM course c "
        "JOIN semester s ON c.semester_id = s.id "
        "LEFT JOIN user u ON c.teacher_id = u.id "
        "WHERE " + " AND ".join(where) + " ORDER BY c.course_code LIMIT %s"
    )
    params.append(_clamp_limit(limit))
    return _ok(await db.execute(sql, tuple(params)))


async def query_experiments(
    course_code: str | None = None,
    experiment_name_keyword: str | None = None,
    semester_name: str | None = None,
    limit: int = 50,
) -> dict:
    """查询实验项目列表（只读）。未指定学期时默认当前学期。"""
    semester = await resolve_semester(semester_name)
    if semester_name and semester is None:
        return _error(f"学期不存在: {semester_name}")
    where, params = ["c.semester_id = %s"], [semester["id"]]
    if course_code:
        where.append("c.course_code = %s")
        params.append(course_code)
    if experiment_name_keyword:
        where.append("e.experiment_name LIKE %s")
        params.append(f"%{experiment_name_keyword}%")
    sql = (
        "SELECT e.id, e.experiment_code, e.experiment_name, e.category, e.type, "
        "e.subject, e.hours, e.group_size, c.course_code, c.course_name, s.semester_name "
        "FROM experiment e "
        "JOIN course c ON e.course_id = c.id "
        "JOIN semester s ON c.semester_id = s.id "
        "WHERE " + " AND ".join(where) + " ORDER BY c.course_code, e.sort_order LIMIT %s"
    )
    params.append(_clamp_limit(limit))
    return _ok(await db.execute(sql, tuple(params)))


async def query_schedules(
    lab_code: str | None = None,
    semester_name: str | None = None,
    course_code: str | None = None,
    limit: int = 50,
) -> dict:
    """查询排课/课表记录（只读）。未指定学期时默认当前学期。"""
    semester = await resolve_semester(semester_name)
    if semester_name and semester is None:
        return _error(f"学期不存在: {semester_name}")
    where, params = ["sc.semester_id = %s"], [semester["id"]]
    if lab_code:
        where.append("sc.lab_code = %s")
        params.append(lab_code)
    if course_code:
        where.append("sc.course_code = %s")
        params.append(course_code)
    sql = (
        "SELECT sc.id, sc.course_code, sc.course_name, sc.total_hours, "
        "u.real_name AS teacher_name, sc.lab_code, sc.class_names, sc.student_count, "
        "sc.raw_schedule_text, s.semester_name "
        "FROM schedule sc "
        "JOIN semester s ON sc.semester_id = s.id "
        "LEFT JOIN user u ON sc.teacher_id = u.id "
        "WHERE " + " AND ".join(where) + " ORDER BY sc.lab_code, sc.course_code LIMIT %s"
    )
    params.append(_clamp_limit(limit))
    return _ok(await db.execute(sql, tuple(params)))
