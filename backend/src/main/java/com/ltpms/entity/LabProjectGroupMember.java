package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_project_group_member")
/**
 * 分组组员关系实体（同一实验下一人只属一组，由 ProjectGroupService 保证）。
 */
public class LabProjectGroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;
    private Long studentId;
    private LocalDateTime createdAt;
}
