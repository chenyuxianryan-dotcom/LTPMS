package com.ltpms.dto.project;

import lombok.Data;

/**
 * 数据集改名/描述请求（创建者+归属老师）。
 */
@Data
public class DatasetUpdateRequest {

    private String datasetName;
    private String description;
}
