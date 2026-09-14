package com.ltpms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
/**
 * 开放计划批量覆盖式设置请求：对选中实验室先删后插，语义与课表「整学期覆盖」一致。
 */
public class LabOpenBatchRequest {

    @NotNull(message = "学期不能为空")
    private Long semesterId;

    @NotEmpty(message = "请至少选择一间实验室")
    private List<String> labCodes;

    @NotEmpty(message = "请至少设置一个时段")
    @Valid
    private List<Slot> slots;

    /** 开放对象：全校/本院/指定专业/教师专用，默认全校 */
    private String audience = "全校";

    private String remark;

    @Data
    /**
     * 批量设置的统一时段模板。
     */
    public static class Slot {

        @NotNull(message = "星期不能为空")
        @Min(value = 1, message = "星期取值为 1-7")
        @Max(value = 7, message = "星期取值为 1-7")
        private Integer dayOfWeek;

        @NotNull(message = "起始节次不能为空")
        @Min(value = 1, message = "节次取值为 1-12")
        @Max(value = 12, message = "节次取值为 1-12")
        private Integer startSection;

        @NotNull(message = "结束节次不能为空")
        @Min(value = 1, message = "节次取值为 1-12")
        @Max(value = 12, message = "节次取值为 1-12")
        private Integer endSection;

        @NotBlank(message = "周次范围不能为空")
        private String weekRange;

        /** 全周/单周/双周，默认 ALL */
        private String weekType = "ALL";
    }
}
