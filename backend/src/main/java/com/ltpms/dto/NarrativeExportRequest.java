package com.ltpms.dto;

import lombok.Data;

import java.util.List;

/**
 * 叙事报告导出请求 DTO。
 *
 * <p>前端把已在页面上生成的叙事内容（标题、学期、正文、统计数字）原样提交，
 * 后端只做排版渲染，不重新调用大模型，保证导出内容与页面所见一致。</p>
 */
@Data
public class NarrativeExportRequest {

    /** 文档标题，如「全校教学总结」 */
    private String title;

    /** 学期/学年标签，如「2026-2027学年第1学期」，可为空 */
    private String semester;

    /** 叙事正文（可能包含换行） */
    private String text;

    /** 统计数据附表（指标名 → 数值），可为空 */
    private List<NumberItem> numbers;

    @Data
    public static class NumberItem {
        private String label;
        private String value;
    }
}
