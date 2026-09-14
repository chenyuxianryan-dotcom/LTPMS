package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("lab_open_exception")
/**
 * 实验室开放例外日实体（临时闭馆/检修期）。
 */
public class LabOpenException {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String labCode;
    private Long semesterId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LocalDateTime createdAt;
}
