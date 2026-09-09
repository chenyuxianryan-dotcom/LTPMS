"""GLM-4V 视觉识别：课表照片 → 结构化课表行草稿。

DeepSeek 无视觉能力，本模块走智谱 GLM-4V-Flash 通道。ZHIPU_API_KEY 为空时优雅降级，
不影响查询类能力。识别结果字段与 Java 侧课表导入（/schedules/import）入参结构对齐。
"""

import json
import re

import httpx

from app.config import settings


async def recognize_schedule_photo(image_base64: str) -> dict:
    """识别一张课表照片，返回结构化课表行草稿。

    返回 dict：success / rows（课表行列表）/ error。
    """
    if not settings.zhipu_api_key:
        return {
            "success": False,
            "rows": [],
            "error": "视觉识别服务未配置（ZHIPU_API_KEY 为空），请改用 Excel 导入",
        }

    prompt = (
        "识别这张高校实验课表表格，提取每一行数据。字段："
        "course_code(课程号)、course_name(课程名)、total_hours(总学时)、"
        "teacher_no(教师工号)、lab_code(实验室编号)、class_names(上课班级)、"
        "student_count(选课人数)、raw_schedule_text(上课时间文本)。"
        "以 JSON 数组返回，每个元素一个对象，键名严格用上述英文名。"
        "无法识别的字段填 null。只返回 JSON 数组，不要输出任何解释文字。"
    )

    payload = {
        "model": settings.zhipu_vision_model,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:image/jpeg;base64,{image_base64}"},
                    },
                    {"type": "text", "text": prompt},
                ],
            }
        ],
        "temperature": 0.1,
    }
    headers = {
        "Authorization": f"Bearer {settings.zhipu_api_key}",
        "Content-Type": "application/json",
    }

    try:
        timeout = httpx.Timeout(30.0)
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(settings.zhipu_api_url, json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()

        content = data["choices"][0]["message"]["content"]
        rows = _parse_rows(content)
        return {"success": True, "rows": rows, "error": None}
    except Exception as e:
        return {"success": False, "rows": [], "error": f"视觉识别失败：{e}"}


def _parse_rows(content: str) -> list[dict]:
    """从模型返回文本中提取 JSON 数组，容错处理前后缀文字。"""
    if not content:
        return []
    # 去掉 markdown 代码块围栏
    cleaned = re.sub(r"```(?:json)?", "", content).strip()
    start = cleaned.find("[")
    end = cleaned.rfind("]")
    if start == -1 or end == -1 or end <= start:
        return []
    try:
        parsed = json.loads(cleaned[start : end + 1])
        if isinstance(parsed, list):
            return [r for r in parsed if isinstance(r, dict)]
    except json.JSONDecodeError:
        return []
    return []
