"""实验台数字档案（T3.2）：AI 生成实验室/实验项目的数字档案。

实验室档案 = 基本信息 + 按学期使用统计；
实验项目档案 = 实验要点 + 所属课题全貌（成员/分组进度、任务与材料提交状态、排课统计）。
数据聚合（真实统计）→ 共享 generate_text 做语言包装 → SQLite 缓存（TTL 24h）。
红线：数字只能来自聚合数据，LLM 只做语言包装不编数字；派生值（完成度、应分组数）在 Python 侧算好；
      学号/姓名不进模型上下文；无档案时前端降级显示统计。
"""

import hashlib
import json
import math
import os
import sqlite3
import threading
from datetime import datetime, timedelta
from typing import Any

from app.services.text_generation import generate_text
from app.utils.db import db

ARCHIVE_TTL_HOURS = 24

# ---- 统计聚合 SQL（真实数据，LLM 的唯一数字来源） ----

_SQL_LAB = (
    "SELECT id, lab_code, lab_name, manager, device_count, status, created_at "
    "FROM laboratory WHERE id = %s LIMIT 1"
)
_SQL_LAB_USAGE = (
    "SELECT s.semester_name, COUNT(sc.id) AS schedule_count, "
    "COALESCE(SUM(sc.total_hours), 0) AS total_hours, "
    "COALESCE(SUM(sc.student_count), 0) AS student_total, "
    "COUNT(DISTINCT sc.course_code) AS course_count "
    "FROM schedule sc JOIN semester s ON sc.semester_id = s.id "
    "WHERE sc.lab_code = %s GROUP BY s.semester_name ORDER BY s.semester_name DESC"
)
_SQL_EXPERIMENT = (
    "SELECT e.id, e.course_id, e.experiment_code, e.experiment_name, e.category, e.type, e.subject, "
    "e.hours, e.group_size, e.requirement, e.created_at, "
    "c.course_code, c.course_name, s.semester_name "
    "FROM experiment e JOIN course c ON e.course_id = c.id "
    "JOIN semester s ON c.semester_id = s.id "
    "WHERE e.id = %s LIMIT 1"
)
_SQL_EXPERIMENT_USAGE = (
    "SELECT COUNT(sc.id) AS schedule_count, COALESCE(SUM(sc.student_count), 0) AS student_total "
    "FROM schedule sc WHERE sc.course_code = %s"
)

# ---- 课题全貌聚合（实验行经 experiment.course_id → SYSTEM 容器课题一跳） ----

_SQL_CONTAINER_PROJECT = (
    "SELECT id, project_code, project_name, description, status, created_at "
    "FROM lab_project WHERE course_id = %s AND source = 'SYSTEM' LIMIT 1"
)
_SQL_CATALOG = (
    "SELECT id, experiment_code, experiment_name, category, requirement, group_size, hours "
    "FROM experiment WHERE course_id = %s ORDER BY sort_order, id"
)
_SQL_MEMBER_COUNT = (
    "SELECT COUNT(*) AS member_count FROM lab_project_member WHERE project_id = %s"
)
_SQL_TASK_STATUS = (
    "SELECT status, COUNT(*) AS cnt FROM lab_project_task "
    "WHERE project_id = %s AND deleted = 0 GROUP BY status"
)
# 逾期为查询时派生（与 ProjectStatsService.isOverdue 同口径：过期且未开始/进行中）
_SQL_TASK_OVERDUE = (
    "SELECT COUNT(*) AS overdue_count FROM lab_project_task "
    "WHERE project_id = %s AND deleted = 0 AND deadline IS NOT NULL AND deadline < NOW() "
    "AND status IN ('NOT_STARTED', 'IN_PROGRESS')"
)
_SQL_TASK_COVERAGE = (
    "SELECT COUNT(DISTINCT assignee_student_id) AS assigned_student_count FROM lab_project_task "
    "WHERE project_id = %s AND deleted = 0 AND status <> 'CLOSED'"
)
_SQL_SUBMISSIONS = (
    "SELECT COUNT(*) AS submission_count, "
    "COALESCE(SUM(CASE WHEN s.reviewed_at IS NULL THEN 1 ELSE 0 END), 0) AS pending_review_count "
    "FROM lab_task_submission s JOIN lab_project_task t ON s.task_id = t.id "
    "WHERE t.project_id = %s AND t.deleted = 0"
)
_SQL_DATASETS = (
    "SELECT COUNT(*) AS dataset_count, COALESCE(SUM(latest_version), 0) AS version_count, "
    "COALESCE(MAX(latest_version), 0) AS latest_version_max "
    "FROM lab_dataset WHERE project_id = %s AND deleted = 0"
)
# 本实验各组人数：只计仍在课题成员池内的关系（经 lab_project_member 过滤，防历史野行）
_SQL_GROUP_DETAIL = (
    "SELECT g.id, g.group_name, g.baseline_size, COUNT(pm.student_id) AS member_count "
    "FROM lab_project_group g "
    "LEFT JOIN lab_project_group_member gm ON gm.group_id = g.id "
    "LEFT JOIN lab_project_member pm ON pm.project_id = g.project_id AND pm.student_id = gm.student_id "
    "WHERE g.project_id = %s AND g.experiment_id = %s "
    "GROUP BY g.id, g.group_name, g.baseline_size ORDER BY g.id"
)
_SQL_GROUPING_BY_EXPERIMENT = (
    "SELECT e.id, e.experiment_code, e.experiment_name, e.group_size, "
    "COUNT(DISTINCT g.id) AS group_count, COUNT(DISTINCT pm.student_id) AS grouped_student_count "
    "FROM experiment e "
    "LEFT JOIN lab_project_group g ON g.experiment_id = e.id AND g.project_id = %s "
    "LEFT JOIN lab_project_group_member gm ON gm.group_id = g.id "
    "LEFT JOIN lab_project_member pm ON pm.project_id = %s AND pm.student_id = gm.student_id "
    "WHERE e.course_id = %s "
    "GROUP BY e.id, e.experiment_code, e.experiment_name, e.group_size "
    "ORDER BY e.sort_order, e.id"
)


