"""实验台数字档案测试（T3.2）。

DB 与 LLM 均为系统边界（monkeypatch 隔离），重点锁定：
  - 聚合数据真实（stats 来自 db.execute 结果）
  - LLM 只做语言包装（数字来自聚合数据）
  - 缓存命中不重复调 LLM；实体不存在不调 LLM
"""

import json
import os
import tempfile

from app.services import archive_service
from app.services.archive_service import ArchiveCache, generate_archive


def _tmp_cache() -> ArchiveCache:
    return ArchiveCache(os.path.join(tempfile.mkdtemp(), "archive_cache.db"))


# ---- 实验项目档案聚合假数据（按 SQL 特征分派，DB 为系统边界） ----

_EXP_ROW = {
    "id": 2, "course_id": 7, "experiment_code": "CS101-20261-001", "experiment_name": "示波器使用",
    "category": "验证型", "type": "必做", "subject": "物理学", "hours": 2, "group_size": 2,
    "requirement": "必修", "created_at": "2026-09-01 00:00:00",
    "course_code": "CS101", "course_name": "大学物理实验", "semester_name": "2026-2027-1",
}
_CATALOG_ROWS = [
    {"id": 1, "experiment_code": "CS101-20261-000", "experiment_name": "长度测量",
     "category": "验证型", "requirement": "必修", "group_size": 2, "hours": 2},
    {"id": 2, "experiment_code": "CS101-20261-001", "experiment_name": "示波器使用",
     "category": "验证型", "requirement": "必修", "group_size": 2, "hours": 2},
    {"id": 3, "experiment_code": "CS101-20261-002", "experiment_name": "牛顿环",
     "category": "综合型", "requirement": "选修", "group_size": 4, "hours": 3},
]
_CONTAINER_ROW = {
    "id": 14, "project_code": "LP-2026-002", "project_name": "大学物理实验",
    "description": None, "status": "ACTIVE", "created_at": "2026-09-18 10:00:00",
}
_TASK_STATUS_ROWS = [
    {"status": "ACCEPTED", "cnt": 3},
    {"status": "IN_PROGRESS", "cnt": 2},
    {"status": "CLOSED", "cnt": 1},
]
_GROUPS_OVER_LIMIT = [
    {"id": 1, "group_name": "第1组", "baseline_size": 2, "member_count": 3},
    {"id": 2, "group_name": "第2组", "baseline_size": 2, "member_count": 3},
]
_GROUPS_CONSISTENT = [
    {"id": 1, "group_name": "第1组", "baseline_size": 2, "member_count": 2},
    {"id": 2, "group_name": "第2组", "baseline_size": 2, "member_count": 2},
    {"id": 3, "group_name": "第3组", "baseline_size": 2, "member_count": 2},
]


def _experiment_execute(group_rows=None, per_experiment=None, container=_CONTAINER_ROW):
    """按 SQL 特征分派实验项目档案的全部聚合查询"""
    async def execute(sql, params=()):
        if "JOIN course c" in sql:
            return [dict(_EXP_ROW)]
        if "FROM schedule sc" in sql:
            return [{"schedule_count": 4, "student_total": 96}]
        if "FROM lab_project WHERE course_id" in sql:
            return [dict(container)] if container else []
        if "GROUP BY e.id" in sql:
            return per_experiment if per_experiment is not None else []
        if "GROUP BY g.id" in sql:
            return [dict(row) for row in (group_rows if group_rows is not None else [])]
        if "FROM experiment WHERE course_id" in sql:
            return [dict(row) for row in _CATALOG_ROWS]
        if "AS cnt FROM lab_project_task" in sql:
            return [dict(row) for row in _TASK_STATUS_ROWS]
        if "overdue_count FROM lab_project_task" in sql:
            return [{"overdue_count": 1}]
        if "assigned_student_count FROM lab_project_task" in sql:
            return [{"assigned_student_count": 4}]
        if "FROM lab_task_submission" in sql:
            return [{"submission_count": 5, "pending_review_count": 2}]
        if "FROM lab_dataset" in sql:
            return [{"dataset_count": 1, "version_count": 3, "latest_version_max": 3}]
        if "FROM lab_project_member" in sql:
            return [{"member_count": 6}]
        raise AssertionError(f"未预期的 SQL：{sql}")

    return execute


class TestCollectStats:
    async def test_lab_not_found_raises(self, monkeypatch):
        async def execute(sql, params=()):
            return []

        monkeypatch.setattr(archive_service.db, "execute", execute)

        result = await generate_archive("laboratory", 999)

        assert result["success"] is False
        assert "实验室不存在" in result["error"]

    async def test_unsupported_entity_type(self, monkeypatch):
        result = await generate_archive("teacher", 1, cache=_tmp_cache())

        assert result["success"] is False
        assert "不支持的档案类型" in result["error"]


