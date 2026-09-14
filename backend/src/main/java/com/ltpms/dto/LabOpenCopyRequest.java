package com.ltpms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
/**
 * 开放计划「从上学期复制」请求：按选中实验室覆盖式复制，幂等。
 */
public class LabOpenCopyRequest {

    @NotNull(message = "源学期不能为空")
    private Long fromSemesterId;

    @NotNull(message = "目标学期不能为空")
    private Long toSemesterId;

    /** 要复制的实验室编号；为空时复制源学期内全部已有计划的实验室 */
    private List<String> labCodes;
}
