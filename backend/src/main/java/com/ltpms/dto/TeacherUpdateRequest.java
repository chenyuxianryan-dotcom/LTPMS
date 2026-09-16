package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 编辑教师基础信息请求 DTO（工号不可修改）。
 */
public class TeacherUpdateRequest {

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private String department;

    private String phone;
}
