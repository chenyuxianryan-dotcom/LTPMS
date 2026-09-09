"""技能框架（YAML 技能包加载 + 发现 + 提示词格式化）的行为测试。"""

import tempfile

import pytest

from app.services.skill_loader import Skill, discover_skills, format_skills_prompt, load_skill


@pytest.fixture
def skill_dir():
    # 不用 pytest 的 tmp_path：Windows 沙箱下系统 Temp 目录（含中文）拒绝访问，
    # 改在项目内建临时目录，用后即删。
    with tempfile.TemporaryDirectory(dir=".", prefix="tmp_skills_") as d:
        yield d


def _write_skill(directory, filename, content):
    import os

    p = os.path.join(directory, filename)
    with open(p, "w", encoding="utf-8") as f:
        f.write(content)
    return p


def test_load_skill_valid(skill_dir):
    p = _write_skill(
        skill_dir,
        "experiment_stat.yaml",
        """name: experiment_stat
description: 统计学期实验项目
prompt: |
  当用户询问实验项目统计时，调用 query_data。
allowed_tools:
  - query_data
""",
    )
    skill = load_skill(p)
    assert skill.name == "experiment_stat"
    assert skill.description == "统计学期实验项目"
    assert "query_data" in skill.prompt
    assert skill.allowed_tools == ("query_data",)


def test_load_skill_defaults(skill_dir):
    p = _write_skill(skill_dir, "minimal.yaml", "name: minimal\nprompt: 仅做提示\n")
    skill = load_skill(p)
    assert skill.description == ""
    assert skill.allowed_tools == ()


def test_load_skill_missing_name_raises(skill_dir):
    p = _write_skill(skill_dir, "bad.yaml", "description: 缺 name\nprompt: x\n")
    with pytest.raises(ValueError):
        load_skill(p)


def test_load_skill_missing_prompt_raises(skill_dir):
    p = _write_skill(skill_dir, "bad.yaml", "name: bad\n")
    with pytest.raises(ValueError):
        load_skill(p)


def test_load_skill_not_a_mapping_raises(skill_dir):
    p = _write_skill(skill_dir, "bad.yaml", "- just\n- a list\n")
    with pytest.raises(ValueError):
        load_skill(p)


def test_discover_skills_returns_sorted(skill_dir):
    _write_skill(skill_dir, "b.yaml", "name: b\nprompt: 技能B\n")
    _write_skill(skill_dir, "a.yaml", "name: a\nprompt: 技能A\n")
    _write_skill(skill_dir, "readme.txt", "不是技能")

    skills = discover_skills(skill_dir)
    assert [s.name for s in skills] == ["a", "b"]


def test_discover_skills_skips_bad_file(skill_dir):
    _write_skill(skill_dir, "good.yaml", "name: good\nprompt: 好的\n")
    _write_skill(skill_dir, "broken.yaml", "name: broken\n")  # 缺 prompt

    skills = discover_skills(skill_dir)
    assert [s.name for s in skills] == ["good"]


def test_discover_skills_missing_dir_returns_empty(skill_dir):
    import os

    assert discover_skills(os.path.join(skill_dir, "nope")) == []


def test_format_skills_prompt():
    skills = [
        Skill(name="a", description="技能A", prompt="内容A", allowed_tools=("query_data",)),
        Skill(name="b", description="", prompt="内容B"),
    ]
    text = format_skills_prompt(skills)
    assert "技能A" in text
    assert "内容A" in text
    assert "a" in text and "b" in text
