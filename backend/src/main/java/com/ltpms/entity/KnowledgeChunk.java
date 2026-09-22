package com.ltpms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档分段。无逻辑删除列：文档软删时分段物理清理。
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    /** 段序号（同一文档内从 1 起） */
    private Integer seq;

    /** 所属标题（切分时保留，用于出处展示） */
    private String heading;

    private String content;

    /** 向量：float32 小端序字节串（1024 维 = 4096 字节） */
    private byte[] embedding;

    private LocalDateTime createdAt;
}
