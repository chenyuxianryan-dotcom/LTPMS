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
 * 今日开放实验室：供仪表盘小块与教师端展示（含节假日/例外日闭馆判定）。
 */
public class LabOpenToday {

    private Long semesterId;
    private String semesterName;
    private LocalDate date;
    private Integer dayOfWeek;

    /** 今日所在周次；inSemester=false 时为 null */
    private Integer weekNo;

    /** 今日是否落在学期周次范围内 */
    private boolean inSemester;

    /** 今日是否为节假日（闭馆） */
    private boolean holiday;
    private String holidayName;

    private List<LabItem> labs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabItem {

        private String labCode;
        private String labName;
        private String audience;

        /** 合并后的节次文本，如 "1-8节"、"1-4、9-10节" */
        private String sectionText;
    }
}
