"""技能框架：YAML 技能包加载、发现与提示词格式化。

技能 = 一个可复用的「提示词 + 允许工具」包，加载后注入 Agent 系统提示，
让 Agent 具备特定领域的行为模板（如「学期实验项目统计」「课程就绪度体检」）。
"""

import os
from dataclasses import dataclass

import yaml


@dataclass(frozen=True)
class Skill:
    name: str
    description: str
    prompt: str
    allowed_tools: tuple[str, ...] = ()


def load_skill(path: str) -> Skill:
    """从单个 YAML 文件加载技能；缺 name/prompt 或非映射结构抛 ValueError。"""
    with open(path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    if not isinstance(data, dict):
        raise ValueError(f"技能文件 {path} 不是映射结构")

    name = data.get("name")
    prompt = data.get("prompt")
    if not name or not prompt:
        raise ValueError(f"技能文件 {path} 缺少必填字段 name / prompt")

    allowed_tools = data.get("allowed_tools") or []
    if isinstance(allowed_tools, str):
        allowed_tools = [allowed_tools]

    return Skill(
        name=str(name).strip(),
        description=str(data.get("description") or "").strip(),
        prompt=str(prompt).strip(),
        allowed_tools=tuple(str(t) for t in allowed_tools),
    )


def discover_skills(skills_dir: str) -> list[Skill]:
    """扫描目录下所有 .yaml/.yml 技能包；坏文件跳过不中断，按文件名排序。"""
    if not os.path.isdir(skills_dir):
        return []

    skills: list[Skill] = []
    for filename in sorted(os.listdir(skills_dir)):
        if not filename.endswith((".yaml", ".yml")):
            continue
        path = os.path.join(skills_dir, filename)
        try:
            skills.append(load_skill(path))
        except (ValueError, yaml.YAMLError):
            # 单个坏技能不拖垮整体加载，静默跳过（启动日志可另补）
            continue
    return skills


def format_skills_prompt(skills: list[Skill]) -> str:
    """把技能列表拼成一段注入系统提示的文本。"""
    if not skills:
        return ""

    lines = ["", "## 可用技能（仅在触发条件满足时启用）"]
    for s in skills:
        desc = f"——{s.description}" if s.description else ""
        tools = f"（允许工具：{', '.join(s.allowed_tools)}）" if s.allowed_tools else ""
        lines.append(f"### 技能：{s.name}{desc}{tools}")
        lines.extend(f"  {line}" for line in s.prompt.splitlines())
    return "\n".join(lines)
