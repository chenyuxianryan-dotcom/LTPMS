package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("schedule")
/**
 * 课表实体。
 */
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long semesterId;
    private String courseCode;
    private String courseName;
    private BigDecimal totalHours;
    private Long teacherId;
    private String labCode;
    private String classNames;
    private Integer studentCount;
    private String rawScheduleText;
    private LocalDateTime createdAt;
}
