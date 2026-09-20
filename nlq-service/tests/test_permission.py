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

    def test_open_management_tables_are_public(self):
        # 节假日 / 开放时段 / 闭馆例外为全校公开只读信息，教师查询不注入权限约束
        for sql in (
            "SELECT * FROM holiday",
            "SELECT * FROM lab_open_time",
            "SELECT * FROM lab_open_exception",
        ):
            assert inject_permission(sql, "TEACHER", 5) == sql

    def test_open_tables_join_public_tables_still_allowed(self):
        # 开放时段关联实验室/学期查询（均为公开表）→ 不注入任何条件
        sql = (
            "SELECT o.day_of_week, l.lab_name FROM lab_open_time o "
            "JOIN laboratory l ON o.lab_code = l.lab_code"
        )
        assert inject_permission(sql, "TEACHER", 5) == sql

    def test_teacher_user_table_rejected(self):
        # user 表持有 real_name / phone / password，已从公开表白名单移出。
        # 人员档案通道对教师做了「仅本人」断言，这条旧通道必须同步收紧，
        # 否则同一权限边界可被 query_data 绕过。
        with pytest.raises(PermissionRejectError):
            inject_permission("SELECT real_name, phone FROM user", "TEACHER", 5)

    def test_admin_user_table_still_allowed(self):
        # 管理员不受行级权限约束，收紧仅针对教师
        sql = "SELECT real_name FROM user"
        assert inject_permission(sql, "ADMIN", 1) == sql

    def test_schedule_time_gets_schedule_join_and_condition(self):
        # schedule_time 无教师列，经 schedule 兜底：仅本人时间明细
        result = inject_permission("SELECT * FROM schedule_time", "TEACHER", 5)
        assert "JOIN schedule ON schedule_time.schedule_id = schedule.id" in result
        assert "schedule.teacher_id = 5" in result

    def test_schedule_time_with_existing_schedule_join_not_duplicated(self):
        sql = (
            "SELECT * FROM schedule_time st "
            "JOIN schedule s ON st.schedule_id = s.id"
        )
        result = inject_permission(sql, "TEACHER", 5)
        assert result.count("JOIN schedule") == 1
        assert "s.teacher_id = 5" in result

    def test_query_history_scoped_to_self(self):
        result = inject_permission("SELECT * FROM query_history", "TEACHER", 5)
        assert "query_history.user_id = 5" in result

    def test_operation_log_scoped_to_self(self):
        result = inject_permission("SELECT * FROM operation_log", "TEACHER", 5)
        assert "operation_log.user_id = 5" in result

    def test_condition_uses_table_alias(self):
        # 模型爱给表起别名，注入条件必须跟随别名，否则 MySQL 1054
        result = inject_permission("SELECT * FROM schedule s", "TEACHER", 5)
        assert "s.teacher_id = 5" in result
        assert "schedule.teacher_id" not in result

    def test_experiment_with_alias_still_joins_with_alias(self):
        result = inject_permission("SELECT * FROM experiment e", "TEACHER", 5)
        assert "JOIN course ON e.course_id = course.id" in result
        assert "course.teacher_id = 5" in result

    def test_placeholder_is_replaced_with_user_id(self):
        # 提示词历史遗留的 :current_user_id 占位符不得泄漏到 MySQL
        result = inject_permission(
            "SELECT * FROM course WHERE course.teacher_id = :current_user_id", "TEACHER", 5
        )
        assert ":current_user_id" not in result
        assert "course.teacher_id = 5" in result


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

    def test_inserts_before_order_by(self):
        # 模型常写「... ORDER BY x」结尾的无过滤查询，WHERE 拼到末尾会 1064
        result = add_where_conditions(
            "SELECT * FROM schedule_time ORDER BY day_of_week", ["schedule.teacher_id = 5"]
        )
        assert "WHERE schedule.teacher_id = 5 ORDER BY day_of_week" in result

    def test_inserts_before_limit(self):
        result = add_where_conditions(
            "SELECT * FROM query_history LIMIT 20", ["query_history.user_id = 5"]
        )
        assert "WHERE query_history.user_id = 5 LIMIT 20" in result

    def test_inserts_before_group_by(self):
        result = add_where_conditions(
            "SELECT lab_code FROM schedule GROUP BY lab_code", ["schedule.teacher_id = 5"]
        )
        assert "WHERE schedule.teacher_id = 5 GROUP BY lab_code" in result

    def test_existing_where_before_order_by_not_glued(self):
        # 回归：已有 WHERE + 尾部 ORDER BY 时，AND 条件须与 ORDER BY 留空格，
        # 否则生成 "... teacher_id = 5ORDER BY ..." 触发 1064
        result = add_where_conditions(
            "SELECT * FROM course WHERE status = 1 ORDER BY created_at",
            ["course.teacher_id = 5"],
        )
        assert "AND course.teacher_id = 5 ORDER BY created_at" in result
        assert "= 5ORDER" not in result

    def test_existing_where_before_group_by_not_glued(self):
        result = add_where_conditions(
            "SELECT * FROM course WHERE total_hours = 32 GROUP BY teacher_id",
            ["course.teacher_id = 5"],
        )
        assert "AND course.teacher_id = 5 GROUP BY teacher_id" in result
        assert "= 5GROUP" not in result

    def test_lab_project_direct_injection(self):
        result = inject_permission("SELECT * FROM lab_project", "TEACHER", 5)
        assert "lab_project.teacher_id = 5" in result

    def test_lab_project_task_backfill_join(self):
        # 任务表无 teacher_id，须补 JOIN lab_project 兜底
        result = inject_permission(
            "SELECT * FROM lab_project_task WHERE status = 1", "TEACHER", 5
        )
        assert "JOIN lab_project" in result
        assert "lab_project.teacher_id = 5" in result

    def test_lab_task_submission_rejected(self):
        # 二跳表一期不入白名单，教师查询应被安全拒绝
        with pytest.raises(PermissionRejectError):
            inject_permission("SELECT * FROM lab_task_submission", "TEACHER", 5)

    def test_student_table_rejected(self):
        # 规则②：student 一期不入白名单
        with pytest.raises(PermissionRejectError):
            inject_permission("SELECT * FROM student", "TEACHER", 5)

    def test_subquery_order_by_is_not_mistaken_for_tail(self):
        sql = "SELECT (SELECT id FROM schedule ORDER BY id LIMIT 1) AS x FROM course"
        result = add_where_conditions(sql, ["course.teacher_id = 5"])
        assert result.endswith("WHERE course.teacher_id = 5")
