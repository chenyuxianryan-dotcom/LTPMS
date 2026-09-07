package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
/**
 * 字典请求 DTO。
 */
public class DictionaryRequest {

    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    @NotBlank(message = "显示值不能为空")
    private String dictLabel;

    @NotBlank(message = "存储值不能为空")
    private String dictValue;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder;

    private Integer status;
}
