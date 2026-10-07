package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_project_task")
/**
 * 课题任务实体（一任务一负责人；状态机 NOT_STARTED/IN_PROGRESS/SUBMITTED/ACCEPTED/REJECTED/CLOSED）。
 * deleted 触发 MyBatis-Plus 全局逻辑删除，查询自动剔除软删行。
 */
public class LabProjectTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String title;
    private String description;
    private Long assigneeStudentId;
    private String status;
    private LocalDateTime deadline;
    private Long datasetId;
    /** 关联实验目录行（T03 深桥接；可空，删实验时置空） */
    private Long experimentId;
    private Integer deleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
