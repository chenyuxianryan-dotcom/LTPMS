package com.ltpms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
/**
 * 实验室开放时段单条请求（与 schedule_time 同构：星期 + 节次 + 周次 + 单双周）。
 */
public class LabOpenTimeRequest {

    @NotBlank(message = "实验室编号不能为空")
    private String labCode;

    @NotNull(message = "学期不能为空")
    private Long semesterId;

    @NotNull(message = "星期不能为空")
    @Min(value = 1, message = "星期取值为 1-7")
    @Max(value = 7, message = "星期取值为 1-7")
    private Integer dayOfWeek;

    @NotNull(message = "起始节次不能为空")
    @Min(value = 1, message = "节次取值为 1-12")
    @Max(value = 12, message = "节次取值为 1-12")
    private Integer startSection;

    @NotNull(message = "结束节次不能为空")
    @Min(value = 1, message = "节次取值为 1-12")
    @Max(value = 12, message = "节次取值为 1-12")
    private Integer endSection;

    @NotBlank(message = "周次范围不能为空")
    private String weekRange;

    /** 全周/单周/双周，默认 ALL */
    private String weekType = "ALL";

    /** 开放对象：全校/本院/指定专业/教师专用，默认全校 */
    private String audience = "全校";

    /** 1=启用 0=停用，默认 1 */
    private Integer enabled = 1;

    private String remark;
}
