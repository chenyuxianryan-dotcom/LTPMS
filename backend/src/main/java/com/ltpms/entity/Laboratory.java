package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("laboratory")
/**
 * 实验室实体。
 */
public class Laboratory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String labCode;
    private String labName;
    private String manager;
    private Integer deviceCount;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
