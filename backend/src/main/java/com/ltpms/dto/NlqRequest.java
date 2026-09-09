package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
/**
 * 自然语言查询请求 DTO，同时承载单轮 /query 与多轮 /agent 的请求。
 */
public class NlqRequest {

    @NotBlank(message = "查询问题不能为空")
    private String question;

    /** 多轮对话历史（可选），元素为 {"role": "user"/"assistant", "content": "..."} */
    private List<Map<String, Object>> history;
}
