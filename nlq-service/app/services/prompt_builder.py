from app.services.schema_cache import schema_cache


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
