"""实验材料文件画像测试（T07）。

文件落盘目录为系统边界（monkeypatch settings.lab_upload_dir 指向临时目录）。
锁定 AC：① csv/xlsx 画像字段；② 超限/坏文件降级不抛异常；③ 目录穿越被拒；④ 无文件不报错。
"""

import tempfile
from pathlib import Path

import pytest

from app.config import settings
from app.services import file_profile
from app.services.file_profile import profile_dataset_file, resolve_upload_path


@pytest.fixture
def upload_dir(monkeypatch):
    """把落盘目录指向临时目录，并放宽采样上限（除超限用例外）。"""
    tmp = tempfile.mkdtemp()
    monkeypatch.setattr(settings, "lab_upload_dir", tmp)
    monkeypatch.setattr(settings, "profile_max_file_mb", 20)
    monkeypatch.setattr(settings, "profile_sample_rows", 2000)
    return Path(tmp)


def _write(directory: Path, name: str, content: str) -> str:
    (directory / name).write_text(content, encoding="utf-8")
    return name


# ---- AC③ 目录穿越 ----

def test_resolve_rejects_parent_traversal(upload_dir):
    assert resolve_upload_path("../secret.csv") is None
    assert resolve_upload_path("../../etc/passwd") is None


def test_resolve_rejects_absolute_override(upload_dir):
    # 绝对路径会覆盖 base，拼接后越界 → 拒绝
    assert resolve_upload_path(str(Path(tempfile.gettempdir()) / "x.csv")) is None


def test_resolve_accepts_plain_filename(upload_dir):
    name = _write(upload_dir, "ok.csv", "a,b\n1,2\n")
    resolved = resolve_upload_path(name)
    assert resolved is not None and resolved.is_file()


def test_profile_traversal_degrades_not_raises(upload_dir):
    result = profile_dataset_file("../secret.csv", "csv", "secret.csv", 10)
    assert result["profiled"] is False
    assert "越界" in result["reason"] or "拒绝" in result["reason"]


# ---- AC④ 无文件 / 文件缺失 ----

def test_profile_missing_file_degrades(upload_dir):
    result = profile_dataset_file("does_not_exist.csv", "csv", "幽灵.csv", None)
    assert result["profiled"] is False
    assert "不存在" in result["reason"]


def test_profile_empty_stored_path(upload_dir):
    result = profile_dataset_file("", "csv", None, None)
    assert result["profiled"] is False


# ---- AC① csv 画像 ----

def test_profile_csv_fields(upload_dir):
    content = (
        "ts,level,module,message\n"
        "2026-09-18T08:15:02,ERROR,log_parse,unexpected token at offset 42\n"
        "2026-09-18T08:16:11,ERROR,index_write,deadlock detected on shard 3\n"
        "2026-09-18T08:19:05,WARN,flush,buffer 78% full\n"
    )
    name = _write(upload_dir, "lab_log.csv", content)
    result = profile_dataset_file(name, "csv", "lab_log_20260918.csv", len(content))

    assert result["profiled"] is True
    assert result["kind"] == "csv"
    assert result["filename"] == "lab_log_20260918.csv"
    assert result["row_count"] == 3
    assert result["column_count"] == 4
    assert result["fields"] == ["ts", "level", "module", "message"]
    assert result["missing_by_field"]["level"] == 0
    # 全为文本列 → 无数值列
    assert result["numeric_columns"] == []
    assert len(result["sample_head"]) == 3


def test_profile_csv_numeric_distribution_and_outlier(upload_dir):
    rows = ["score,grade\n"]
    for i in range(1, 21):
        rows.append(f"{i},pass\n")
    rows.append("100,pass\n")  # 离群点
    name = _write(upload_dir, "nums.csv", "".join(rows))
    result = profile_dataset_file(name, "csv", "nums.csv", None)

    assert result["profiled"] is True
    numeric = {col["field"]: col for col in result["numeric_columns"]}
    assert "score" in numeric
    assert numeric["score"]["count"] == 21
    assert numeric["score"]["min"] == 1.0
    assert numeric["score"]["max"] == 100.0
    assert 100.0 in numeric["score"]["outliers"]
    # grade 列非数值 → 不进 numeric_columns
    assert "grade" not in numeric


