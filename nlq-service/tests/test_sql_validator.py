"""sql_validator.py 行为特征化测试 —— 锁住 SQL 安全校验的既有行为。

面向公共接口 validate_sql / extract_sql，描述「什么样的 SQL 该被放行/拦截」。
"""

from app.services.sql_validator import extract_sql, validate_sql


class TestValidateSql:
    def test_accepts_select(self):
        ok, sql = validate_sql("SELECT * FROM course")
        assert ok is True
        assert "SELECT" in sql

    def test_rejects_empty(self):
        ok, msg = validate_sql("")
        assert ok is False
        assert "空" in msg

    def test_rejects_non_select(self):
        ok, msg = validate_sql("DELETE FROM course")
        assert ok is False
        assert "仅支持 SELECT" in msg

    def test_rejects_comment(self):
        ok, msg = validate_sql("SELECT * FROM course -- 注释")
        assert ok is False
        assert "注释" in msg

    def test_rejects_dangerous_function(self):
        ok, msg = validate_sql("SELECT load_file('/etc/passwd')")
        assert ok is False
        assert "load_file" in msg

    def test_strips_markdown_fence(self):
        ok, sql = validate_sql("```sql\nSELECT 1\n```")
        assert ok is True
        assert "```" not in sql

    def test_rejects_password_column(self):
        # PII 黑名单：密码哈希永不允许进入查询通道，避免随结果回喂给大模型
        ok, msg = validate_sql("SELECT username, password FROM user")
        assert ok is False
        assert "password" in msg

    def test_rejects_star_on_user_table(self):
        # 星号展开会连带带出敏感列
        ok, msg = validate_sql("SELECT * FROM user WHERE id = 1")
        assert ok is False
        assert "*" in msg or "敏感" in msg

    def test_rejects_qualified_star_on_user_table(self):
        # u.* 同样是星号展开
        ok, _ = validate_sql("SELECT u.* FROM user u WHERE u.id = 1")
        assert ok is False

    def test_allows_count_star_on_user_table(self):
        # count(*) 是聚合，不展开列、不泄露敏感字段（此前被误杀，「有多少名教师」永远无法执行）
        ok, _ = validate_sql("SELECT COUNT(*) AS 教师数 FROM user WHERE role = 'TEACHER'")
        assert ok is True

    def test_allows_sum_multiplication_on_user_table(self):
        # SUM(a * b) 中的乘号曾被当成投影星号误杀「按教师统计实验人时数」
        sql = (
            "SELECT u.real_name AS 教师姓名, SUM(e.hours * s.student_count) AS 实验人时数"
            " FROM schedule s JOIN user u ON s.teacher_id = u.id"
            " JOIN course c ON s.course_code = c.course_code"
            " JOIN experiment e ON e.course_id = c.id"
            " GROUP BY u.id, u.real_name ORDER BY 实验人时数 DESC"
        )
        ok, msg = validate_sql(sql)
        assert ok is True, msg

    def test_allows_subquery_count_only_on_other_tables(self):
        # 子查询里的 COUNT(*) 不应触发星号判定
        ok, _ = validate_sql(
            "SELECT (SELECT COUNT(*) FROM user) AS n, c.id FROM course c"
        )
        assert ok is True

    def test_rejects_star_after_subquery(self):
        # 投影里既有子查询又有真正的 * 时仍要拒绝
        ok, _ = validate_sql("SELECT (SELECT COUNT(*) FROM user) AS n, * FROM course")
        assert ok is False

    def test_rejects_star_in_subquery_on_user_table(self):
        ok, _ = validate_sql("SELECT c.id FROM (SELECT * FROM user) AS x JOIN course c ON 1=1")
        assert ok is False

    def test_star_on_other_tables_still_allowed(self):
        ok, _ = validate_sql("SELECT * FROM laboratory")
        assert ok is True

    def test_allows_explicit_columns_on_user_table(self):
        ok, _ = validate_sql("SELECT username, real_name, department FROM user")
        assert ok is True


class TestExtractSql:
    def test_strips_markdown_fence(self):
        assert extract_sql("```sql\nSELECT 1\n```") == "SELECT 1"

    def test_plain_sql_unchanged(self):
        assert extract_sql("SELECT 1") == "SELECT 1"
