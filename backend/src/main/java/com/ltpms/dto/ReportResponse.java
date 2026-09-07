package com.ltpms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 报表响应 DTO。
 */
public class ReportResponse {

    private Summary summary;
    private List<Detail> detail;
    private String detailScope; // ALL / SELF

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long courseCount;
        private long experimentCount;
        private BigDecimal totalPersonHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private String labCode;
        private String labName;
        private String courseCode;
        private String courseName;
        private String teacherName;
        private long experimentCount;
        private BigDecimal personHours;
    }
}
