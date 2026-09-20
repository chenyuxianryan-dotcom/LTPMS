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
}
