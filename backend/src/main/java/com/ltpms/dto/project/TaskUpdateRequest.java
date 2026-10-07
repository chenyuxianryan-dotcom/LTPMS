package com.ltpms.dto.project;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务编辑请求：字段为 null 表示不修改；deadline 等修改自动写 LAB_TASK 修订留痕（r2-B2）。
 */
@Data
public class TaskUpdateRequest {

    private String title;
    private String description;
    private LocalDateTime deadline;
    private Long datasetId;
    /** 关联实验（T03）：null=不修改；0=清空关联；>0=改挂（校验同新建） */
    private Long experimentId;
}
