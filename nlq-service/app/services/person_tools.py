"""人员档案工具集：把八维人档能力暴露给 Agent。

设计要点（对应《LTPMS_人员档案检索_需求拷问记录》D2/D3/D6/D8）：
- 细粒度工具由模型自主编排，resolve_person 是强制第一步；
- person_ref 只接受工号或用户 id，传姓名会被拒并提示先解析——把"先解析再取数"
  从提示词里的祈使句变成工具契约；
- 双份载荷：model_view（脱敏摘要）回喂模型，display（全量原始行）只上屏；
- 权限断言在工具层收口：非 ADMIN 只能取本人数据。
"""

from app.services.dossier_sql import (
    ROLE_LABELS,
    WEEKDAY_LABELS,
    WEEK_TYPE_LABELS,
    find_manager_labs_by_name,
    find_users_by_name,
    find_users_like,
    get_user_by_ref,
    mask_phone,
)
from app.services import dossier_sql
from app.services.tool_registry import Tool, register_tool

# 完成度断言所需的维度清单（agent_loop 用）
PERSON_DOSSIER_DIMENSIONS: tuple[str, ...] = (
    "person_identity",
    "person_courses",
    "person_schedules",
    "person_class_times",
    "person_experiments",
    "person_managed_labs",
    "person_footprints",
    "person_workload",
)

PERSON_TOOL_NAMES: tuple[str, ...] = ("resolve_person",) + PERSON_DOSSIER_DIMENSIONS

_PERSON_REF_SCHEMA = {
    "type": "string",
    "description": (
        "人员引用：工号（如 T015）或用户 id。不接受姓名，姓名需先经 resolve_person 解析；"
        "唯一例外是 resolve_person 返回 manager_only 时的挂名姓名（仅 person_managed_labs 可用）。"
    ),
}


def _block(title: str, columns: list[str], rows: list[list], note: str | None = None) -> dict:
    return {
        "title": title,
        "columns": columns,
        "rows": rows,
        "empty": len(rows) == 0,
        "note": note,
    }


def _result(dimension: str, title: str, count: int, digest: str, blocks: list[dict],
            extra: dict | None = None, status: str | None = None) -> dict:
    payload = {
        "ok": True,
        "dimension": dimension,
        "title": title,
        "count": count,
        "model_view": {"dimension": dimension, "count": count, "digest": digest},
        "display": {"blocks": blocks},
    }
    if extra:
        payload["model_view"].update(extra)
    if status:
        payload["status"] = status
        payload["model_view"]["status"] = status
    return payload


def _empty_result(dimension: str, title: str, note: str = "暂无记录") -> dict:
    return _result(dimension, title, 0, f"{title}：{note}", [
        _block(title, [], [], note=note),
    ])


def _error(message: str) -> dict:
    return {"ok": False, "error": message}


def _deny_reason(role: str, caller_id: int, target_id: int) -> str | None:
    """非 ADMIN 只能取本人数据。"""
    if role == "ADMIN":
        return None
    if target_id != caller_id:
        return "教师身份仅可查询本人档案，无法查询他人"
    return None


async def _load_target(person_ref: str, role: str, user_id: int):
    """解析 person_ref → user 行，并做权限断言。返回 (user, error)。"""
    if not person_ref or not str(person_ref).strip():
        return None, _error("缺少 person_ref：请先调用 resolve_person 取得工号")
    user = await get_user_by_ref(str(person_ref))
    if user is None:
        return None, _error(
            f"未找到人员「{person_ref}」。person_ref 必须是工号或用户 id；"
            "若手上是姓名，请先调用 resolve_person 解析。"
        )
    denial = _deny_reason(role, user_id, user["id"])
    if denial:
        return None, _error(denial)
    return user, None


# ---------------------------------------------------------------- resolve_person

