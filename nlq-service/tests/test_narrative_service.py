"""叙事报告 / 教学总结测试（T3.3）。

重点锁定权限分层（红线）：TEACHER 查询强制带 teacher_id 过滤（只看本人），
ADMIN 全校口径；kind 归一规则；数字来自真实聚合。
"""

from decimal import Decimal

from app.services import narrative_service
from app.services.narrative_service import generate_narrative


class TestPermissionScoping:
    async def test_teacher_forced_personal_scope_and_kind(self, monkeypatch):
        captured = {"sqls": [], "prompts": []}

        async def execute(sql, params=()):
            captured["sqls"].append((sql, params))
            if "FROM semester" in sql:
                return [{"id": 7, "semester_name": "2026-2027-1", "academic_year": "2026-2027"}]
            return [{"n": 3, "c": 2, "student_total": 10}]

        async def fake_generate(prompt):
            captured["prompts"].append(prompt)
            return {"success": True, "text": "总结", "error": None}

        monkeypatch.setattr(narrative_service.db, "execute", execute)
        monkeypatch.setattr(narrative_service, "generate_text", fake_generate)

        result = await generate_narrative("annual_report", "TEACHER", 5)

        # kind 归一为个人教学总结 + scope=personal
        assert result["scope"] == "personal"
        assert "教学总结" in captured["prompts"][0]
        # 所有课程/排课/实验聚合 SQL 必须带 teacher_id 过滤（不放过零过滤）
        agg_calls = [(s, p) for s, p in captured["sqls"] if "FROM semester" not in s]
        assert agg_calls, "应有聚合查询"
        for sql, params in agg_calls:
            assert ("teacher_id = %s" in sql) or ("c.teacher_id = %s" in sql), f"教师口径缺权限过滤: {sql}"
            assert 5 in params

    async def test_admin_school_scope_no_teacher_filter(self, monkeypatch):
        captured = {"sqls": []}

        async def execute(sql, params=()):
            captured["sqls"].append(sql)
            if "FROM semester" in sql:
                return [{"id": 7, "semester_name": "2026-2027-1", "academic_year": "2026-2027"}]
            return [{"n": 10, "c": 5}]

        async def fake_generate(_prompt):
            return {"success": True, "text": "报告", "error": None}

        monkeypatch.setattr(narrative_service.db, "execute", execute)
        monkeypatch.setattr(narrative_service, "generate_text", fake_generate)

        result = await generate_narrative("annual_report", "ADMIN", 1)

        assert result["scope"] == "school"
        agg_sqls = [s for s in captured["sqls"] if "FROM semester" not in s]
        assert agg_sqls
        assert all("teacher_id = %s" not in s or "COUNT(DISTINCT teacher_id)" in s for s in agg_sqls)


class TestNumbersContract:
    async def test_llm_prompt_contains_real_numbers_only(self, monkeypatch):
        async def execute(sql, params=()):
            if "FROM semester" in sql:
                return [{"id": 7, "semester_name": "2026-2027-1", "academic_year": "2026-2027"}]
            if "person_hours" in sql or "student_count" in sql:
                return [{"n": Decimal("480.0")}]
            return [{"n": 6, "c": 4}]

        captured = {}

        async def fake_generate(prompt):
            captured["prompt"] = prompt
            return {"success": True, "text": "报告", "error": None}

        monkeypatch.setattr(narrative_service.db, "execute", execute)
        monkeypatch.setattr(narrative_service, "generate_text", fake_generate)

        result = await generate_narrative("annual_report", "ADMIN", 1)

        assert result["success"] is True
        assert "原样来自提供的数据" in captured["prompt"]
        assert "不得编造" in captured["prompt"]
        # 聚合数字随 prompt 传递
        assert "'semester_name': '2026-2027-1'" in captured["prompt"]

    async def test_no_active_semester_graceful(self, monkeypatch):
        async def execute(sql, params=()):
            return []  # 无任何学期

        async def fake_generate(prompt):
            return {"success": True, "text": "报告", "error": None}

        monkeypatch.setattr(narrative_service.db, "execute", execute)
        monkeypatch.setattr(narrative_service, "generate_text", fake_generate)

        result = await generate_narrative("teaching_summary", "ADMIN", 1)

        assert result["success"] is True
        assert result["numbers"]["semester_name"] is None

    async def test_llm_failure_returns_error_with_numbers(self, monkeypatch):
        async def execute(sql, params=()):
            if "FROM semester" in sql:
                return [{"id": 7, "semester_name": "2026-2027-1", "academic_year": "2026-2027"}]
            return [{"n": 1, "c": 1}]

        async def fake_generate(_prompt):
            return {"success": False, "text": "", "error": "文本生成失败：超时"}

        monkeypatch.setattr(narrative_service.db, "execute", execute)
        monkeypatch.setattr(narrative_service, "generate_text", fake_generate)

        result = await generate_narrative("teaching_summary", "ADMIN", 1)

        assert result["success"] is False
        assert "文本生成失败" in result["error"]
        # 聚合数字仍然返回（前端可降级展示统计）
        assert result["numbers"]["semester_name"] == "2026-2027-1"
