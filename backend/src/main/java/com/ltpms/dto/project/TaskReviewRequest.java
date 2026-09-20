package com.ltpms.dto.project;

import lombok.Data;

/**
 * 任务验收请求：decision=ACCEPT/REJECT，驳回必须附意见。
 */
@Data
public class TaskReviewRequest {

    private String decision;
    private String comment;
}
