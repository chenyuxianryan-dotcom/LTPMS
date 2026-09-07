package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("semester")
/**
 * 学期实体。
 */
public class Semester {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String academicYear;
    private Integer semesterNum;
    private String semesterName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private LocalDateTime createdAt;
}
