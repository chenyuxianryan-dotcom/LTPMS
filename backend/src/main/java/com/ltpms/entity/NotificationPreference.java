package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification_preference")
public class NotificationPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 通知类型常量：MORNING_BRIEF / DEADLINE_WARNING / ... */
    private String notificationType;

    /** 1=开启 0=关闭 */
    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
