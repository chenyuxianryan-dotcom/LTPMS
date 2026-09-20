package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_student")
/**
 * 课程选课名单行（平行班粒度：course 一行按班存，r2-B4）。
 * 容器懒生成时按此表全量快照为 lab_project_member（source=AUTO_SNAPSHOT）。
 */
public class CourseStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;
    private Long studentId;
    /** 上课班级名——平行班区分 */
    private String className;
    /** IMPORT / MANUAL */
    private String source;
    private LocalDateTime joinedAt;
}
