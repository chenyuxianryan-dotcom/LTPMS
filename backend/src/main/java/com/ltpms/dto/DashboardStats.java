package com.ltpms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 首页仪表盘统计数据 DTO。
 */
public class DashboardStats {

    private Long labCount;
    private Long courseCount;
    private Long experimentCount;
    private Long teacherCount;
    private Long myCourseCount;
    private Long myExperimentCount;
    private List<RecentQuery> recentQueries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentQuery {
        private String question;
        private String time;
    }
}
