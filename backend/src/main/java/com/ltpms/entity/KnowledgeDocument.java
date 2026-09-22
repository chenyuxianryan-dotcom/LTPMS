package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档（制度与流程 / 课程资料）。
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    /** 分类常量：制度与流程（管理员维护） */
    public static final String CATEGORY_POLICY = "POLICY";
    /** 分类常量：课程资料（教师自助上传） */
    public static final String CATEGORY_COURSE = "COURSE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** POLICY / COURSE */
    private String category;

    /** 归属课程 course.id（仅 COURSE 类；POLICY 为 null） */
    private Long courseId;

    private String originalFilename;

    /** 落盘文件名（uploads/kb 下，UUID 命名） */
    private String storedPath;

    /** docx / pdf / txt / md */
    private String fileType;

    private Long fileSize;

    /** 切分段数（含向量） */
    private Integer chunkCount;

    private Long uploaderId;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
