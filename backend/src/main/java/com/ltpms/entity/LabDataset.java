package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_dataset")
/**
 * 课题数据集实体（项目内同名→409 二选一，不静默挂链）。
 */
public class LabDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String datasetName;
    private String description;
    private Integer latestVersion;
    private Long createdBy;
    private Integer deleted;
    private LocalDateTime createdAt;
}
