"""人员档案检索（D2~D9 决策）的行为测试。

覆盖四层：
- intent_router：姓名索引最长匹配（「刘思」不得吞掉「刘思思」）
- person_tools：resolve 五分支、教师仅本人、person_ref 只收工号、双份载荷与脱敏
- agent_loop：工具白名单收口、model_view 回喂、完成度断言与补漏
- agent_service：路由命中后锁定人档技能与工具集
"""

import pytest

from app.services import agent_loop, agent_service, intent_router, person_tools
from app.services.skill_loader import Skill
from app.services.tool_registry import Tool, clear_registry, register_tool


@pytest.fixture(autouse=True)
def _clean_registry():
    clear_registry()
    yield
    clear_registry()


def _user(user_id=33, username="T015", real_name="韩艳", role="TEACHER",
          department="软件学院", phone="13349957310"):
    return {
        "id": user_id, "username": username, "real_name": real_name, "role": role,
        "department": department, "phone": phone, "status": 1, "created_at": None,
    }


def _patch_users(monkeypatch, exact=(), fuzzy=(), labs=()):
    async def fake_exact(name):
        return list(exact)

    async def fake_like(name, limit=10):
        return list(fuzzy)

    async def fake_labs(name):
        return list(labs)

    monkeypatch.setattr(person_tools, "find_users_by_name", fake_exact)
    monkeypatch.setattr(person_tools, "find_users_like", fake_like)
    monkeypatch.setattr(person_tools, "find_manager_labs_by_name", fake_labs)


# ------------------------------------------------------------------ intent_router

class TestPersonNameIndex:
    @pytest.mark.asyncio
    async def test_longest_match_wins_over_substring(self, monkeypatch):
        """「刘思」与「刘思思」同住一个索引时，问句中同时包含两者必须命中更长的那个。"""
        async def fake_execute(sql, params=()):
            return [{"name": "刘思"}, {"name": "刘思思"}]

        monkeypatch.setattr(intent_router.db, "execute", fake_execute)
        index = intent_router.PersonNameIndex()
        await index.refresh()

        assert index.match("刘思思管哪些实验室") == "刘思思"
        assert index.match("刘思管哪些实验室") == "刘思"

    @pytest.mark.asyncio
    async def test_matches_username_and_returns_none_without_hit(self, monkeypatch):
        async def fake_execute(sql, params=()):
            return [{"name": "韩艳"}, {"name": "T015"}]

        monkeypatch.setattr(intent_router.db, "execute", fake_execute)
        index = intent_router.PersonNameIndex()
        await index.refresh()

        assert index.match("看看 T015 的情况") == "T015"
        assert index.match("统计各实验室的课程数量") is None


# ------------------------------------------------------------------ resolve_person

class TestResolvePerson:
    @pytest.mark.asyncio
    async def test_exact_returns_person_ref(self, monkeypatch):
        _patch_users(monkeypatch, exact=[_user()])
        result = await person_tools._resolve_person_executor("韩艳", role="ADMIN", user_id=1)

        assert result["status"] == "exact"
        assert result["model_view"]["person_ref"] == "T015"
        # 回喂给模型的一律是打码手机号
        assert result["model_view"]["candidates"][0]["phone_masked"] == "133****7310"

    @pytest.mark.asyncio
    async def test_ambiguous_returns_candidates_for_user_to_pick(self, monkeypatch):
        _patch_users(monkeypatch, exact=[_user(), _user(user_id=99, username="T099")])
        result = await person_tools._resolve_person_executor("韩艳", role="ADMIN", user_id=1)

        assert result["status"] == "ambiguous"
        assert len(result["model_view"]["candidates"]) == 2
        assert "person_ref" not in result["model_view"]
        assert "不要猜测" in result["model_view"]["digest"]

    @pytest.mark.asyncio
    async def test_manager_only_flags_ghost_person(self, monkeypatch):
        _patch_users(monkeypatch, labs=[{"lab_code": "36-401", "lab_name": "物理实验室",
                                         "manager": "杨彬", "device_count": 20, "status": 1}])
        result = await person_tools._resolve_person_executor("杨彬", role="ADMIN", user_id=1)

        assert result["status"] == "manager_only"
        assert result["model_view"]["manager_only"] is True
        assert result["model_view"]["candidates"] == []
        # 挂名人员没有工号，姓名即下一步要传给 person_managed_labs 的 person_ref
        assert result["model_view"]["person_ref"] == "杨彬"

    @pytest.mark.asyncio
    async def test_not_found_offers_fuzzy_candidates_without_faking_identity(self, monkeypatch):
        _patch_users(monkeypatch, fuzzy=[_user()])
        result = await person_tools._resolve_person_executor("x", role="ADMIN", user_id=1)

        assert result["status"] == "not_found"
        assert "不要编造身份" in result["model_view"]["digest"]

    @pytest.mark.asyncio
    async def test_teacher_cannot_resolve_others(self, monkeypatch):
        _patch_users(monkeypatch, exact=[_user(user_id=2, username="T001", real_name="张伟")])
        result = await person_tools._resolve_person_executor("张伟", role="TEACHER", user_id=33)

        assert result["status"] == "denied"
        assert result["model_view"]["candidates"] == []

    @pytest.mark.asyncio
    async def test_teacher_resolves_self(self, monkeypatch):
        _patch_users(monkeypatch, exact=[_user()])
        result = await person_tools._resolve_person_executor("韩艳", role="TEACHER", user_id=33)

        assert result["status"] == "exact"
        assert result["model_view"]["person_ref"] == "T015"


