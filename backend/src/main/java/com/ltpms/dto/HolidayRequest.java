package com.ltpms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
/**
 * 节假日新增请求，支持日期区间批量展开（逐日一行落库）。
 */
public class HolidayRequest {

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @NotBlank(message = "节假日名称不能为空")
    private String name;
}
