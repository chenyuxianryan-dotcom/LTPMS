package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 头像更新请求 DTO。
 */
@Data
public class AvatarRequest {

    /**
     * 头像标识：preset:avatar-XX（内置默认头像）。
     */
    @NotBlank(message = "头像标识不能为空")
    private String avatar;
}