# ------------------------------------------------------------------ 八维工具

class TestDimensionTools:
    @pytest.mark.asyncio
    async def test_name_as_person_ref_is_rejected(self, monkeypatch):
        """工具契约：person_ref 只收工号/id，姓名必须先过 resolve_person。"""
        async def fake_get(ref):
            return None

        monkeypatch.setattr(person_tools, "get_user_by_ref", fake_get)
        result = await person_tools._courses_executor("韩艳", role="ADMIN", user_id=1)

        assert result["ok"] is False
        assert "resolve_person" in result["error"]

    @pytest.mark.asyncio
    async def test_teacher_cross_access_denied(self, monkeypatch):
        async def fake_get(ref):
            return _user(user_id=2, username="T001", real_name="张伟")

        monkeypatch.setattr(person_tools, "get_user_by_ref", fake_get)
        result = await person_tools._courses_executor("T001", role="TEACHER", user_id=33)

        assert result["ok"] is False
        assert "仅可查询本人" in result["error"]

    @pytest.mark.asyncio
    async def test_phone_masked_for_teacher_shown_for_admin(self, monkeypatch):
        async def fake_get(ref):
            return _user()

        async def fake_identity(user_id):
            return [_user()]

        monkeypatch.setattr(person_tools, "get_user_by_ref", fake_get)
        monkeypatch.setattr(person_tools.dossier_sql, "identity", fake_identity)

        admin = await person_tools._identity_executor("T015", role="ADMIN", user_id=1)
        teacher = await person_tools._identity_executor("T015", role="TEACHER", user_id=33)

        assert admin["display"]["blocks"][0]["rows"][0][4] == "13349957310"
        assert teacher["display"]["blocks"][0]["rows"][0][4] == "133****7310"
        # 回喂模型的一律打码
        assert "133****7310" in admin["model_view"]["digest"]

    @pytest.mark.asyncio
    async def test_empty_dimension_is_explicit_not_dropped(self, monkeypatch):
        async def fake_get(ref):
            return _user()

        async def fake_courses(user_id, semester=None):
            return []

        monkeypatch.setattr(person_tools, "get_user_by_ref", fake_get)
        monkeypatch.setattr(person_tools.dossier_sql, "courses", fake_courses)

        result = await person_tools._courses_executor("T015", role="ADMIN", user_id=1)

        assert result["count"] == 0
        block = result["display"]["blocks"][0]
        assert block["empty"] is True
        assert block["note"] == "暂无记录"
        assert "暂无记录" in result["model_view"]["digest"]

    @pytest.mark.asyncio
    async def test_model_view_excludes_display_rows(self, monkeypatch):
        """双份载荷：display 全量行走 steps 上屏，model_view 里不得夹带明细。"""
        async def fake_get(ref):
            return _user()

        async def fake_schedules(user_id, semester=None):
            return [{
                "schedule_id": 17, "course_code": "SE301", "course_name": "软件工程实验",
                "lab_code": "36-201", "lab_name": "软件实验室", "class_names": "软工2301",
                "student_count": 45, "total_hours": 32, "raw_schedule_text": "周一第3-4节(1-16周)",
                "semester_name": "2026-2027-1",
            }]

        monkeypatch.setattr(person_tools, "get_user_by_ref", fake_get)
        monkeypatch.setattr(person_tools.dossier_sql, "schedules", fake_schedules)

        result = await person_tools._schedules_executor("T015", role="ADMIN", user_id=1)

        assert result["display"]["blocks"][0]["rows"], "明细必须上屏"
        assert "rows" not in result["model_view"]
        assert "软工2301" not in str(result["model_view"])


