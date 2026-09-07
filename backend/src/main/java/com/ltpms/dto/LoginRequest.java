package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 登录请求 DTO。
 */
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
