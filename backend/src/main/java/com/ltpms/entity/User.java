package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
/**
 * 用户实体。
 */
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String realName;
    private String role;
    private String department;
    private String phone;
    /** 头像：preset:avatar-XX（内置默认头像）或 /api/v1/files/avatar/xxx.png（本地上传），NULL=未设置 */
    private String avatar;
    private Integer status;
    private Integer isDefaultPwd;
    private Integer failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