def test_profile_csv_missing_counted(upload_dir):
    name = _write(upload_dir, "miss.csv", "a,b\n1,\n2,x\n,\n")
    result = profile_dataset_file(name, "csv", "miss.csv", None)
    assert result["missing_by_field"]["a"] == 1
    assert result["missing_by_field"]["b"] == 2


# ---- AC① txt 画像 ----

def test_profile_txt(upload_dir):
    name = _write(upload_dir, "note.txt", "line1\nline2\nline3\n")
    result = profile_dataset_file(name, "txt", "note.txt", None)
    assert result["profiled"] is True
    assert result["kind"] == "txt"
    assert result["line_count"] == 3
    assert result["sample_head"][0] == "line1"


# ---- AC① xlsx 画像（openpyxl） ----

def test_profile_xlsx(upload_dir):
    from openpyxl import Workbook

    workbook = Workbook()
    sheet = workbook.active
    sheet.title = "数据"
    sheet.append(["id", "value"])
    for i in range(1, 11):
        sheet.append([i, i * 2])
    path = upload_dir / "sheet.xlsx"
    workbook.save(path)

    result = profile_dataset_file("sheet.xlsx", "xlsx", "sheet.xlsx", None)
    assert result["profiled"] is True
    assert result["kind"] == "xlsx"
    assert result["fields"] == ["id", "value"]
    assert result["row_count"] == 10
    assert result["sheet_names"] == ["数据"]
    numeric = {col["field"]: col for col in result["numeric_columns"]}
    assert numeric["value"]["max"] == 20.0


# ---- zip / 图片：仅登记 ----

def test_profile_zip_register_only(upload_dir):
    name = _write(upload_dir, "pack.zip", "not really a zip but type drives branch")
    result = profile_dataset_file(name, "zip", "pack.zip", None)
    assert result["profiled"] is False
    assert "登记" in result["reason"]


def test_profile_image_register_only(upload_dir):
    name = _write(upload_dir, "photo.png", "binary-ish")
    result = profile_dataset_file(name, "png", "photo.png", None)
    assert result["profiled"] is False
    assert "登记" in result["reason"]


# ---- AC② 超限 / 坏文件降级 ----

def test_profile_oversize_degrades(upload_dir, monkeypatch):
    monkeypatch.setattr(settings, "profile_max_file_mb", 0)  # 任何非空文件都超限
    name = _write(upload_dir, "big.csv", "a,b\n1,2\n")
    result = profile_dataset_file(name, "csv", "big.csv", None)
    assert result["profiled"] is False
    assert "上限" in result["reason"]


def test_profile_corrupt_xlsx_degrades_not_raises(upload_dir):
    # 声明 xlsx 但内容是垃圾 → openpyxl 解析失败 → 降级，不抛异常
    name = _write(upload_dir, "bad.xlsx", "this is not a real xlsx")
    result = profile_dataset_file(name, "xlsx", "bad.xlsx", None)
    assert result["profiled"] is False
    assert "降级" in result["reason"]


def test_profile_real_fixture_sample(upload_dir):
    """AC① 真实样本口径：与 backend fixture_lab_log_v1.csv 同结构（ts/level/module/message）。"""
    real = Path(__file__).resolve().parents[2] / "backend" / "uploads" / "lab" / "fixture_lab_log_v1.csv"
    if not real.is_file():
        pytest.skip("仓库 fixture 样本不存在")
    import csv
    with open(real, "r", encoding="utf-8", newline="") as fh:
        header = next(csv.reader(fh))
    assert header == ["ts", "level", "module", "message"]
