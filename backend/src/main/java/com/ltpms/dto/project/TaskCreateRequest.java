package com.ltpms.dto.project;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务新建请求：assigneeStudentIds 多于 1 人时服务端复制为多条独立任务（Q4）。
 */
@Data
public class TaskCreateRequest {

    private String title;
    private String description;
    private List<Long> assigneeStudentIds;
    private LocalDateTime deadline;
    private Long datasetId;
    /** 关联实验目录行（T03；可空；非空时须属于课题所属课程容器目录，独立课题一律 400） */
    private Long experimentId;
}
