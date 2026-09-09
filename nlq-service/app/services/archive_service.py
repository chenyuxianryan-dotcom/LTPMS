"""实验台数字档案（T3.2）：AI 生成实验室/实验项目的「一句话简介 + 历史 + 使用统计」。

数据聚合（真实统计）→ 共享 generate_text 做语言包装 → SQLite 缓存（TTL 24h）。
红线：数字只能来自聚合数据，LLM 只做语言包装不编数字；无档案时前端降级显示统计。
"""

import hashlib
import json
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
    "SELECT e.id, e.experiment_code, e.experiment_name, e.category, e.type, e.subject, "
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


def _default_value(value: Any) -> Any:
    if value is None:
        return None
    if hasattr(value, "isoformat"):
        return value.isoformat(sep=" ") if hasattr(value, "hour") else value.isoformat()
    if hasattr(value, "as_tuple"):
        return float(value)
    return value


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
    "你是高校实验教学中心的数字档案管理员，负责为实验项目撰写有温度的数字档案。"
    "根据下面提供的实验项目真实数据，输出三部分，用小标题分隔：\n"
    "一句话简介（50字以内）/ 项目背景（100字左右）/ 使用统计小结（准确引用数据）。\n\n"
    "【严格要求：所有数字只能来自提供的数据，不得编造、推算或引入数据之外的数字】\n\n"
    "实验项目数据：\n{stats}"
)


def _cache_key(entity_type: str, entity_id: int, stats: dict) -> str:
    raw = json.dumps(stats, ensure_ascii=False, sort_keys=True, default=str)
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
    return f"{entity_type}:{entity_id}:{digest}"


def _stats_text(stats: dict) -> str:
    return json.dumps(stats, ensure_ascii=False, indent=2, default=str)


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
    """聚合实验项目档案数据：项目信息 + 所属课程 + 排课使用统计。找不到抛 ValueError。"""
    rows = await db.execute(_SQL_EXPERIMENT, (experiment_id,))
    if not rows:
        raise ValueError(f"实验项目不存在: id={experiment_id}")
    exp = rows[0]
    usage = await db.execute(_SQL_EXPERIMENT_USAGE, (exp["course_code"],))
    stats = {k: _default_value(v) for k, v in exp.items()}
    usage_row = usage[0] if usage else {}
    stats["usage"] = {
        k: _default_value(usage_row.get(k)) for k in ("schedule_count", "student_total")
    }
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
