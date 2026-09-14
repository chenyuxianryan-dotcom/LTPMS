package com.ltpms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 排课与开放矛盾明细（三维不重叠），供占用总览计数与数据智能 NOT_OPEN 预警共用。
 */
public class LabConflict {

    private String labCode;
    private String labName;
    private String courseName;
    private String classNames;
    private Integer dayOfWeek;
    private Integer startSection;
    private Integer endSection;

    /** 生效周次文本，如 "1-16周"、"1-15周（单周）" */
    private String weekText;
}
