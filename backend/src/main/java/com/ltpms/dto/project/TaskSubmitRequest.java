package com.ltpms.dto.project;

import lombok.Data;

/**
 * 任务提交请求：文字说明 + 可选挂一个数据集版本作为交付物。
 */
@Data
public class TaskSubmitRequest {

    private String content;
    private Long datasetVersionId;
}
