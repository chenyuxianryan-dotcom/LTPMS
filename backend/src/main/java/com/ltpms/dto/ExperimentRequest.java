package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
/**
 * 实验项目请求 DTO。
 */
public class ExperimentRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @NotBlank(message = "实验名称不能为空")
    private String experimentName;

    @NotBlank(message = "实验类别不能为空")
    private String category;

    @NotBlank(message = "实验类型不能为空")
    private String type;

    @NotBlank(message = "所属学科不能为空")
    private String subject;

    @NotBlank(message = "实验要求不能为空")
    private String requirement;

    @NotBlank(message = "实验者类别不能为空")
    private String experimenterType;

    @NotNull(message = "每组人数不能为空")
    private Integer groupSize;

    @NotNull(message = "实验学时数不能为空")
    private BigDecimal hours;

    private Integer sortOrder;
}
