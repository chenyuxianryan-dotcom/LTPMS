package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
}
