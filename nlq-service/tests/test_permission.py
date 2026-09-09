"""permission.py 行为特征化测试 —— 锁住数据权限注入的既有行为。

这些测试面向公共接口 `inject_permission` / `extract_tables` / `add_where_conditions`，
描述「什么该发生」，不关心内部实现。阶段 1 重构时它们必须保持全绿。
"""

import pytest

from app.services.permission import (
    PermissionRejectError,
    add_where_conditions,
    extract_tables,
    inject_permission,
)


class TestInjectPermission:
    def test_admin_unchanged(self):
        sql = "SELECT * FROM course"
        assert inject_permission(sql, "ADMIN", 1) == sql

    def test_teacher_course_injects_teacher_id(self):
        result = inject_permission("SELECT * FROM course", "TEACHER", 5)
        assert "course.teacher_id = 5" in result

    def test_teacher_schedule_injects_teacher_id(self):
        result = inject_permission("SELECT * FROM schedule", "TEACHER", 5)
        assert "schedule.teacher_id = 5" in result

    def test_public_table_not_injected(self):
        # 实验室/学期/字典是公开表，教师查询不应注入权限约束
        sql = "SELECT * FROM laboratory"
        assert inject_permission(sql, "TEACHER", 5) == sql

    def test_experiment_falls_back_to_course_join(self):
        # experiment 无 teacher_id，须通过 course 兜底，并自动注入 JOIN
        result = inject_permission("SELECT * FROM experiment", "TEACHER", 5)
        assert "JOIN course" in result
        assert "course.teacher_id = 5" in result

    def test_unknown_table_rejected(self):
        # 无法建立权限约束的受控表必须拒绝执行，不放行零权限查询
        with pytest.raises(PermissionRejectError):
            inject_permission("SELECT * FROM unknown_table", "TEACHER", 5)

    def test_teacher_only_public_tables_allowed(self):
        # 只查公开表，不涉及受控表 → 允许执行，返回原 SQL
        sql = "SELECT * FROM semester"
        assert inject_permission(sql, "TEACHER", 5) == sql


class TestExtractTables:
    def test_extracts_from_and_join(self):
        tables = extract_tables(
            "SELECT * FROM course c JOIN semester s ON c.semester_id = s.id"
        )
        assert tables == {"course", "semester"}

    def test_strips_backticks_and_schema_prefix(self):
        tables = extract_tables("SELECT * FROM `course`")
        assert tables == {"course"}


class TestAddWhereConditions:
    def test_appends_where_when_absent(self):
        result = add_where_conditions("SELECT * FROM course", ["course.teacher_id = 5"])
        assert result.endswith("WHERE course.teacher_id = 5")

    def test_appends_and_when_where_present(self):
        result = add_where_conditions(
            "SELECT * FROM course WHERE status = 1", ["course.teacher_id = 5"]
        )
        assert "status = 1" in result
        assert "AND course.teacher_id = 5" in result
