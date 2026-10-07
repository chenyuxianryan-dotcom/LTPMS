"""实验项目全项目报告测试（T07 聚合扩展 + T08 生成管线）。

DB / LLM / 文件画像均为系统边界（monkeypatch 隔离）。锁定：
  - 课题六节口径聚合真实，派生值 Python 侧算好；
  - 独立课题（无课程）目录/排课/分组节显式 None；
  - 隐私红线：学号/姓名不进 stats（成员只给计数）；
  - 缓存命中 / force 绕过 / 数据集版本指纹失效；
  - 模型不可用降级返回确定性 stats。
"""

import json
import os
import tempfile

import pytest

from app.services import report_service
from app.services.archive_service import ArchiveCache
from app.services.report_service import collect_project_stats, generate_report


def _tmp_cache() -> ArchiveCache:
    return ArchiveCache(os.path.join(tempfile.mkdtemp(), "report_cache.db"))


_CONTAINER_PROJECT = {
    "id": 14, "project_code": "LP-2026-002", "project_name": "大学物理实验",
    "description": "物理实验课题", "status": "ACTIVE", "source": "SYSTEM",
    "course_id": 10, "semester_id": 3, "created_at": "2026-09-18 10:00:00",
}
_INDEPENDENT_PROJECT = {
    "id": 63, "project_code": "LP-2026-099", "project_name": "独立课题",
    "description": None, "status": "ACTIVE", "source": "USER",
    "course_id": None, "semester_id": None, "created_at": "2026-09-20 10:00:00",
}
_COURSE_ROW = {"id": 10, "course_code": "CS101", "course_name": "大学物理实验", "semester_name": "2026-2027-1"}
_CATALOG_ROWS = [
    {"id": 1, "experiment_code": "CS101-001", "experiment_name": "长度测量",
     "category": "验证型", "requirement": "必修", "group_size": 2, "hours": 2},
    {"id": 2, "experiment_code": "CS101-002", "experiment_name": "示波器",
     "category": "验证型", "requirement": "必修", "group_size": 2, "hours": 2},
]
_TASK_STATUS = [{"status": "ACCEPTED", "cnt": 3}, {"status": "IN_PROGRESS", "cnt": 2}, {"status": "CLOSED", "cnt": 1}]
_SUBMISSIONS = {"submission_count": 5, "pending_review_count": 2,
                "accepted_submission_count": 3, "rejected_submission_count": 0}
_DATASETS = [{"id": 9, "dataset_name": "日志样本数据", "description": "样本", "latest_version": 1}]
_VERSIONS = {9: {"id": 13, "version_no": 1, "stored_path": "fixture_lab_log_v1.csv",
                 "original_filename": "lab_log_20260918.csv", "file_type": "csv", "file_size": 408}}
_GROUPING = [
    {"id": 1, "experiment_code": "CS101-001", "experiment_name": "长度测量",
     "group_size": 2, "group_count": 1, "grouped_student_count": 2},
    {"id": 2, "experiment_code": "CS101-002", "experiment_name": "示波器",
     "group_size": 2, "group_count": 0, "grouped_student_count": 0},
]
_USAGE = {"schedule_count": 4, "student_total": 96}


def _execute(project_row, dataset_rows=None, version_rows=None, member_count=6,
             task_status=None, catalog_rows=None, grouping_rows=None, course_row=None,
             usage_row=None):
    dataset_rows = _DATASETS if dataset_rows is None else dataset_rows
    version_rows = _VERSIONS if version_rows is None else version_rows
    task_status = _TASK_STATUS if task_status is None else task_status
    catalog_rows = _CATALOG_ROWS if catalog_rows is None else catalog_rows
    grouping_rows = _GROUPING if grouping_rows is None else grouping_rows
    course_row = _COURSE_ROW if course_row is None else course_row
    usage_row = _USAGE if usage_row is None else usage_row

    async def execute(sql, params=()):
        if "FROM lab_project WHERE id" in sql:
            return [dict(project_row)] if project_row else []
        if "FROM course c JOIN semester" in sql:
            return [dict(course_row)] if course_row else []
        if "FROM lab_project_member" in sql:
            return [{"member_count": member_count}]
        if "AS cnt FROM lab_project_task" in sql:
            return [dict(r) for r in task_status]
        if "overdue_count FROM lab_project_task" in sql:
            return [{"overdue_count": 1}]
        if "assigned_student_count FROM lab_project_task" in sql:
            return [{"assigned_student_count": 4}]
        if "FROM lab_task_submission" in sql:
            return [dict(_SUBMISSIONS)]
        if "FROM lab_dataset WHERE project_id" in sql:
            return [dict(r) for r in dataset_rows]
        if "FROM lab_dataset_version" in sql:
            ds_id = params[0]
            return [dict(version_rows[ds_id])] if ds_id in version_rows else []
        if "FROM experiment WHERE course_id" in sql:
            return [dict(r) for r in catalog_rows]
        if "FROM schedule sc" in sql:
            return [dict(usage_row)] if usage_row else []
        if "GROUP BY e.id" in sql:
            return [dict(r) for r in grouping_rows]
        raise AssertionError(f"未预期的 SQL：{sql}")

    return execute


