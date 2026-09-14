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
 * 单间实验室周网格：7 天 × 12 节，四色态（占用 / 开放空闲 / 未开放 / 闭馆）+ 闭馆日集合。
 */
public class LabOpenGrid {

    private Long semesterId;
    private String labCode;
    private String labName;
    private LocalDate week1StartDate;
    private int totalWeeks;

    /** 生效周次（未指定时取当前周并夹取到学期范围内） */
    private Integer week;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;

    private List<Cell> cells;

    /** 学期范围内的闭馆日集合（节假日 + 本实验室例外日） */
    private List<ClosedDate> closedDates;

    public static final String STATE_OCCUPIED = "OCCUPIED";
    public static final String STATE_OPEN = "OPEN";
    public static final String STATE_NOT_OPEN = "NOT_OPEN";
    public static final String STATE_CLOSED = "CLOSED";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cell {

        private int dayOfWeek;
        private int section;
        private String state;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClosedDate {

        private LocalDate date;
        private String name;

        /** HOLIDAY=节假日 EXCEPTION=实验室例外日 */
        private String type;

        private int week;
        private int dayOfWeek;
    }
}
