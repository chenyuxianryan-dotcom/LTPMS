package com.ltpms.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 教师列表视图对象，脱敏后返回（不含密码、角色、登录失败次数、锁定时间等内部字段）。
 */
public class TeacherVO {

    private Long id;
    private String username;
    private String realName;
    private String department;
    private String phone;
    /** 头像：preset:avatar-XX（内置）或 /api/v1/files/avatar/x.png（上传）；空=按用户 ID 取默认 */
    private String avatar;
    private Integer status;
    private Integer isDefaultPwd;
    private LocalDateTime createdAt;
}