def _default_value(value: Any) -> Any:
    if value is None:
        return None
    if hasattr(value, "isoformat"):
        return value.isoformat(sep=" ") if hasattr(value, "hour") else value.isoformat()
    if hasattr(value, "as_tuple"):
        return float(value)
    return value


def _first_row(rows: list[dict]) -> dict:
    return rows[0] if rows else {}


def _int(row: dict | None, key: str) -> int:
    """聚合列缺行/缺键时按 0 计（假数据与降级路径下不抛异常）"""
    value = (row or {}).get(key)
    return int(value) if value is not None else 0


def _rate(numerator: int, denominator: int) -> float:
    return round(numerator * 10000.0 / denominator) / 100.0 if denominator else 0.0


class ArchiveCache:
    """数字档案 SQLite 缓存：key=entity_type:entity_id:stats_hash，TTL 内直接复用。"""

    def __init__(self, db_path: str):
        self._lock = threading.Lock()
        parent = os.path.dirname(os.path.abspath(db_path))
        if parent:
            os.makedirs(parent, exist_ok=True)
        self._conn = sqlite3.connect(db_path, check_same_thread=False)
        with self._lock:
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS archive_cache (
                    cache_key TEXT PRIMARY KEY,
                    text TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """
            )
            self._conn.commit()

    def get(self, cache_key: str, ttl_hours: int = ARCHIVE_TTL_HOURS) -> str | None:
        with self._lock:
            row = self._conn.execute(
                "SELECT text, created_at FROM archive_cache WHERE cache_key = ?",
                (cache_key,),
            ).fetchone()
        if not row:
            return None
        created = datetime.fromisoformat(row[1])
        if datetime.now() - created > timedelta(hours=ttl_hours):
            return None
        return row[0]

    def set(self, cache_key: str, text: str) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT OR REPLACE INTO archive_cache (cache_key, text) VALUES (?, ?)",
                (cache_key, text),
            )
            self._conn.commit()


_LAB_PROMPT = (
    "你是高校实验教学中心的数字档案管理员，负责为实验室撰写有温度的数字档案。"
    "根据下面提供的实验室真实数据，输出三部分，用小标题分隔：\n"
    "一句话简介（50字以内）/ 历史沿革（100字左右）/ 使用统计小结（准确引用数据）。\n\n"
    "【严格要求：所有数字只能来自提供的数据，不得编造、推算或引入数据之外的数字】\n\n"
    "实验室数据：\n{stats}"
)

_EXPERIMENT_PROMPT = (
    "你是高校实验教学中心的数字档案管理员，为实验项目撰写一份完整数字档案。"
    "目标是教师读完这一篇即可掌握该项目的全部关键信息，无需再跳转其他页面。\n"
    "严格按以下六个小标题输出，缺数据的小标题写「暂无数据」，不要把原始字段名罗列进正文：\n"
    "一、项目总体介绍（实验名称与编号、类别/类型/学科/要求、学时、每组人数、"
    "所属课程与学期、所属课题容器概况）\n"
    "二、实验目录全景（本课程实验总数、本实验在目录中的次序、目录内每组人数区间）\n"
    "三、成员与分组分配进度（成员总数、本实验已分组/未分组人数、当前组数与按每组人数应分组数、"
    "各组人数、分组是否与目录基准一致）\n"
    "四、任务与材料提交状态（任务总数与各状态数量、完成度、逾期任务数、"
    "已派发/尚未派发任务的学生数、提交条数与待验收条数、数据集数量与版本总数）\n"
    "五、排课与使用统计（排课次数、覆盖学生人次）\n"
    "六、风险提示（仅依据上述数字给 1-3 条，如未分组人数、逾期任务、待验收积压）\n\n"
    "【严格要求：所有数字只能来自提供的数据，不得编造、推算或引入数据之外的数字；"
    "完成度、应分组数等派生值已在数据中算好，直接引用，不要自行计算或改写口径】\n"
    "篇幅：整篇 500-700 字，信息密度优先，语言克制。\n\n"
    "实验项目与课题数据：\n{stats}"
)


def _cache_key(entity_type: str, entity_id: int, stats: dict) -> str:
    raw = json.dumps(stats, ensure_ascii=False, sort_keys=True, default=str)
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
    return f"{entity_type}:{entity_id}:{digest}"


def _stats_text(stats: dict) -> str:
    return json.dumps(stats, ensure_ascii=False, indent=2, default=str)


def _catalog_overview(rows: list[dict], experiment_id: int) -> dict:
    """实验目录全景：实验总数、本实验次序、目录内每组人数区间"""
    sizes = [_int(row, "group_size") for row in rows]
    return {
        "experiment_count": len(rows),
        "position": next(
            (index for index, row in enumerate(rows, start=1) if row.get("id") == experiment_id), None
        ),
        "group_size_min": min(sizes) if sizes else None,
        "group_size_max": max(sizes) if sizes else None,
        "experiments": [
            {
                "experiment_code": row.get("experiment_code"),
                "experiment_name": row.get("experiment_name"),
                "group_size": _int(row, "group_size"),
                "hours": _default_value(row.get("hours")),
            }
            for row in rows
        ],
    }


def _grouping_overview(group_rows: list[dict], per_experiment: list[dict],
                       member_count: int, baseline: int) -> dict:
    """本实验分组进度 + 目录内各实验分组概况；consistent=已全员覆盖且组数/人数均合目录基准"""
    groups = [
        {
            "group_name": row.get("group_name"),
            "baseline_size": _int(row, "baseline_size"),
            "member_count": _int(row, "member_count"),
        }
        for row in group_rows
    ]
    grouped = sum(group["member_count"] for group in groups)
    expected = math.ceil(member_count / baseline) if baseline > 0 and member_count > 0 else 0
    consistent = bool(groups) and all(
        group["baseline_size"] == baseline and group["member_count"] <= baseline for group in groups
    ) and grouped == member_count and expected == len(groups)
    return {
        "baseline_size": baseline,
        "member_count": member_count,
        "grouped_student_count": grouped,
        "ungrouped_student_count": max(member_count - grouped, 0),
        "actual_group_count": len(groups),
        "expected_group_count": expected,
        "consistent": consistent,
        "groups": groups,
        "by_experiment": [
            {
                "experiment_code": row.get("experiment_code"),
                "experiment_name": row.get("experiment_name"),
                "group_size": _int(row, "group_size"),
                "group_count": _int(row, "group_count"),
                "grouped_student_count": _int(row, "grouped_student_count"),
            }
            for row in per_experiment
        ],
    }


async def collect_lab_stats(lab_id: int) -> dict:
    """聚合实验室档案数据：基本信息 + 按学期使用统计。找不到抛 ValueError。"""
    lab_rows = await db.execute(_SQL_LAB, (lab_id,))
    if not lab_rows:
        raise ValueError(f"实验室不存在: id={lab_id}")
    lab = lab_rows[0]
    usage = await db.execute(_SQL_LAB_USAGE, (lab["lab_code"],))
    return {
        "basic": {k: _default_value(v) for k, v in lab.items()},
        "usage_by_semester": [
            {k: _default_value(v) for k, v in row.items()} for row in usage
        ],
    }


async def collect_experiment_stats(experiment_id: int) -> dict:
    """聚合实验项目档案数据：实验信息 + 所属课程 + 排课统计 + 所属课题全貌。

    课题各节经 experiment.course_id → SYSTEM 容器课题一跳取得；
    容器尚未懒生成时各节显式为 None（区别于 0，避免被读成「全员未提交」）。找不到实验抛 ValueError。
    """
    rows = await db.execute(_SQL_EXPERIMENT, (experiment_id,))
    if not rows:
        raise ValueError(f"实验项目不存在: id={experiment_id}")
    exp = rows[0]
    usage = await db.execute(_SQL_EXPERIMENT_USAGE, (exp["course_code"],))
    stats = {k: _default_value(v) for k, v in exp.items()}
    usage_row = _first_row(usage)
    stats["usage"] = {
        k: _default_value(usage_row.get(k)) for k in ("schedule_count", "student_total")
    }
    stats["catalog"] = _catalog_overview(
        await db.execute(_SQL_CATALOG, (exp["course_id"],)), experiment_id
    )

    project_row = _first_row(await db.execute(_SQL_CONTAINER_PROJECT, (exp["course_id"],)))
    if not project_row:
        stats["project"] = None
        stats["members"] = None
        stats["grouping"] = None
        stats["tasks"] = None
        stats["materials"] = None
        return stats

    project_id = project_row["id"]
    stats["project"] = {k: _default_value(v) for k, v in project_row.items()}
    member_count = _int(_first_row(await db.execute(_SQL_MEMBER_COUNT, (project_id,))), "member_count")
    stats["members"] = {"member_count": member_count}

    by_status = {
        row["status"]: _int(row, "cnt")
        for row in await db.execute(_SQL_TASK_STATUS, (project_id,))
    }
    total = sum(by_status.values())
    closed = by_status.get("CLOSED", 0)
    effective = total - closed
    accepted = by_status.get("ACCEPTED", 0)
    assigned = _int(
        _first_row(await db.execute(_SQL_TASK_COVERAGE, (project_id,))), "assigned_student_count"
    )
    stats["tasks"] = {
        "total": total,
        "effective_excluding_closed": effective,
        "by_status": by_status,
        "accepted": accepted,
        # 完成度口径与 ProjectStatsService 一致：ACCEPTED /（总数 − CLOSED）
        "completion_rate": _rate(accepted, effective),
        "overdue": _int(_first_row(await db.execute(_SQL_TASK_OVERDUE, (project_id,))), "overdue_count"),
        "assigned_student_count": assigned,
        "unassigned_student_count": max(member_count - assigned, 0),
    }

    submission_row = _first_row(await db.execute(_SQL_SUBMISSIONS, (project_id,)))
    dataset_row = _first_row(await db.execute(_SQL_DATASETS, (project_id,)))
    stats["materials"] = {
        "submission_count": _int(submission_row, "submission_count"),
        "pending_review_count": _int(submission_row, "pending_review_count"),
        "dataset_count": _int(dataset_row, "dataset_count"),
        "version_count": _int(dataset_row, "version_count"),
        "latest_version_max": _int(dataset_row, "latest_version_max"),
    }

    stats["grouping"] = _grouping_overview(
        await db.execute(_SQL_GROUP_DETAIL, (project_id, experiment_id)),
        await db.execute(
            _SQL_GROUPING_BY_EXPERIMENT, (project_id, project_id, exp["course_id"])
        ),
        member_count,
        _int(exp, "group_size"),
    )
    return stats


async def generate_archive(entity_type: str, entity_id: int, cache: ArchiveCache | None = None) -> dict:
    """生成数字档案：聚合真实数据 → LLM 包装 → 缓存。

    返回 {success, text, cached, stats, error}。
    """
    try:
        if entity_type == "laboratory":
            stats = await collect_lab_stats(entity_id)
            prompt_template = _LAB_PROMPT
        elif entity_type == "experiment":
            stats = await collect_experiment_stats(entity_id)
            prompt_template = _EXPERIMENT_PROMPT
        else:
            return {
                "success": False, "text": "", "cached": False, "stats": None,
                "error": f"不支持的档案类型: {entity_type}",
            }
    except ValueError as e:
        return {
            "success": False, "text": "", "cached": False, "stats": None,
            "error": str(e),
        }

    key = _cache_key(entity_type, entity_id, stats)
    if cache is not None:
        cached_text = cache.get(key)
        if cached_text is not None:
            return {"success": True, "text": cached_text, "cached": True, "stats": stats, "error": None}

    result = await generate_text(prompt_template.format(stats=_stats_text(stats)))
    if not result["success"]:
        return {
            "success": False, "text": "", "cached": False, "stats": stats,
            "error": result["error"],
        }

    if cache is not None:
        cache.set(key, result["text"])
    return {"success": True, "text": result["text"], "cached": False, "stats": stats, "error": None}
