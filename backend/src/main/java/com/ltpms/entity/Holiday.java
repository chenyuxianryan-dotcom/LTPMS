package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("holiday")
/**
 * 节假日实体。
 */
public class Holiday {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate holidayDate;
    private String name;
    private LocalDateTime createdAt;
}
