package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("experiment")
/**
 * 实验项目实体。
 */
public class Experiment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String experimentCode;
    private Long courseId;
    private String experimentName;
    private String category;
    private String type;
    private String subject;
    private String requirement;
    private String experimenterType;
    private Integer groupSize;
    private BigDecimal hours;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
