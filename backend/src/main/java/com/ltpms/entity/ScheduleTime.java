package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("schedule_time")
/**
 * 课表时间段实体。
 */
public class ScheduleTime {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scheduleId;
    private Integer dayOfWeek;
    private Integer startSection;
    private Integer endSection;
    private String weekRange;
    private String weekType;
}