@pytest.fixture
def fake_profile(monkeypatch):
    monkeypatch.setattr(report_service, "profile_dataset_file",
                        lambda *a, **k: {"profiled": True, "kind": "csv", "row_count": 6,
                                         "column_count": 4, "fields": ["ts", "level", "module", "message"],
                                         "numeric_columns": []})


class TestCollectProjectStats:
    async def test_container_project_six_sections(self, monkeypatch, fake_profile):
        monkeypatch.setattr(report_service.db, "execute", _execute(_CONTAINER_PROJECT))
        stats = await collect_project_stats(14)

        assert stats["project"]["project_code"] == "LP-2026-002"
        assert stats["course"]["course_code"] == "CS101"
        assert stats["members"] == {"member_count": 6}
        # 完成度 = ACCEPTED /(总数 − CLOSED) = 3/5 = 60%
        assert stats["tasks"]["total"] == 6
        assert stats["tasks"]["effective_excluding_closed"] == 5
        assert stats["tasks"]["completion_rate"] == 60.0
        assert stats["tasks"]["overdue"] == 1
        assert stats["tasks"]["unassigned_student_count"] == 2
        assert stats["materials"]["pending_review_count"] == 2
        assert stats["catalog"]["experiment_count"] == 2
        assert stats["usage"]["schedule_count"] == 4
        assert stats["grouping"]["grouped_student_count"] == 2
        assert stats["grouping"]["ungrouped_student_count"] == 4
        # 数据集清单 + 画像 + 版本指纹
        assert stats["materials"]["dataset_count"] == 1
        assert stats["datasets"][0]["profile"]["profiled"] is True
        assert stats["dataset_fingerprint"] == ["9:v1"]

    async def test_independent_project_degrades_none(self, monkeypatch, fake_profile):
        monkeypatch.setattr(report_service.db, "execute", _execute(_INDEPENDENT_PROJECT))
        stats = await collect_project_stats(63)

        assert stats["project"]["source"] == "USER"
        for section in ("course", "catalog", "usage", "grouping"):
            assert stats[section] is None
        # 任务 / 材料 / 数据集仍聚合
        assert stats["tasks"]["total"] == 6
        assert stats["datasets"][0]["dataset_name"] == "日志样本数据"

    async def test_dataset_without_version_no_error(self, monkeypatch, fake_profile):
        monkeypatch.setattr(report_service.db, "execute",
                            _execute(_CONTAINER_PROJECT, version_rows={}))
        stats = await collect_project_stats(14)
        assert stats["datasets"][0]["profile"]["profiled"] is False
        assert "暂无文件版本" in stats["datasets"][0]["profile"]["reason"]

    async def test_privacy_no_student_names_in_stats(self, monkeypatch, fake_profile):
        """隐私红线：stats 序列化后不得出现学号/姓名字段（成员只给计数）。"""
        monkeypatch.setattr(report_service.db, "execute", _execute(_CONTAINER_PROJECT))
        stats = await collect_project_stats(14)
        blob = json.dumps(stats, ensure_ascii=False)
        for forbidden in ("student_name", "student_no", "uploader", "assignee_name",
                          "created_by_name", "严春", "member_name"):
            assert forbidden not in blob
        # 成员节只给计数，不含任何人员标识
        assert set(stats["members"].keys()) == {"member_count"}


