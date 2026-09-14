package com.ltpms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
/**
 * 设置学期第 1 周起始日请求。
 */
public class Week1StartDateRequest {

    @NotNull(message = "第 1 周起始日不能为空")
    private LocalDate week1StartDate;
}
