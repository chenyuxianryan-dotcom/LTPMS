package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
/**
 * 实验室开放例外日请求（临时闭馆/检修期，含起止日期）。
 */
public class LabOpenExceptionRequest {

    @NotBlank(message = "实验室编号不能为空")
    private String labCode;

    @NotNull(message = "学期不能为空")
    private Long semesterId;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private String reason;
}
