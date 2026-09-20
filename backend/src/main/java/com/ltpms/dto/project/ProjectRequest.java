package com.ltpms.dto.project;

import lombok.Data;

/**
 * 课题新建/编辑请求（M-Q7 收口：自建课题仅独立存在，课程/学期由 SYSTEM 容器承载）。
 */
@Data
public class ProjectRequest {

    private String projectName;
    private String description;
}
