"""execute_query 行为测试 —— NL2SQL 全链路的公共执行入口。

面向公共接口 execute_query(question, role, user_id)，描述「一次查询如何编排：
build_messages → call_deepseek → extract/validate → inject_permission → ensure_limit → db.execute」。
LLM / 建库 schema / DB 均属系统边界，用 monkeypatch 隔离。
"""

from app.services import query_service
from app.services.permission import PermissionRejectError


async def _fake_build_messages(question, role):
    return [{"role": "system", "content": "x"}]


async def _fake_deepseek(messages):
    return "SELECT lab_code FROM laboratory"


async def _fake_execute(sql):
    return [{"lab_code": "36-401"}, {"lab_code": "36-402"}]


class TestExecuteQuery:
    async def test_success_returns_structured_result(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)
        monkeypatch.setattr(query_service, "call_deepseek", _fake_deepseek)
        monkeypatch.setattr(query_service.db, "execute", _fake_execute)

        r = await query_service.execute_query("列出实验室", "ADMIN", 1)

        assert r["success"] is True
        assert r["row_count"] == 2
        assert r["columns"] == ["lab_code"]
        assert r["rows"] == [["36-401"], ["36-402"]]
        assert r["sql"].startswith("SELECT")
        assert r["error"] is None

    async def test_teacher_gets_permission_injected(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        async def deepseek_course(_):
            return "SELECT * FROM course"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek_course)

        captured = {}

        async def execute(sql):
            captured["sql"] = sql
            return []

        monkeypatch.setattr(query_service.db, "execute", execute)

        await query_service.execute_query("我的课程", "TEACHER", 5)

        assert "course.teacher_id = 5" in captured["sql"]

    async def test_unsafe_sql_returns_error_without_executing(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        async def deepseek_delete(_):
            return "DELETE FROM course"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek_delete)

        executed = []

        async def execute(sql):
            executed.append(sql)
            return []

        monkeypatch.setattr(query_service.db, "execute", execute)

        r = await query_service.execute_query("删掉课程", "ADMIN", 1)

        assert r["success"] is False
        assert "无法安全执行" in r["error"]
        assert executed == []  # 危险 SQL 绝不触库

    async def test_permission_reject_returns_error_not_raise(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        async def deepseek_unknown(_):
            return "SELECT * FROM unknown_table"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek_unknown)

        r = await query_service.execute_query("未知表", "TEACHER", 1)

        assert r["success"] is False
        assert "拒绝执行" in r["error"]

    async def test_unexpected_error_is_captured(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)
        monkeypatch.setattr(query_service, "call_deepseek", _fake_deepseek)

        async def boom(sql):
            raise RuntimeError("db down")

        monkeypatch.setattr(query_service.db, "execute", boom)

        r = await query_service.execute_query("任意问题", "ADMIN", 1)

        assert r["success"] is False
        assert "查询执行失败" in r["error"]