# ------------------------------------------------------------------ 挂名档案

_GHOST_LAB = {"lab_code": "36-601", "lab_name": "计算机实验室",
              "manager": "杨彬", "device_count": 50, "status": 1}


class TestGhostManagedLabs:
    """决策 D6：挂名人员仅 ADMIN 可查、仅「管理的实验室」一维，且用姓名当 person_ref。"""

    @pytest.fixture(autouse=True)
    def _no_user_row(self, monkeypatch):
        async def fake_get(ref):
            return None

        monkeypatch.setattr(person_tools, "get_user_by_ref", fake_get)
        _patch_users(monkeypatch, labs=[_GHOST_LAB])

    @pytest.mark.asyncio
    async def test_admin_reads_ghost_labs_by_name(self):
        result = await person_tools._managed_labs_executor("杨彬", role="ADMIN", user_id=1)

        assert result["ok"] is True
        assert result["count"] == 1
        assert result["display"]["blocks"][0]["rows"][0][0] == "36-601"
        assert "计算机实验室" in result["model_view"]["digest"]

    @pytest.mark.asyncio
    async def test_teacher_cannot_read_ghost_labs(self):
        result = await person_tools._managed_labs_executor("杨彬", role="TEACHER", user_id=33)

        assert result["ok"] is False
        assert "仅可查询本人" in result["error"]

    @pytest.mark.asyncio
    async def test_unknown_ghost_name_still_errors(self, monkeypatch):
        _patch_users(monkeypatch, labs=[])
        result = await person_tools._managed_labs_executor("查无此人", role="ADMIN", user_id=1)

        assert result["ok"] is False
        assert "resolve_person" in result["error"]


# ------------------------------------------------------------------ agent_loop

def _text_message(content):
    return {"role": "assistant", "content": content}


def _tool_call_message(name, arguments="{}", call_id="call_1"):
    return {
        "role": "assistant", "content": None,
        "tool_calls": [{"id": call_id, "type": "function",
                        "function": {"name": name, "arguments": arguments}}],
    }


def _register(name, payload=None):
    async def executor(**kwargs):
        return payload if payload is not None else {"ok": True}

    register_tool(Tool(name, f"工具{name}", {"type": "object", "properties": {}}, executor=executor))


def _replay(replies):
    calls = []

    async def chat_fn(messages, tools):
        calls.append({"messages": list(messages), "tools": tools})
        if not replies:
            raise AssertionError("fake chat_fn 无更多回复")
        return replies.pop(0)

    chat_fn.calls = calls
    return chat_fn


