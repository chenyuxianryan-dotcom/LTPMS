package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dictionary")
/**
 * 字典实体。
 */
public class Dictionary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer sortOrder;
    private Integer status;
}
