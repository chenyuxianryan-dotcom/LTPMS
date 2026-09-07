package com.ltpms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
/**
 * 教师状态请求 DTO。
 */
public class TeacherStatusRequest {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
