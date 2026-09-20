package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student")
/**
 * 学生档案实体（与 user 一对一；登录名=学号，role=STUDENT）。
 */
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String studentNo;
    private String className;
    private String major;
    private LocalDateTime createdAt;
}
