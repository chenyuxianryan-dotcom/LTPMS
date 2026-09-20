package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_project_member")
/**
 * 课题成员实体（带入即快照，课程名单变更不回写）。
 */
public class LabProjectMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long studentId;
    /** CREATE / MANUAL / COURSE_SNAPSHOT / AUTO_SNAPSHOT */
    private String source;
    private LocalDateTime joinedAt;
}
