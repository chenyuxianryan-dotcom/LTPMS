"""视觉识别模块行为测试（统一 DeepSeek 通道，VISION_PROVIDER=zhipu 为回退分支）。

聚焦：课表行归一化的容错、模型文本取 JSON 的容错、key 未配置时的优雅降级。
真实视觉调用依赖外部 key，不在单测内验证。
"""

from app.config import settings
from app.services.vision import _normalize_rows, _parse_object, recognize_schedule_photo


class TestNormalizeRows:
    def test_camel_case_passthrough(self):
        rows = _normalize_rows([{"courseCode": "CS101", "courseName": "数据结构"}])
        assert len(rows) == 1
        assert rows[0]["courseCode"] == "CS101"
        assert rows[0]["courseName"] == "数据结构"

    def test_snake_case_fallback(self):
        rows = _normalize_rows([{"course_code": "CS101", "raw_schedule_text": "周一第1-2节"}])
        assert rows[0]["courseCode"] == "CS101"
        assert rows[0]["rawScheduleText"] == "周一第1-2节"

    def test_non_list_returns_empty(self):
        assert _normalize_rows('{"courseCode": "CS101"}') == []
        assert _normalize_rows(None) == []

    def test_drops_non_dict_and_blank_rows(self):
        rows = _normalize_rows(
            ["识别噪声", {"courseCode": None, "totalHours": ""}, {"courseName": "数据结构"}]
        )
        assert len(rows) == 1
        assert rows[0]["courseName"] == "数据结构"
        assert rows[0]["courseCode"] is None


class TestParseObject:
    def test_plain_json_object(self):
        data = _parse_object('{"route": "schedule", "digest": "3 行课表"}')
        assert data["route"] == "schedule"

    def test_markdown_fence(self):
        data = _parse_object('```json\n{"route": "document"}\n```')
        assert data["route"] == "document"

    def test_surrounding_text(self):
        data = _parse_object('识别结果如下：{"route": "notice"} 以上是全部')
        assert data["route"] == "notice"

    def test_unparseable_returns_none(self):
        assert _parse_object("无法识别") is None
        assert _parse_object("") is None
        assert _parse_object("{不是合法 JSON}") is None


class TestRecognizeDegradation:
    async def test_deepseek_key_missing_degrades(self, monkeypatch):
        monkeypatch.setattr(settings, "vision_provider", "deepseek")
        monkeypatch.setattr(settings, "deepseek_api_key", "")

        result = await recognize_schedule_photo("aGVsbG8=")

        assert result["success"] is False
        assert "未配置" in result["error"]
        assert result["rows"] == []

    async def test_zhipu_fallback_key_missing_degrades(self, monkeypatch):
        monkeypatch.setattr(settings, "vision_provider", "zhipu")
        monkeypatch.setattr(settings, "zhipu_api_key", "")

        result = await recognize_schedule_photo("aGVsbG8=")

        assert result["success"] is False
        assert "未配置" in result["error"]
        assert result["rows"] == []
