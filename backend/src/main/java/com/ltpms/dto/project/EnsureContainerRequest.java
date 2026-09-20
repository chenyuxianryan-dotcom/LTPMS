package com.ltpms.dto.project;

import lombok.Data;

/**
 * 课程容器懒生成请求（R1：读侧显式 ensure，幂等 GET-or-create）。
 */
@Data
public class EnsureContainerRequest {

    private Long courseId;
}