async def _resolve_person_executor(name: str, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    name = (name or "").strip()
    if not name:
        return _error("缺少 name：请输入要检索的人名或工号")

    users = await find_users_by_name(name)
    manager_labs = await find_manager_labs_by_name(name)

    # 教师角色：只认本人，且绝不返回他人候选（教师管理页本身即 ADMIN only）
    if role != "ADMIN":
        mine = [u for u in users if u["id"] == user_id]
        if mine:
            u = mine[0]
            return _resolved("exact", [u], [], f"已定位到您本人：{u['real_name']}（{u['username']}）")
        if users or manager_labs:
            return _result("resolve_person", "人员解析", 0,
                           "教师身份仅可查询本人档案；未在本人名下匹配到该姓名。",
                           [_block("人员解析", [], [], note="仅可查询本人")],
                           extra={"candidates": []}, status="denied")
        return _result("resolve_person", "人员解析", 0,
                       f"未找到「{name}」。教师身份仅可查询本人档案。",
                       [_block("人员解析", [], [], note="未找到")],
                       extra={"candidates": []}, status="not_found")

    if users:
        if len(users) > 1:
            return _resolved(
                "ambiguous", users, manager_labs,
                f"「{name}」匹配到 {len(users)} 个账号，无法确定目标；"
                "请把以下候选（含工号）列给用户并请其指认，不要猜测、不要取数。",
            )
        return _resolved("exact", users, manager_labs,
                         f"已唯一定位到 {users[0]['real_name']}（工号 {users[0]['username']}）")

    if manager_labs:
        return _resolved(
            "manager_only", [], manager_labs,
            f"「{name}」在 user 表无账号，仅在 laboratory.manager 中以姓名形式存在；"
            "只能取「管理的实验室」一维，其余维度应标注「该人员无系统账号」。",
        )

    fuzzy = await find_users_like(name)
    if fuzzy:
        return _resolved("not_found", fuzzy, [],
                         f"未找到「{name}」的精确匹配。以下是近似候选，请请用户确认，不要编造身份。")
    return _result("resolve_person", "人员解析", 0,
                   f"未找到与「{name}」相关的人员。请如实告知，不要编造。",
                   [_block("人员解析", [], [], note="未找到")],
                   extra={"candidates": []}, status="not_found")


def _resolved(status: str, users: list[dict], manager_labs: list[dict], digest: str) -> dict:
    candidates = [
        {
            "person_ref": u["username"],
            "user_id": u["id"],
            "real_name": u["real_name"],
            "department": u.get("department"),
            "role": ROLE_LABELS.get(u.get("role"), u.get("role")),
            "phone_masked": mask_phone(u.get("phone")),
            "source": "user",
        }
        for u in users
    ]
    columns = ["候选来源", "工号", "姓名", "角色", "院系", "手机号", "用户 id"]
    rows = [
        ["账号", c["person_ref"], c["real_name"], c["role"], c["department"], c["phone_masked"], c["user_id"]]
        for c in candidates
    ]
    if manager_labs:
        ghost_name = _ghost_name_hint(manager_labs)
        rows.extend(
            [["仅挂名", "-", ghost_name, "-", "-", "-", "-"] for _ in manager_labs[:1]]
        )

    blocks = [_block("人员候选", columns, rows, note=None if rows else "未找到")]
    if manager_labs:
        lab_rows = [[l["lab_code"], l["lab_name"], l["device_count"], l["status"]] for l in manager_labs]
        blocks.append(_block("其名下实验室（按姓名匹配）", ["实验室编号", "名称", "设备数", "状态"], lab_rows))

    result = _result("resolve_person", "人员解析", len(candidates) + (1 if manager_labs else 0),
                     digest, blocks,
                     extra={
                         "candidates": candidates,
                         "manager_only": bool(manager_labs) and not candidates,
                     },
                     status=status)
    # 单候选时直接给出下一步要用的 person_ref，省掉模型二次解析；
    # 挂名人员没有工号，用姓名当 person_ref（仅 person_managed_labs 认得）
    if status == "exact" and candidates:
        result["model_view"]["person_ref"] = candidates[0]["person_ref"]
    elif status == "manager_only" and manager_labs:
        result["model_view"]["person_ref"] = _ghost_name_hint(manager_labs)
    return result


def _ghost_name_hint(manager_labs: list[dict]) -> str:
    return manager_labs[0].get("manager") or "（仅挂名）"


# ---------------------------------------------------------------- 八维取数

async def _identity_executor(person_ref: str, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    user, err = await _load_target(person_ref, role, user_id)
    if err:
        return err
    rows = await dossier_sql.identity(user["id"])
    if not rows:
        return _empty_result("person_identity", "身份与账号")
    r = rows[0]
    masked = mask_phone(r.get("phone"))
    # display 按角色决定是否给明文；digest 会回喂模型，必须始终打码
    phone_display = r.get("phone") if role == "ADMIN" else masked
    display_rows = [[
        r["username"], r["real_name"], ROLE_LABELS.get(r["role"], r["role"]),
        r.get("department"), phone_display, "正常" if r.get("status") == 1 else "停用",
        "是" if r.get("is_default_pwd") == 1 else "否", dossier_sql.fmt(r.get("created_at")),
    ]]
    digest = (f"{r['real_name']}（{r['username']}），{ROLE_LABELS.get(r['role'], r['role'])}，"
              f"{r.get('department') or '院系未填'}，手机号 {masked or '未填'}")
    return _result(
        "person_identity", "身份与账号", 1, digest,
        [_block("身份与账号",
                ["工号", "姓名", "角色", "院系", "手机号", "账号状态", "首次登录需改密", "创建时间"],
                display_rows)],
    )


async def _courses_executor(person_ref: str, semester: str | None = None, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    user, err = await _load_target(person_ref, role, user_id)
    if err:
        return err
    try:
        rows = await dossier_sql.courses(user["id"], semester)
    except LookupError as e:
        return _error(str(e))
    if not rows:
        return _empty_result("person_courses", "任课课程")
    display_rows = [[r["course_code"], r["course_name"], r["total_hours"], r["major"], r["semester_name"]] for r in rows]
    names = "、".join(f"{r['course_name']}({r['course_code']})" for r in rows[:5])
    return _result(
        "person_courses", "任课课程", len(rows),
        f"{len(rows)} 门任课：{names}" + (f" 等 {len(rows)} 门" if len(rows) > 5 else ""),
        [_block("任课课程", ["课程号", "课程名", "学时", "专业", "学期"], display_rows)],
    )


async def _schedules_executor(person_ref: str, semester: str | None = None, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    user, err = await _load_target(person_ref, role, user_id)
    if err:
        return err
    try:
        rows = await dossier_sql.schedules(user["id"], semester)
    except LookupError as e:
        return _error(str(e))
    if not rows:
        return _empty_result("person_schedules", "排课记录")
    display_rows = [[
        r["course_code"], r["course_name"], r["lab_code"], r["lab_name"],
        r["class_names"], r["student_count"], r["total_hours"], r["raw_schedule_text"], r["semester_name"],
    ] for r in rows]
    return _result(
        "person_schedules", "排课记录", len(rows),
        f"{len(rows)} 条排课，分布在 {len({r['lab_code'] for r in rows})} 间实验室；"
        f"涉及班级人数合计 {sum(int(r['student_count'] or 0) for r in rows)} 人",
        [_block("排课记录",
                ["课程号", "课程名", "实验室", "实验室名称", "班级", "人数", "学时", "时间文本", "学期"],
                display_rows)],
    )


async def _class_times_executor(person_ref: str, semester: str | None = None, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    user, err = await _load_target(person_ref, role, user_id)
    if err:
        return err
    try:
        rows = await dossier_sql.class_times(user["id"], semester)
    except LookupError as e:
        return _error(str(e))
    if not rows:
        return _empty_result("person_class_times", "课节时间明细",
                             note="暂无课节记录（schedule_time 为空）")
    display_rows = [[
        r["course_code"], r["course_name"], WEEKDAY_LABELS.get(r["day_of_week"], r["day_of_week"]),
        f"第{r['start_section']}-{r['end_section']}节", r["week_range"], WEEK_TYPE_LABELS.get(r["week_type"], r["week_type"]),
        r["semester_name"],
    ] for r in rows]
    return _result(
        "person_class_times", "课节时间明细", len(rows),
        f"{len(rows)} 个课节：" + "；".join(
            f"{WEEKDAY_LABELS.get(r['day_of_week'], '?')}第{r['start_section']}-{r['end_section']}节" for r in rows[:6]
        ),
        [_block("课节时间明细", ["课程号", "课程名", "星期", "节次", "周次范围", "周类型", "学期"], display_rows)],
    )


async def _experiments_executor(person_ref: str, semester: str | None = None, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    user, err = await _load_target(person_ref, role, user_id)
    if err:
        return err
    try:
        rows = await dossier_sql.experiments(user["id"], semester)
    except LookupError as e:
        return _error(str(e))
    if not rows:
        return _empty_result("person_experiments", "实验项目")
    display_rows = [[
        r["course_code"], r["course_name"], r["experiment_code"], r["experiment_name"],
        r["category"], r["type"], r["subject"], r["requirement"], r["experimenter_type"],
        r["group_size"], r["hours"], r["semester_name"],
    ] for r in rows]
    return _result(
        "person_experiments", "实验项目", len(rows),
        f"{len(rows)} 个实验项目，覆盖 {len({r['course_code'] for r in rows})} 门课程",
        [_block("实验项目",
                ["课程号", "课程名", "实验编码", "实验名称", "类别", "类型", "学科", "要求", "实验对象", "分组人数", "学时", "学期"],
                display_rows)],
    )


async def _managed_labs_executor(person_ref: str, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    if not person_ref or not str(person_ref).strip():
        return _error("缺少 person_ref：请先调用 resolve_person 取得工号或挂名姓名")
    user = await get_user_by_ref(str(person_ref))
    if user is None:
        # 挂名档案（决策 D6）：user 表无账号、仅 laboratory.manager 以姓名登记，仅 ADMIN 可查
        ghost_rows = await find_manager_labs_by_name(str(person_ref))
        if not ghost_rows:
            return _error(
                f"未找到人员「{person_ref}」。person_ref 必须是工号或用户 id；"
                "若手上是姓名，请先调用 resolve_person 解析。"
            )
        if role != "ADMIN":
            return _error("教师身份仅可查询本人档案，无法查询他人")
        return _managed_labs_result(ghost_rows)
    denial = _deny_reason(role, user_id, user["id"])
    if denial:
        return _error(denial)
    return _managed_labs_result(await find_manager_labs_by_name(user["real_name"]))


def _managed_labs_result(rows: list[dict]) -> dict:
    if not rows:
        return _empty_result("person_managed_labs", "管理的实验室",
                             note="未以该姓名登记为任何实验室负责人")
    display_rows = [[r["lab_code"], r["lab_name"], r["device_count"], "启用" if r["status"] == 1 else "停用"] for r in rows]
    return _result(
        "person_managed_labs", "管理的实验室", len(rows),
        f"以负责人身份登记 {len(rows)} 间实验室：" + "、".join(r["lab_name"] for r in rows[:5]),
        [_block("管理的实验室", ["实验室编号", "名称", "设备数", "状态"], display_rows)],
    )


async def _footprints_executor(person_ref: str, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    user, err = await _load_target(person_ref, role, user_id)
    if err:
        return err
    if role != "ADMIN":
        return _result(
            "person_footprints", "行为足迹", 0,
            "行为足迹仅管理员可见，该维度已跳过。",
            [_block("行为足迹", [], [], note="仅管理员可见")],
        )
    data = await dossier_sql.footprints(user["id"])
    history, notifications, operations = data["history"], data["notifications"], data["operations"]

    blocks = [
        _block("智能查询记录", ["问题", "是否成功", "角色", "时间"],
               [[h["question"], "成功" if h["success"] == 1 else "失败", h["role"], dossier_sql.fmt(h["created_at"])] for h in history],
               note="系统尚未产生查询记录" if not history else None),
        _block("通知", ["标题", "类型", "已读", "时间"],
               [[n["title"], n["type"], "是" if n["is_read"] == 1 else "否", dossier_sql.fmt(n["created_at"])] for n in notifications],
               note="系统尚未启用通知推送" if not notifications else None),
        _block("操作日志", ["动作", "对象类型", "对象 id", "IP", "时间"],
               [[o["action"], o["target_type"], o["target_id"], o["ip"], dossier_sql.fmt(o["created_at"])] for o in operations],
               note="系统尚未产生操作日志" if not operations else None),
    ]
    total = len(history) + len(notifications) + len(operations)
    return _result(
        "person_footprints", "行为足迹", total,
        f"查询记录 {len(history)} 条、通知 {len(notifications)} 条、操作日志 {len(operations)} 条",
        blocks,
    )


async def _workload_executor(person_ref: str, semester: str | None = None, role: str = "ADMIN", user_id: int = 0, **kwargs) -> dict:
    user, err = await _load_target(person_ref, role, user_id)
    if err:
        return err
    try:
        data = await dossier_sql.workload(user["id"], semester)
    except LookupError as e:
        return _error(str(e))

    summary_rows = [[
        data["course_count"], data["experiment_count"], data["schedule_count"],
        data["total_hours"], data["student_hours"],
    ]]
    gap = data["syllabus_gap"]
    gap_rows = [[g["course_code"], g["course_name"]] for g in gap]
    blocks = [
        _block("工作量概览",
               ["任课门数", "实验项目数", "排课条数", "课程总学时", "学生人时数"],
               summary_rows,
               note="学生人时数 = Σ(排课学时 × 班级人数)，度量的是学生学时，不是教师工作量"),
        _block("就绪度缺口（已排课但未建实验项目）", ["课程号", "课程名"], gap_rows,
               note="无缺口，全部课程均已建实验项目" if not gap else None),
    ]
    return _result(
        "person_workload", "派生洞察", 1,
        f"{data['course_count']} 门课 / {data['experiment_count']} 个实验项目 / "
        f"{data['schedule_count']} 条排课；学生人时数 {data['student_hours']:.0f}；"
        f"就绪度缺口 {len(gap)} 门",
        blocks,
    )


# ---------------------------------------------------------------- 注册

def _tool(name: str, description: str, executor) -> Tool:
    return Tool(
        name=name,
        description=description,
        parameters={
            "type": "object",
            "properties": {"person_ref": _PERSON_REF_SCHEMA},
            "required": ["person_ref"],
        },
        executor=executor,
    )


RESOLVE_PERSON_TOOL = Tool(
    name="resolve_person",
    description=(
        "人员解析：把姓名或工号解析为唯一的人员引用。这是人员档案检索的第一步，"
        "必须先调用它。返回 status：exact（唯一定位）/ ambiguous（多人同名，需请用户指认）/ "
        "manager_only（仅挂名无账号）/ not_found（未找到）。"
    ),
    parameters={
        "type": "object",
        "properties": {
            "name": {"type": "string", "description": "要检索的人名或工号"},
        },
        "required": ["name"],
    },
    executor=_resolve_person_executor,
)

PERSON_TOOLS: tuple[Tool, ...] = (
    _tool("person_identity",
          "人员档案第 1 维：身份与账号（工号/姓名/角色/院系/手机号/账号状态）。",
          _identity_executor),
    _tool("person_courses",
          "人员档案第 2 维：该人员的任课课程列表。可传 semester 限定学期（如 2026-2027-1）。",
          _courses_executor),
    _tool("person_schedules",
          "人员档案第 3 维：该人员的排课记录（含实验室、班级、人数、时间文本）。",
          _schedules_executor),
    _tool("person_class_times",
          "人员档案第 4 维：该人员的课节时间明细（星期/节次/周次范围）。",
          _class_times_executor),
    _tool("person_experiments",
          "人员档案第 5 维：该人员任课下挂的实验项目（user→course→experiment 二跳）。",
          _experiments_executor),
    _tool("person_managed_labs",
          "人员档案第 6 维：该人员以负责人姓名登记的实验室（按 laboratory.manager 精确匹配）。",
          _managed_labs_executor),
    _tool("person_footprints",
          "人员档案第 7 维：行为足迹（智能查询记录/通知/操作日志）。仅管理员可见。",
          _footprints_executor),
    _tool("person_workload",
          "人员档案第 8 维：派生洞察（任课门数/实验项目数/排课数/课程学时/学生人时数/就绪度缺口）。",
          _workload_executor),
)


def register_person_tools() -> None:
    """注册人员档案工具集。重复注册由 tool_registry 抛错保护。"""
    register_tool(RESOLVE_PERSON_TOOL)
    for tool in PERSON_TOOLS:
        register_tool(tool)
