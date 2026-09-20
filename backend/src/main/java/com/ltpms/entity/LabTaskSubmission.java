package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_task_submission")
/**
 * 任务提交实体（追加式：驳回重提=新行，历史保留）。
 * content/reviewComment 仅本人+归属老师+ADMIN 可见（后端字段裁剪）。
 */
public class LabTaskSubmission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long submitterStudentId;
    private String content;
    private Long datasetVersionId;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private String reviewComment;
    private LocalDateTime reviewedAt;
}