class TestAgentLoopDossier:
    @pytest.mark.asyncio
    async def test_tool_allowlist_restricts_schemas(self):
        _register("query_data")
        _register("person_identity")
        chat_fn = _replay([_text_message("好")])

        await agent_loop.run_agent("q", "ADMIN", 1, chat_fn=chat_fn,
                                   tool_allowlist=("person_identity",))

        names = [t["function"]["name"] for t in chat_fn.calls[0]["tools"]]
        assert names == ["person_identity"]

    @pytest.mark.asyncio
    async def test_model_view_is_fed_back_instead_of_display(self):
        _register("person_identity", {
            "ok": True,
            "model_view": {"count": 1, "digest": "已取到身份"},
            "display": {"blocks": [{"rows": [["13800000000"]]}]},
        })
        chat_fn = _replay([_tool_call_message("person_identity"), _text_message("好")])

        await agent_loop.run_agent("q", "ADMIN", 1, chat_fn=chat_fn)

        tool_message = [m for m in chat_fn.calls[1]["messages"] if m.get("role") == "tool"][0]
        assert "已取到身份" in tool_message["content"]
        assert "13800000000" not in tool_message["content"]

    @pytest.mark.asyncio
    async def test_missing_dimension_triggers_one_nudge_then_report(self):
        _register("person_identity")
        _register("person_courses")
        chat_fn = _replay([
            _tool_call_message("person_identity", call_id="c1"),
            _text_message("先给个不完整的答案"),
            _text_message("二次仍缺"),
        ])

        result = await agent_loop.run_agent(
            "q", "ADMIN", 1, chat_fn=chat_fn, max_steps=6,
            required_dimensions=("person_identity", "person_courses"),
        )

        assert result["answer"] == "二次仍缺"
        assert result["missing_dimensions"] == ["person_courses"]

    @pytest.mark.asyncio
    async def test_non_exact_resolution_disables_completion_assertion(self):
        """resolve 返回 denied 时不该再逼模型取八维：无补漏轮、missing 为空。"""
        _register("resolve_person", {"ok": True, "status": "denied",
                                     "model_view": {"status": "denied", "count": 0}})
        _register("person_identity")
        chat_fn = _replay([
            _tool_call_message("resolve_person", '{"name": "张伟"}', call_id="c1"),
            _text_message("教师身份仅可查询本人档案。"),
        ])

        result = await agent_loop.run_agent(
            "q", "TEACHER", 33, chat_fn=chat_fn, max_steps=6,
            required_dimensions=("person_identity",),
        )

        assert len(chat_fn.calls) == 2, "不应追加补漏轮"
        assert result["answer"] == "教师身份仅可查询本人档案。"
        assert result["missing_dimensions"] == []

    @pytest.mark.asyncio
    async def test_exact_resolution_keeps_completion_assertion(self):
        """resolve 返回 exact 时断言依旧生效：缺维度要先补一轮再收尾。"""
        _register("resolve_person", {"ok": True, "status": "exact",
                                     "model_view": {"status": "exact", "count": 1, "person_ref": "T015"}})
        _register("person_identity")
        chat_fn = _replay([
            _tool_call_message("resolve_person", '{"name": "韩艳"}', call_id="c1"),
            _text_message("先收个尾"),
            _text_message("补完了"),
        ])

        result = await agent_loop.run_agent(
            "q", "ADMIN", 1, chat_fn=chat_fn, max_steps=6,
            required_dimensions=("person_identity",),
        )

        assert len(chat_fn.calls) == 3, "exact 路径必须保留一次补漏机会"
        nudge = chat_fn.calls[2]["messages"][-1]["content"]
        assert "person_identity" in nudge
        assert result["answer"] == "补完了"

    @pytest.mark.asyncio
    async def test_nudge_can_complete_the_checklist(self):
        _register("person_identity")
        _register("person_courses")
        chat_fn = _replay([
            _tool_call_message("person_identity", call_id="c1"),
            _text_message("不完整"),
            _tool_call_message("person_courses", call_id="c2"),
            _text_message("完整档案"),
        ])

        result = await agent_loop.run_agent(
            "q", "ADMIN", 1, chat_fn=chat_fn, max_steps=6,
            required_dimensions=("person_identity", "person_courses"),
        )

        assert result["answer"] == "完整档案"
        assert result["missing_dimensions"] == []
        assert [s["tool"] for s in result["steps"]] == ["person_identity", "person_courses"]

    @pytest.mark.asyncio
    async def test_breaker_reports_missing_dimensions(self):
        _register("person_identity")

        async def chat_fn(messages, tools):
            return _tool_call_message("person_identity")

        result = await agent_loop.run_agent(
            "q", "ADMIN", 1, chat_fn=chat_fn, max_steps=2,
            required_dimensions=("person_identity", "person_courses"),
        )

        assert result["error"] is not None
        assert result["missing_dimensions"] == ["person_courses"]

    @pytest.mark.asyncio
    async def test_forced_person_ref_overrides_model_argument(self):
        """「我的档案」锁定：模型传的 person_ref 被服务端覆写，且系统提示声明跳过解析。"""
        seen = {}

        async def executor(**kwargs):
            seen.update(kwargs)
            return {"ok": True}

        register_tool(Tool("person_identity", "工具", {"type": "object", "properties": {}}, executor=executor))
        chat_fn = _replay([
            _tool_call_message("person_identity", arguments='{"person_ref": "别人"}'),
            _text_message("好"),
        ])

        await agent_loop.run_agent("q", "TEACHER", 5, chat_fn=chat_fn, forced_person_ref="T015")

        assert seen["person_ref"] == "T015"
        assert seen["role"] == "TEACHER"
        assert seen["user_id"] == 5
        system = chat_fn.calls[0]["messages"][0]["content"]
        assert "T015" in system
        assert "跳过第 1 步" in system


# ------------------------------------------------------------------ agent_service 路由

