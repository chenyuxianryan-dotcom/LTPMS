"""共享文本生成工具：材料清单 / 数字档案 / 叙事报告 / 教学总结共用。

避免为每个 LLM 文本生成场景各写一套调用代码（防臃肿红线），
统一经 call_deepseek 生成文本，场景差异由 prompt 模板（技能化）表达。
"""

from app.services.deepseek_client import call_deepseek


async def generate_text(prompt: str, timeout: float | None = None) -> dict:
    """根据 prompt 生成一段文本，返回结构化结果。

    timeout 为 per-call 超时覆盖（None 时沿用全局 15s；报告等长文场景传更宽值）。
    返回 dict：success / text / error。
    """
    try:
        text = await call_deepseek([{"role": "user", "content": prompt}], timeout=timeout)
        return {"success": True, "text": text, "error": None}
    except Exception as e:
        return {"success": False, "text": "", "error": f"文本生成失败：{e}"}


async def generate_material_list(experiment_info: str) -> dict:
    """材料清单生成：根据实验项目信息推算所需器材/耗材清单。

    experiment_info: 实验项目的结构化描述（名称/类别/要求等）。
    """
    prompt = (
        "你是高校实验教学的器材管理员。根据以下实验项目信息，"
        "推算完成该实验所需的器材与耗材清单。\n\n"
        "实验项目信息：\n" + experiment_info + "\n\n"
        "请以「器材/耗材名称 | 数量 | 备注」三列的形式输出，"
        "每条一行，标注这是「建议清单，可编辑」。只输出清单，不要其他解释。"
    )
    return await generate_text(prompt)
