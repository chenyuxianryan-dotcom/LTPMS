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

    async def test_experiment_archive_includes_course_context(self, monkeypatch):
        async def execute(sql, params=()):
            if "FROM experiment" in sql:
                return [{"id": 2, "experiment_code": "CS101-20261-001", "experiment_name": "示波器使用",
                         "category": "验证型", "type": "必做", "subject": "物理学", "hours": 2,
                         "group_size": 2, "requirement": "必修", "created_at": "2026-09-01 00:00:00",
                         "course_code": "CS101", "course_name": "大学物理实验",
                         "semester_name": "2026-2027-1"}]
            return [{"schedule_count": 4, "student_total": 96}]

        monkeypatch.setattr(archive_service.db, "execute", execute)

        cache = _tmp_cache()
        result = await generate_archive("experiment", 2, cache=cache)

        assert result["success"] is True
        assert result["stats"]["course_code"] == "CS101"
        assert result["stats"]["usage"]["schedule_count"] == 4


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