class TestAgentServiceRouting:
    @pytest.mark.asyncio
    async def test_routed_person_locks_dossier_toolset(self, monkeypatch):
        captured = {}

        async def fake_run_agent(question, role, user_id, **kwargs):
            captured.update(kwargs)
            return {"answer": "ok", "steps": [], "steps_used": 0, "error": None}

        monkeypatch.setattr(agent_service.agent_loop, "run_agent", fake_run_agent)

        dossier = Skill("person_dossier", "人员档案", "清单", allowed_tools=("resolve_person", "person_identity"))
        other = Skill("experiment_stat", "统计", "统计", allowed_tools=("query_data",))

        class _Index:
            async def ensure_fresh(self):
                return None

            def match(self, question):
                return "韩艳"

        class _Store:
            def list(self, user_id):
                return {}

            def set(self, *a):
                return None

        result = await agent_service.run_agent_query(
            "查一下韩艳", "ADMIN", 1, memory_store=_Store(),
            skills=[dossier, other], name_index=_Index(),
        )

        assert result["routed_person_name"] == "韩艳"
        assert [s.name for s in captured["skills"]] == ["person_dossier"]
        assert captured["tool_allowlist"] == ("person_identity", "resolve_person")
        assert captured["required_dimensions"] == person_tools.PERSON_DOSSIER_DIMENSIONS
        assert captured["max_steps"] == agent_service.AGENT_MAX_STEPS

    @pytest.mark.asyncio
    async def test_plain_question_excludes_dossier_skill(self, monkeypatch):
        captured = {}

        async def fake_run_agent(question, role, user_id, **kwargs):
            captured.update(kwargs)
            return {"answer": "ok", "steps": [], "steps_used": 0, "error": None}

        monkeypatch.setattr(agent_service.agent_loop, "run_agent", fake_run_agent)

        dossier = Skill("person_dossier", "人员档案", "清单", allowed_tools=("resolve_person",))
        other = Skill("experiment_stat", "统计", "统计", allowed_tools=("query_data",))

        class _Index:
            async def ensure_fresh(self):
                return None

            def match(self, question):
                return None

        class _Store:
            def list(self, user_id):
                return {}

            def set(self, *a):
                return None

        await agent_service.run_agent_query(
            "统计各实验室课程数", "ADMIN", 1, memory_store=_Store(),
            skills=[dossier, other], name_index=_Index(),
        )

        assert [s.name for s in captured["skills"]] == ["experiment_stat"]
        assert captured["tool_allowlist"] is None
        assert captured["required_dimensions"] == ()

    @pytest.mark.asyncio
    async def test_router_failure_degrades_to_plain_qa(self, monkeypatch):
        captured = {}

        async def fake_run_agent(question, role, user_id, **kwargs):
            captured.update(kwargs)
            return {"answer": "ok", "steps": [], "steps_used": 0, "error": None}

        monkeypatch.setattr(agent_service.agent_loop, "run_agent", fake_run_agent)

        class _BrokenIndex:
            async def ensure_fresh(self):
                raise RuntimeError("索引不可用")

            def match(self, question):
                raise AssertionError("索引不可用时不应继续匹配")

        class _Store:
            def list(self, user_id):
                return {}

            def set(self, *a):
                return None

        result = await agent_service.run_agent_query(
            "统计各实验室课程数", "ADMIN", 1, memory_store=_Store(),
            skills=[], name_index=_BrokenIndex(),
        )

        assert result["answer"] == "ok"
        assert result["routed_person_name"] is None

    @pytest.mark.asyncio
    async def test_self_dossier_locks_current_user(self, monkeypatch):
        """「我的档案」入口：person_ref 锁成当前登录用户，工具集不含 resolve_person。"""
        captured = {}

        async def fake_run_agent(question, role, user_id, **kwargs):
            captured.update(kwargs)
            return {"answer": "ok", "steps": [], "steps_used": 0, "error": None}

        async def fake_get_user_by_ref(ref):
            assert ref == "5"
            return _user(user_id=5, username="T015", real_name="韩艳")

        monkeypatch.setattr(agent_service.agent_loop, "run_agent", fake_run_agent)
        monkeypatch.setattr(agent_service, "get_user_by_ref", fake_get_user_by_ref)

        dossier = Skill("person_dossier", "人员档案", "清单",
                        allowed_tools=("resolve_person", "person_identity"))
        other = Skill("experiment_stat", "统计", "统计", allowed_tools=("query_data",))

        class _Index:
            async def ensure_fresh(self):
                return None

            def match(self, question):
                raise AssertionError("锁定入口不应依赖姓名索引")

        class _Store:
            def list(self, user_id):
                return {}

            def set(self, *a):
                return None

        result = await agent_service.run_agent_query(
            "查看我的完整档案", "TEACHER", 5, memory_store=_Store(),
            skills=[dossier, other], name_index=_Index(), self_dossier=True,
        )

        assert result["routed_person_name"] == "韩艳"
        assert [s.name for s in captured["skills"]] == ["person_dossier"]
        assert captured["forced_person_ref"] == "T015"
        assert captured["tool_allowlist"] == person_tools.PERSON_DOSSIER_DIMENSIONS
        assert "resolve_person" not in captured["tool_allowlist"]
        assert captured["required_dimensions"] == person_tools.PERSON_DOSSIER_DIMENSIONS
