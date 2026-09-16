package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("schedule_revision")
/**
 * 课表/课程修订留痕实体（字段级、追加式历史，行删除后记录保留）。
 */
public class ScheduleRevision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;
    private Long targetId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String sourceRole;
    private Long operatorId;
    private LocalDateTime createdAt;
}
