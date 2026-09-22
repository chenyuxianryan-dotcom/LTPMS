from app.services.schema_cache import schema_cache


AGENT_SYSTEM_PROMPT = """你是「阿实」，高校实验教学项目管理系统的常驻实验助教——懂每间实验室、每门课、每个人。
语气轻快、说人话，不堆术语，不谄媚。

三条铁律：
1. 涉及「人」的问题，先用工具把这个人定位清楚，再取数。不确定就问，绝不猜。
2. 教师身份只能查询本人；密码、手机号等个人隐私永不外发，也永不复述。
3. 宁可说「暂无记录」，不可编造一行数据。

【普通问答】需要数据时调用 query_data（传入自然语言问题），只依据工具返回的真实结果作答。
工具查不到或报错时如实告知用户，不要臆测。

【知识库】用户问校内制度、办事流程、课程与实验资料时，先调用 knowledge_search 检索，
再依据返回的段落作答：
- 每条结论必须带出处（《文档标题》· 段落标题），不得改写或张冠李戴；
- 校内事实（规定、流程、截止时间、提交材料）检索无果时，如实说「资料里没查到」，
  并给出「该问谁」的出口；不得用模型记忆补造校内规定与流程；
- 通用知识（概念解释、方法原理）可以照答，但必须声明这是通用知识、非课程指定；
- 资料结论与系统数据分开表述：来自知识库的说「资料显示」，来自 query_data 的说「系统显示」。

【档案模式】当系统提示中出现「人员档案」技能时，你的职责是「把档案摊全并如实点评」，
不是「把档案写成一段漂亮话」。此时：
- 少说多列：结论与点评合计不超过两句，其余交给结构化档案卡呈现；
- 逐维度分块列出，count 为 0 的维度必须显式写「暂无记录」，不得省略该区块；
- 不得为了读起来顺而合并、概括或省略任何维度。
"""


SYSTEM_PROMPT = """你是一个 SQL 生成助手，专门服务于高校实验教学项目管理系统。
请严格遵守以下规则：
1. 只能生成 SELECT 查询语句，禁止 INSERT / UPDATE / DELETE / DROP 等任何写操作。
2. 禁止使用 LOAD_FILE、INTO OUTFILE、SLEEP 等危险函数。
3. 禁止在 SQL 中使用注释（-- 或 /* */）。
4. 根据提供的表结构（含列注释）、字典映射与关联说明生成 SQL。
5. 涉及 user 表时必须显式列出所需列（禁止 SELECT * 或 u.*），禁止查询 password。
6. 输出列一律取中文别名（如 lab_code AS 实验室编号），聚合列同样取中文别名。
7. 条件值使用库内存储格式：实验室编号形如 '36-401'（用户口语「36栋401」需归一化）、
   课程号形如 'CS101'、学期名形如 '2026-2027-1'；人名使用正式姓名。
8. 仅输出 SQL 文本，不要任何解释。
9. 若问题与上述表结构无关（问制度、流程、办事指南、资料内容、概念解释等），
   只输出 NOT_A_DATA_QUESTION，不要臆造表名、也不要硬凑一句 SQL。
"""


# 表间关联与统计口径（模型不知道 FK 关系，必须显式给出）
SCHEMA_GUIDE = """-- 关联与统计口径
-- 表关联：experiment.course_id = course.id；schedule.course_code = course.course_code；
-- schedule.lab_code = laboratory.lab_code；schedule_time.schedule_id = schedule.id；
-- schedule.teacher_id / course.teacher_id 关联 user.id（role = 'TEACHER' 的是教师）；
-- laboratory.manager 是负责人姓名，可能没有对应 user 账号（挂名）。
-- 口径：人时数 = experiment.hours × schedule.student_count（学生人时，不是教师工作量）；
-- 「开设课程数」按课程号去重计数；当前学期 = semester.status = 'ACTIVE'。
-- 开放管理：lab_open_time.lab_code = laboratory.lab_code；lab_open_time.semester_id = semester.id；
-- lab_open_exception.lab_code = laboratory.lab_code；lab_open_exception.semester_id = semester.id；
-- holiday 无学期外键（holiday_date 逐日一行），某学期节假日 = holiday_date BETWEEN semester.start_date AND semester.end_date。
-- 开放口径：audience ∈ 全校/本院/指定专业/教师专用；week_type ∈ ALL/ODD/EVEN（全周/单周/双周）；
-- lab_open_time 仅 enabled = 1 的行是生效开放时段；lab_open_exception 表示实验室临时闭馆（该日不开放）。
-- 周历口径：第 w 周起始日 = semester.week1_start_date + (w-1)×7 天（week1_start_date 为空时按 start_date 推算）；
-- 「本周」= 按该口径用 CURDATE() 推算的周次。
-- 利用率 = 占用节时 / 开放节时（均为周加权，不含节假日/例外日扣减）。
"""


def build_few_shot() -> list[dict]:
    return [
        {
            "role": "user",
            "content": "列出所有实验室"
        },
        {
            "role": "assistant",
            "content": "SELECT lab_code, lab_name, manager, device_count FROM laboratory WHERE status = 1"
        },
        {
            "role": "user",
            "content": "统计 2026-2027 学年的实验课程数量"
        },
        {
            "role": "assistant",
            "content": "SELECT COUNT(*) as course_count FROM course c JOIN semester s ON c.semester_id = s.id WHERE s.academic_year = '2026-2027'"
        }
    ]


async def build_messages(question: str, role: str) -> list[dict]:
    schema = await schema_cache.get_schema_text()
    dict_text = await schema_cache.get_dict_text()

    permission_note = ""
    if role == "TEACHER":
        permission_note = """
当前用户为教师角色，查询结果必须只包含当前教师自己任教的数据。
系统会自动为查询注入「仅本人」的数据权限过滤条件，你不需要（也不要）在 SQL 中手写 teacher_id / user_id 过滤。
"""

    user_prompt = f"""{schema}

{dict_text}

{SCHEMA_GUIDE}

{permission_note}

用户问题：{question}
"""

    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        *build_few_shot(),
        {"role": "user", "content": user_prompt}
    ]
    return messages


def build_repair_messages(base_messages: list[dict], failed_sql: str, error: str) -> list[dict]:
    """失败修复回合：把上一次的 SQL 与错误反馈回模型，要求重新生成"""
    return [
        *base_messages,
        {"role": "assistant", "content": failed_sql},
        {
            "role": "user",
            "content": (
                f"你上一次生成的内容未能通过校验或执行失败：{error}\n"
                "请根据上述错误修正，只输出新的 SELECT 语句（不要解释）。"
            ),
        },
    ]
