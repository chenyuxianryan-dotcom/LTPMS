package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
/**
 * 学期请求 DTO。
 */
public class SemesterRequest {

    @NotBlank(message = "学年不能为空")
    private String academicYear;

    @NotNull(message = "学期序号不能为空")
    private Integer semesterNum;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
