package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_project_group")
/**
 * 课题实验分组实体（每个实验各一套分组，每组人数取自 experiment.group_size）。
 */
public class LabProjectGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long experimentId;
    /** 自动分组为「第N组」，手动建组可由教师自定义 */
    private String groupName;
    /** 建组时快照的每组人数，用于目录「每组人数」变更后的一致性提示 */
    private Integer baselineSize;
    private LocalDateTime createdAt;
}
