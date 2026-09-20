package com.ltpms.dto.project;

import lombok.Data;

/**
 * 课题新建/编辑请求（courseId/semesterId 为可选归类标签，不产生成员关系）。
 */
@Data
public class ProjectRequest {

    private String projectName;
    private String description;
    private Long courseId;
    private Long semesterId;
}
