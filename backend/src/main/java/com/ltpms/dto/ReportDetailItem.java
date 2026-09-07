package com.ltpms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
/**
 * 报表明细项 DTO。
 */
public class ReportDetailItem {

    private String labCode;
    private String labName;
    private String courseCode;
    private String courseName;
    private String teacherName;
    private Long experimentCount;
    private BigDecimal personHours;
}
