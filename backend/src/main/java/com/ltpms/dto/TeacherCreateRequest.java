package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 新增教师请求 DTO。
 */
public class TeacherCreateRequest {

    @NotBlank(message = "工号不能为空")
    private String username;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private String department;

    private String phone;
}
