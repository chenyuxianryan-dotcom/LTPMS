package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course")
/**
 * 课程实体。
 */
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String courseCode;
    private String courseName;
    private Long semesterId;
    private Long teacherId;
    private BigDecimal totalHours;
    private String major;
    private LocalDateTime createdAt;

    /** 授课教师姓名（非表字段，列表响应补充） */
    @TableField(exist = false)
    private String teacherName;

    /** 学期名称（非表字段，列表响应补充） */
    @TableField(exist = false)
    private String semesterName;
}
