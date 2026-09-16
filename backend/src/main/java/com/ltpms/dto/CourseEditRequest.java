package com.ltpms.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程聚合行编辑请求（教师本人/管理员）：字段为 null 表示不修改。
 */
@Data
public class CourseEditRequest {

    private String courseName;
    private String major;
    private BigDecimal totalHours;
}
