"""MCP 只读查询层测试（T3.1）。

系统边界（DB）用 fake executor 隔离，重点锁定：
  - 固定只读 SQL + 参数化过滤（无自由文本拼接）
  - 行数上限受 mcp_max_rows 约束
  - 学期解析回退链（当前 → 最近结束 → 最早未来）
  - 序列化安全（Decimal/datetime → JSON 可序列化）
"""

from decimal import Decimal

from mcp_server import queries
from app.config import settings


def _fake_db(rows_by_call=None, capture=None):
    """构造 monkeypatch 用的 db.execute 替身；按调用次序返回 rows_by_call 列表。"""

    async def execute(sql, params=()):
        if capture is not None:
            capture.append((sql, params))
        if rows_by_call:
            return rows_by_call.pop(0)
        return []

    return execute


class TestSemesterResolution:
    async def test_fallback_chain_active_then_ended(self, monkeypatch):
        calls = []

        async def execute(sql, params=()):
            calls.append(sql)
            if "CURDATE()" in sql and "start_date <= CURDATE()" in sql:
                return []  # 无进行中学期
            if "end_date < CURDATE()" in sql:
                return [{"id": 3, "semester_name": "2025-2026-2", "academic_year": "2025-2026", "semester_num": 2}]
            return []

        monkeypatch.setattr(queries.db, "execute", execute)

        semester = await queries.resolve_semester()

        assert semester["semester_name"] == "2025-2026-2"

    async def test_explicit_name_lookup(self, monkeypatch):
        async def execute(sql, params=()):
            assert "semester_name = %s" in sql
            assert params == ("2026-2027-1",)
            return [{"id": 5, "semester_name": "2026-2027-1", "academic_year": "2026-2027", "semester_num": 1}]

        monkeypatch.setattr(queries.db, "execute", execute)

        semester = await queries.resolve_semester("2026-2027-1")

        assert semester["id"] == 5


class TestQuerySchedules:
    async def test_lab_code_filter_and_semester_default(self, monkeypatch):
        seen = {"semester_sqls": 0}

        async def execute(sql, params=()):
            if "FROM semester" in sql:
                seen["semester_sqls"] += 1
                return [{"id": 7, "semester_name": "2026-2027-1", "academic_year": "2026-2027", "semester_num": 1}]
            assert "lab_code = %s" in sql
            assert "sc.semester_id = %s" in sql
            assert params[0] == 7
            assert params[1] == "36-401"
            return [
                {"id": 1, "course_code": "CS101", "course_name": "大学物理实验", "total_hours": Decimal("16.0"),
                 "teacher_name": "张三", "lab_code": "36-401", "semester_name": "2026-2027-1"}
            ]

        monkeypatch.setattr(queries.db, "execute", execute)

        result = await queries.query_schedules(lab_code="36-401")

        assert result["success"] is True
        assert result["count"] == 1
        # Decimal 已序列化为 float，JSON 可安全传输
        assert result["rows"][0]["total_hours"] == 16.0

    async def test_unknown_semester_name_rejected(self, monkeypatch):
        async def execute(sql, params=()):
            return []  # 学期按名查询无结果

        monkeypatch.setattr(queries.db, "execute", execute)

        result = await queries.query_schedules(semester_name="2099-2100-1")

        assert result["success"] is False
        assert "学期不存在" in result["error"]

    async def test_limit_capped_by_max_rows(self, monkeypatch):
        captured = []

        async def execute(sql, params=()):
            if "FROM semester" in sql:
                return [{"id": 7, "semester_name": "2026-2027-1", "academic_year": "2026-2027", "semester_num": 1}]
            captured.append(params)
            return []

        monkeypatch.setattr(queries.db, "execute", execute)

        await queries.query_schedules(lab_code="36-401", limit=99999)

        assert captured[0][-1] == settings.mcp_max_rows


class TestQueryLaboratories:
    async def test_invalid_status_rejected(self, monkeypatch):
        executed = []

        async def execute(sql, params=()):
            executed.append(sql)
            return []

        monkeypatch.setattr(queries.db, "execute", execute)

        result = await queries.query_laboratories(status=2)

        assert result["success"] is False
        assert "status" in result["error"]
        assert executed == []  # 非法参数不触库

    async def test_all_readonly_selects(self, monkeypatch):
        executed = []

        async def execute(sql, params=()):
            executed.append(sql)
            return []

        monkeypatch.setattr(queries.db, "execute", execute)

        await queries.query_laboratories(lab_code="36-401")

        assert executed[0].strip().upper().startswith("SELECT")
        assert "INSERT" not in executed[0].upper()
