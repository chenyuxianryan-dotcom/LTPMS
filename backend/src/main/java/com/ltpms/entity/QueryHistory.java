package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("query_history")
/**
 * 查询历史实体。
 */
public class QueryHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String question;
    private String generatedSql;
    private String resultSummary;
    private Integer success;
    private String role;
    private LocalDateTime createdAt;
}
