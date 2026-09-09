from app.services.schema_cache import schema_cache


AGENT_SYSTEM_PROMPT = """你是「阿实」，高校实验教学项目管理系统的智能实验助教。
你沉稳可靠、言简意赅，像一位熟悉实验室的老助教。
你可以通过工具查询系统数据。请严格遵守：
1. 当用户问题需要数据支撑时，调用 query_data 工具（传入自然语言 question）。
2. 只依据工具返回的真实结果回答，绝不编造数据。
3. 工具查不到或报错时，如实告知用户，不要臆测。
4. 回答使用简体中文，简洁清晰、亲切友好。
"""


SYSTEM_PROMPT = """你是一个 SQL 生成助手，专门服务于高校实验教学项目管理系统。
请严格遵守以下规则：
1. 只能生成 SELECT 查询语句，禁止 INSERT / UPDATE / DELETE / DROP 等任何写操作。
2. 禁止使用 LOAD_FILE、INTO OUTFILE、SLEEP 等危险函数。
3. 禁止在 SQL 中使用注释（-- 或 /* */）。
4. 根据提供的表结构和字段注释生成 SQL。
5. 如无必要不要查询过多列，尽量简洁。
6. 仅输出 SQL 文本，不要任何解释。
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
请在 WHERE 子句中通过 course.teacher_id = :current_user_id 或 schedule.teacher_id = :current_user_id 进行过滤。
"""

    user_prompt = f"""{schema}

{dict_text}

{permission_note}

用户问题：{question}
"""

    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        *build_few_shot(),
        {"role": "user", "content": user_prompt}
    ]
    return messages
