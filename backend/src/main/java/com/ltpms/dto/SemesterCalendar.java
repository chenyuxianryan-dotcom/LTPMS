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
 * 学期周历：第 1 周起始日与周次↔日期映射表。
 */
public class SemesterCalendar {

    private Long semesterId;
    private String semesterName;
    private LocalDate startDate;
    private LocalDate endDate;

    /** 第 1 周起始日；未显式配置时回退为学期开始日期 */
    private LocalDate week1StartDate;

    /** 周历是否已显式配置（week1_start_date 非空） */
    private boolean configured;

    private int totalWeeks;
    private List<WeekItem> weeks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekItem {

        private int week;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> holidayNames;
    }
}
