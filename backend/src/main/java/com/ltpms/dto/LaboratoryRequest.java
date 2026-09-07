package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
/**
 * 实验室请求 DTO。
 */
public class LaboratoryRequest {

    @NotBlank(message = "实验室编号不能为空")
    private String labCode;

    @NotBlank(message = "实验室名称不能为空")
    private String labName;

    private String manager;

    @NotNull(message = "设备数不能为空")
    private Integer deviceCount;

    private Integer status;
}
