"""共享文本生成工具行为测试。

聚焦：generate_text 委托 call_deepseek、异常兜底、材料清单生成。
"""

from app.services import text_generation


class TestGenerateText:
    async def test_generate_text_delegates(self, monkeypatch):
        async def fake_call(messages):
            return "生成结果文本"

        monkeypatch.setattr(text_generation, "call_deepseek", fake_call)
        result = await text_generation.generate_text("生成一段话")
        assert result["success"] is True
        assert result["text"] == "生成结果文本"

    async def test_generate_text_error_fallback(self, monkeypatch):
        async def fake_call(messages):
            raise RuntimeError("模型超时")

        monkeypatch.setattr(text_generation, "call_deepseek", fake_call)
        result = await text_generation.generate_text("生成一段话")
        assert result["success"] is False
        assert "模型超时" in result["error"]

    async def test_generate_material_list_contains_advice(self, monkeypatch):
        async def fake_call(messages):
            # 校验 prompt 里带入了实验信息
            assert "密度计实验" in messages[0]["content"]
            return "密度计 | 1 | 建议清单"

        monkeypatch.setattr(text_generation, "call_deepseek", fake_call)
        result = await text_generation.generate_material_list("实验项目：密度计实验")
        assert result["success"] is True
        assert "建议清单" in result["text"]
