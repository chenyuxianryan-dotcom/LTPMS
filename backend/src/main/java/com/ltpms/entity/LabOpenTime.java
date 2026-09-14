package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_open_time")
/**
 * 实验室开放时段实体（与 schedule_time 同构，挂在学期）。
 */
public class LabOpenTime {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String labCode;
    private Long semesterId;
    private Integer dayOfWeek;
    private Integer startSection;
    private Integer endSection;
    private String weekRange;
    private String weekType;
    private String audience;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