class TestGenerateArchive:
    async def test_lab_archive_generated_with_real_stats(self, monkeypatch):
        async def execute(sql, params=()):
            if "FROM laboratory" in sql:
                assert params == (1,)
                return [{"id": 1, "lab_code": "36-401", "lab_name": "物理实验室",
                         "manager": "张三", "device_count": 60, "status": 1, "created_at": "2024-09-01 00:00:00"}]
            # 按学期使用统计
            return [{"semester_name": "2026-2027-1", "schedule_count": 8,
                     "total_hours": 128, "student_total": 240, "course_count": 3}]

        monkeypatch.setattr(archive_service.db, "execute", execute)

        captured = {}

        async def fake_generate(prompt):
            captured["prompt"] = prompt
            return {"success": True, "text": "档案文本", "error": None}

        monkeypatch.setattr(archive_service, "generate_text", fake_generate)

        result = await generate_archive("laboratory", 1, cache=_tmp_cache())

        assert result["success"] is True
        assert result["text"] == "档案文本"
        # 统计数据进入 prompt，LLM 的数字来源唯一
        assert "36-401" in captured["prompt"]
        assert "不得编造" in captured["prompt"]

    async def test_cache_hit_skips_llm(self, monkeypatch):
        async def execute(sql, params=()):
            if "FROM laboratory" in sql:
                return [{"id": 1, "lab_code": "36-401", "lab_name": "物理实验室",
                         "manager": "张三", "device_count": 60, "status": 1, "created_at": "2024-09-01 00:00:00"}]
            return [{"semester_name": "2026-2027-1", "schedule_count": 8,
                     "total_hours": 128, "student_total": 240, "course_count": 3}]

        monkeypatch.setattr(archive_service.db, "execute", execute)

        llm_calls = []

        async def fake_generate(prompt):
            llm_calls.append(prompt)
            return {"success": True, "text": "档案文本", "error": None}

        monkeypatch.setattr(archive_service, "generate_text", fake_generate)

        cache = _tmp_cache()
        first = await generate_archive("laboratory", 1, cache=cache)
        second = await generate_archive("laboratory", 1, cache=cache)

        assert first["cached"] is False
        assert second["cached"] is True
        assert second["text"] == first["text"]
        assert len(llm_calls) == 1  # 第二次走缓存，LLM 只调一次

    async def test_experiment_archive_covers_whole_project(self, monkeypatch):
        """实验档案聚合扩为课题全貌：目录/成员/任务/材料/分组六节齐备，派生值 Python 侧算好"""
        monkeypatch.setattr(archive_service.db, "execute", _experiment_execute(group_rows=_GROUPS_OVER_LIMIT))

        captured = {}

        async def fake_generate(prompt):
            captured["prompt"] = prompt
            return {"success": True, "text": "档案文本", "error": None}

        monkeypatch.setattr(archive_service, "generate_text", fake_generate)

        result = await generate_archive("experiment", 2, cache=_tmp_cache())

        assert result["success"] is True
        stats = result["stats"]
        # 原有扁平字段与排课统计保持兼容
        assert stats["course_code"] == "CS101"
        assert stats["usage"]["schedule_count"] == 4
        # 所属课题容器
        assert stats["project"]["id"] == 14
        assert stats["members"]["member_count"] == 6
        # 任务：完成度 = ACCEPTED /(总数 − CLOSED) = 3/5
        assert stats["tasks"]["total"] == 6
        assert stats["tasks"]["effective_excluding_closed"] == 5
        assert stats["tasks"]["completion_rate"] == 60.0
        assert stats["tasks"]["overdue"] == 1
        assert stats["tasks"]["unassigned_student_count"] == 2
        assert stats["materials"]["pending_review_count"] == 2
        # 分组：6 人 / 每组 2 人 → 应分 3 组，实际 2 组且超员 → 不一致
        assert stats["grouping"]["expected_group_count"] == 3
        assert stats["grouping"]["actual_group_count"] == 2
        assert stats["grouping"]["consistent"] is False
        # 实验目录全景
        assert stats["catalog"]["experiment_count"] == 3
        assert stats["catalog"]["position"] == 2
        assert stats["catalog"]["group_size_min"] == 2
        assert stats["catalog"]["group_size_max"] == 4
        # 六节结构与「不编数字」红线进入 prompt
        assert "六、风险提示" in captured["prompt"]
        assert "不得编造" in captured["prompt"]

    async def test_experiment_archive_without_container_degrades_to_none(self, monkeypatch):
        """容器课题未懒生成：课题各节显式为 None（区别于 0），不影响实验本体"""
        monkeypatch.setattr(archive_service.db, "execute", _experiment_execute(container=None))

        async def fake_generate(prompt):
            return {"success": True, "text": "档案文本", "error": None}

        monkeypatch.setattr(archive_service, "generate_text", fake_generate)

        result = await generate_archive("experiment", 2, cache=_tmp_cache())

        assert result["success"] is True
        stats = result["stats"]
        assert stats["experiment_code"] == "CS101-20261-001"
        for section in ("project", "members", "grouping", "tasks", "materials"):
            assert stats[section] is None

    async def test_experiment_archive_grouping_consistent(self, monkeypatch):
        """3 组各 2 人、全员覆盖、基准与目录相同 → consistent 为 True"""
        monkeypatch.setattr(archive_service.db, "execute", _experiment_execute(group_rows=_GROUPS_CONSISTENT))

        async def fake_generate(prompt):
            return {"success": True, "text": "档案文本", "error": None}

        monkeypatch.setattr(archive_service, "generate_text", fake_generate)

        result = await generate_archive("experiment", 2, cache=_tmp_cache())

        grouping = result["stats"]["grouping"]
        assert grouping["consistent"] is True
        assert grouping["ungrouped_student_count"] == 0
        assert grouping["expected_group_count"] == grouping["actual_group_count"] == 3


class TestCacheSerialization:
    def test_cache_roundtrip(self):
        cache = _tmp_cache()
        cache.set("laboratory:1:abc", "文本A")

        assert cache.get("laboratory:1:abc") == "文本A"
        assert cache.get("laboratory:2:abc") is None

    def test_stats_json_serializable(self):
        """_default_value 输出必须 JSON 可序列化（Decimal/datetime 兜底）。"""
        from datetime import datetime
        from decimal import Decimal

        stats = {"a": Decimal("1.5"), "b": datetime(2026, 9, 9, 12, 0, 0), "c": None}
        clean = {k: archive_service._default_value(v) for k, v in stats.items()}
        json.dumps(clean)  # 不抛异常即通过
