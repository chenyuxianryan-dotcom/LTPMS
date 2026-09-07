package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 自然语言查询请求 DTO。
 */
public class NlqRequest {

    @NotBlank(message = "查询问题不能为空")
    private String question;
}
