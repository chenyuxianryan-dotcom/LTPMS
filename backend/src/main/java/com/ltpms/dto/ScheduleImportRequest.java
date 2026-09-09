package com.ltpms.dto;

import lombok.Data;

import java.util.List;

/**
 * 课表结构化导入请求（课表照片识别草稿落库用）。
 */
@Data
public class ScheduleImportRequest {

    private List<ScheduleImportRow> rows;
}
