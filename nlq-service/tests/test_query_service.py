"""execute_query 行为测试 —— NL2SQL 全链路的公共执行入口。

面向公共接口 execute_query(question, role, user_id)，描述「一次查询如何编排：
build_messages → call_deepseek → extract/validate → inject_permission → ensure_limit → db.execute
→ 失败时带错误反馈修复一次」。
LLM / 建库 schema / DB 均属系统边界，用 monkeypatch 隔离。
"""

from app.services import query_service
from app.services.permission import PermissionRejectError


async def _fake_build_messages(question, role):
    return [{"role": "system", "content": "x"}]


async def _fake_deepseek(messages):
    return "SELECT lab_code FROM laboratory"


async def _fake_execute(sql):
    return ["lab_code"], [{"lab_code": "36-401"}, {"lab_code": "36-402"}]


class TestExecuteQuery:
    async def test_success_returns_structured_result(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)
        monkeypatch.setattr(query_service, "call_deepseek", _fake_deepseek)
        monkeypatch.setattr(query_service.db, "execute_with_columns", _fake_execute)

        r = await query_service.execute_query("列出实验室", "ADMIN", 1)

        assert r["success"] is True
        assert r["row_count"] == 2
        assert r["columns"] == ["lab_code"]
        assert r["rows"] == [["36-401"], ["36-402"]]
        assert r["sql"].startswith("SELECT")
        assert r["error"] is None

    async def test_empty_result_keeps_columns(self, monkeypatch):
        # 0 行也必须保留列结构（前端据此渲染表头/空态，而不是空白框）
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)
        monkeypatch.setattr(query_service, "call_deepseek", _fake_deepseek)

        async def execute(sql):
            return ["lab_code", "lab_name"], []

        monkeypatch.setattr(query_service.db, "execute_with_columns", execute)

        r = await query_service.execute_query("列出实验室", "ADMIN", 1)

        assert r["success"] is True
        assert r["row_count"] == 0
        assert r["columns"] == ["lab_code", "lab_name"]
        assert r["rows"] == []

    async def test_teacher_gets_permission_injected(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        async def deepseek_course(_):
            return "SELECT * FROM course"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek_course)

        captured = {}

        async def execute(sql):
            captured["sql"] = sql
            return [], []

        monkeypatch.setattr(query_service.db, "execute_with_columns", execute)

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
            return [], []

        monkeypatch.setattr(query_service.db, "execute_with_columns", execute)

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

        monkeypatch.setattr(query_service.db, "execute_with_columns", boom)

        r = await query_service.execute_query("任意问题", "ADMIN", 1)

        assert r["success"] is False
        assert "查询执行失败" in r["error"]


class TestNotADataQuestion:
    """模型自陈「非数据问题」时的短路：不打库、不重试，带 44001 交给前端转顾问。

    背景：制度/流程/资料类提问没有对应业务表，硬凑 SQL 只会报「表不存在」，
    或者更糟——模型直接去捞知识库原文（无出处、绕过可见性），所以由模型显式
    拒绝、链路立即结束，前端据 error_code 转阿实走 knowledge_search。
    """

    async def test_sentinel_short_circuits_with_unsafe_code(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        calls = []

        async def deepseek(messages):
            calls.append(messages)
            return "NOT_A_DATA_QUESTION"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek)

        executed = []

        async def execute(sql):
            executed.append(sql)
            return [], []

        monkeypatch.setattr(query_service.db, "execute_with_columns", execute)

        r = await query_service.execute_query("实验课请假要提前多久", "ADMIN", 1)

        assert r["success"] is False
        assert r["error_code"] == "NLQ_UNSAFE"
        assert executed == []  # 非数据问题绝不触库
        assert len(calls) == 1  # 不进入修复回合

    async def test_sentinel_wins_over_trailing_sql(self, monkeypatch):
        # 混写（哨兵 + 半截 SQL）一律按非数据问题处理：畸形输出不该硬跑
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        async def deepseek(_):
            return "NOT_A_DATA_QUESTION\nSELECT lab_code FROM laboratory"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek)

        executed = []

        async def execute(sql):
            executed.append(sql)
            return [], []

        monkeypatch.setattr(query_service.db, "execute_with_columns", execute)

        r = await query_service.execute_query("实验室管理规范是什么", "ADMIN", 1)

        assert r["success"] is False
        assert r["error_code"] == "NLQ_UNSAFE"
        assert executed == []

    async def test_failure_without_sentinel_has_no_error_code(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        async def deepseek(_):
            return "DELETE FROM course"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek)

        r = await query_service.execute_query("删掉课程", "ADMIN", 1)

        assert r["success"] is False
        assert r["error_code"] is None  # 危险 SQL 不是「非数据问题」，前端按普通失败展示


class TestRepairRound:
    """校验/执行失败时带错误反馈重生成一次（上限 1 次修复）"""

    async def test_validator_reject_then_repair_succeeds(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        calls = []

        async def deepseek(messages):
            calls.append(messages)
            if len(calls) == 1:
                return "SELECT * FROM user"  # 撞 user 星号规则
            return "SELECT COUNT(*) AS 教师数 FROM user WHERE role = 'TEACHER'"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek)

        async def execute(sql):
            return ["教师数"], [{"教师数": 8}]

        monkeypatch.setattr(query_service.db, "execute_with_columns", execute)

        r = await query_service.execute_query("现在有多少名教师", "ADMIN", 1)

        assert r["success"] is True
        assert r["rows"] == [[8]]
        assert len(calls) == 2
        # 修复回合把失败原因回喂给了模型
        assert "校验未通过" in calls[1][-1]["content"]
        assert calls[1][-2]["content"] == "SELECT * FROM user"

    async def test_exec_error_then_repair_succeeds(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)
        monkeypatch.setattr(query_service, "call_deepseek", _fake_deepseek)

        attempts = {"n": 0}

        async def flaky(sql):
            attempts["n"] += 1
            if attempts["n"] == 1:
                raise RuntimeError("(1064, syntax error)")
            return ["lab_code"], [{"lab_code": "36-401"}]

        monkeypatch.setattr(query_service.db, "execute_with_columns", flaky)

        r = await query_service.execute_query("列出实验室", "ADMIN", 1)

        assert r["success"] is True
        assert attempts["n"] == 2

    async def test_repair_limited_to_one_extra_attempt(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        calls = []

        async def deepseek(messages):
            calls.append(messages)
            return "DELETE FROM course"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek)

        r = await query_service.execute_query("删掉课程", "ADMIN", 1)

        assert r["success"] is False
        assert len(calls) == 2  # 首答 + 一次修复，不再重试

    async def test_permission_reject_then_repair_succeeds(self, monkeypatch):
        monkeypatch.setattr(query_service, "build_messages", _fake_build_messages)

        calls = []

        async def deepseek(messages):
            calls.append(messages)
            if len(calls) == 1:
                return "SELECT * FROM ghost_table"
            return "SELECT COUNT(*) AS 实验室数 FROM laboratory"

        monkeypatch.setattr(query_service, "call_deepseek", deepseek)

        async def execute(sql):
            return ["实验室数"], [{"实验室数": 39}]

        monkeypatch.setattr(query_service.db, "execute_with_columns", execute)

        r = await query_service.execute_query("有多少间实验室", "TEACHER", 5)

        assert r["success"] is True
        assert "只使用表结构中存在的表" in calls[1][-1]["content"]
