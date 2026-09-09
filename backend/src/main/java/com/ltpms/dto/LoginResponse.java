package com.ltpms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 登录响应 DTO。
 */
public class LoginResponse {

    private String token;
    private UserInfo user;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String realName;
        private String role;
        private Integer isDefaultPwd;
        /** 头像标识（preset:avatar-XX 或上传文件 URL），可为空 */
        private String avatar;
    }
}
