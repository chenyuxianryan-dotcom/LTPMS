package com.ltpms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 实验室占用总览：每间实验室的开放节时 / 占用节时 / 利用率 / 矛盾数 / 是否已配置。
 * 节时口径 = Σ(节数 × 生效周数)，与利用率（D13）同源；不含节假日/例外日扣减。
 */
public class LabOpeningOverview {

    private Long semesterId;
    private LocalDate week1StartDate;
    private int totalWeeks;
    private List<LabItem> labs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabItem {

        private Long labId;
        private String labCode;
        private String labName;

        /** 是否存在 enabled=1 的开放时段（全停用视为未开放） */
        private boolean planned;

        /** 开放对象汇总，多值以「、」连接 */
        private String audience;

        /** 开放节时 = Σ(开放节数 × 生效周数) */
        private int openSlotHours;

        /** 占用节时 = Σ(排课节数 × 生效周数) */
        private int occupiedSlotHours;

        /** 利用率 %（占用÷开放，1 位小数）；未开放时为 null，不参与红黄绿评分 */
        private Double rate;

        /** 排课与开放三维（星期+节次+周次）不重叠的时段数 */
        private int conflictCount;
    }
}
