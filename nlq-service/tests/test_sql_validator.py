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


class TestExtractSql:
    def test_strips_markdown_fence(self):
        assert extract_sql("```sql\nSELECT 1\n```") == "SELECT 1"

    def test_plain_sql_unchanged(self):
        assert extract_sql("SELECT 1") == "SELECT 1"