class TestGenerateReport:
    async def test_not_found_returns_error(self, monkeypatch, fake_profile):
        monkeypatch.setattr(report_service.db, "execute", _execute(None))
        result = await generate_report(999, cache=_tmp_cache())
        assert result["success"] is False
        assert "不存在" in result["error"]

    async def test_six_section_prompt_and_numbers(self, monkeypatch, fake_profile):
        monkeypatch.setattr(report_service.db, "execute", _execute(_CONTAINER_PROJECT))
        captured = {}

        async def fake_generate(prompt, timeout=None):
            captured["prompt"] = prompt
            captured["timeout"] = timeout
            return {"success": True, "text": "报告正文", "error": None}

        monkeypatch.setattr(report_service, "generate_text", fake_generate)
        result = await generate_report(14, cache=_tmp_cache())

        assert result["success"] is True
        assert result["text"] == "报告正文"
        assert result["generated_at"] is not None
        # 六节模板 + 红线进入 prompt；数字来自聚合
        assert "四、材料与数据画像" in captured["prompt"]
        assert "六、总结与建议" in captured["prompt"]
        assert "不得编造" in captured["prompt"]
        assert "60.0" in captured["prompt"]  # 完成度已算好写入
        # 报告独立超时（非全局 15s）
        assert captured["timeout"] and captured["timeout"] > 15

    async def test_cache_hit_skips_llm(self, monkeypatch, fake_profile):
        monkeypatch.setattr(report_service.db, "execute", _execute(_CONTAINER_PROJECT))
        calls = []

        async def fake_generate(prompt, timeout=None):
            calls.append(prompt)
            return {"success": True, "text": "报告正文", "error": None}

        monkeypatch.setattr(report_service, "generate_text", fake_generate)
        cache = _tmp_cache()
        first = await generate_report(14, cache=cache)
        second = await generate_report(14, cache=cache)

        assert first["cached"] is False
        assert second["cached"] is True
        assert second["text"] == first["text"]
        assert len(calls) == 1

    async def test_force_bypasses_cache(self, monkeypatch, fake_profile):
        monkeypatch.setattr(report_service.db, "execute", _execute(_CONTAINER_PROJECT))
        calls = []

        async def fake_generate(prompt, timeout=None):
            calls.append(prompt)
            return {"success": True, "text": f"报告{len(calls)}", "error": None}

        monkeypatch.setattr(report_service, "generate_text", fake_generate)
        cache = _tmp_cache()
        await generate_report(14, cache=cache)
        forced = await generate_report(14, force=True, cache=cache)

        assert forced["cached"] is False
        assert len(calls) == 2

    async def test_new_dataset_version_invalidates_cache(self, monkeypatch, fake_profile):
        """AC④：上传新版本 → 版本指纹变 → 缓存自动失效（无需 force）。"""
        calls = []

        async def fake_generate(prompt, timeout=None):
            calls.append(prompt)
            return {"success": True, "text": "报告正文", "error": None}

        monkeypatch.setattr(report_service, "generate_text", fake_generate)
        cache = _tmp_cache()

        monkeypatch.setattr(report_service.db, "execute", _execute(_CONTAINER_PROJECT))
        first = await generate_report(14, cache=cache)
        assert first["cached"] is False

        # 数据集升到 v2
        datasets_v2 = [{"id": 9, "dataset_name": "日志样本数据", "description": "样本", "latest_version": 2}]
        versions_v2 = {9: {"id": 14, "version_no": 2, "stored_path": "fixture_lab_log_v2.csv",
                           "original_filename": "lab_log_v2.csv", "file_type": "csv", "file_size": 500}}
        monkeypatch.setattr(report_service.db, "execute",
                            _execute(_CONTAINER_PROJECT, dataset_rows=datasets_v2, version_rows=versions_v2))
        second = await generate_report(14, cache=cache)

        assert second["cached"] is False  # 指纹变 → 未命中
        assert len(calls) == 2

    async def test_model_unavailable_degrades_with_stats(self, monkeypatch, fake_profile):
        """AC⑥：模型不可用 → success=False 但 stats 仍返回（前端降级不白屏）。"""
        monkeypatch.setattr(report_service.db, "execute", _execute(_CONTAINER_PROJECT))

        async def fake_generate(prompt, timeout=None):
            return {"success": False, "text": "", "error": "文本生成失败：超时"}

        monkeypatch.setattr(report_service, "generate_text", fake_generate)
        result = await generate_report(14, cache=_tmp_cache())

        assert result["success"] is False
        assert result["stats"] is not None
        assert result["stats"]["tasks"]["total"] == 6
