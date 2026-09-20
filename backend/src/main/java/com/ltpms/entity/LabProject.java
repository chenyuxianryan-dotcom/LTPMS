package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_project")
/**
 * 课题实体（UI 一律自称「课题」；teacher_id 为行级隔离锚点）。
 */
public class LabProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectCode;
    private String projectName;
    private Long teacherId;
    /** 可选归类标签，不产生成员关系 */
    private Long courseId;
    private Long semesterId;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
