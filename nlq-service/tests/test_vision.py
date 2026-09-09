"""GLM-4V 视觉识别模块行为测试。

聚焦：ZHIPU key 为空时的优雅降级、_parse_rows 的容错解析。
真实 GLM-4V 调用依赖外部 key，不在单测内验证。
"""

from app.services.vision import _parse_rows, recognize_schedule_photo


class TestParseRows:
    def test_plain_json_array(self):
        content = '[{"course_code": "CS101", "course_name": "数据结构"}]'
        rows = _parse_rows(content)
        assert len(rows) == 1
        assert rows[0]["course_code"] == "CS101"

    def test_markdown_fence(self):
        content = '```json\n[{"course_code": "CS101"}]\n```'
        rows = _parse_rows(content)
        assert len(rows) == 1
        assert rows[0]["course_code"] == "CS101"

    def test_with_surrounding_text(self):
        content = '识别结果如下：[{"course_code": "CS101"}] 以上是全部'
        rows = _parse_rows(content)
        assert len(rows) == 1

    def test_no_json_returns_empty(self):
        assert _parse_rows("无法识别") == []

    def test_non_list_returns_empty(self):
        assert _parse_rows('{"a": 1}') == []


class TestRecognizeDegradation:
    async def test_empty_key_degrades(self, monkeypatch):
        from app.config import settings

        monkeypatch.setattr(settings, "zhipu_api_key", "")
        result = await recognize_schedule_photo("aGVsbG8=")
        assert result["success"] is False
        assert "ZHIPU_API_KEY" in result["error"]
        assert result["rows"] == []
