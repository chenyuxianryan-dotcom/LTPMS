package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_dataset_version")
/**
 * 数据集版本实体（版本链只增不覆盖）。
 * storedPath 为服务端生成 UUID 文件名相对路径，绝不用用户原名拼路径。
 */
public class LabDatasetVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;
    private Integer versionNo;
    private String storedPath;
    private String originalFilename;
    private Long fileSize;
    /** 魔数校验结果类型，不信后缀 */
    private String fileType;
    private Long uploaderId;
    private String note;
    private Integer deleted;
    private LocalDateTime createdAt;
}
