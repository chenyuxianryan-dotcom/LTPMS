package com.ltpms.dto;

import lombok.Data;

/**
 * 实验项目报告 PDF 导出请求 DTO（教学闭环收口 T09）。
 *
 * <p>沿用叙事报告「所见即所得」口径：前端把已生成的六节叙述正文原样提交，后端只做排版渲染，
 * 不重新调用大模型。附录「成员完成情况表」由后端从库直出（姓名/学号/完成情况），
 * 不经前端、不进模型上下文（隐私红线）。</p>
 */
@Data
public class ProjectReportExportRequest {

    /** 文档标题，可空（后端兜底「实验项目报告 · 课题名」） */
    private String title;

    /** 六节叙述正文（含换行与小标题），由 /nlq/report 生成后原样回传 */
    private String text;

    /** 数据截至时间（缓存命中时为缓存创建时间），可空 */
    private String generatedAt;
}
