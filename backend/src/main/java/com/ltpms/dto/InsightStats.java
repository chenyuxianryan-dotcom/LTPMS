package com.ltpms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据智能聚合结果 DTO（就绪度体检 / 利用率热力图 / 异常预警 / 冲突检测）。
 * 统一由 InsightService 产出，供前端仪表盘、阿实 Agent 共用，避免各建一套查询。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightStats {

    /** 就绪度体检报告（红黄绿三态课程） */
    private List<ReadinessItem> readiness;

    /** 实验室利用率热力图（各实验室占用率） */
    private List<UtilizationItem> utilization;

    /** 异常预警事件列表 */
    private List<AnomalyItem> anomalies;

    /** 排课冲突列表 */
    private List<ConflictItem> conflicts;

    /**
     * 就绪度体检项：课程 + 三态结论。
     * status: GREEN=已建实验项目(就绪) / RED=已排课未建实验项目(待完善)。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadinessItem {
        private Long courseId;
        private String courseCode;
        private String courseName;
        private String teacherName;
        private int experimentCount;
        private String status;
        private String advice;
    }

    /**
     * 实验室利用率项：实验室 + 排课占用统计。
     * utilization = 已排课时段数（占用度），前端据此渲染红黄绿热力。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UtilizationItem {
        private Long labId;
        private String labCode;
        private String labName;
        private int scheduleCount;
        private int timeSlotCount;
    }

    /**
     * 异常预警项。
     * type: OVERLOAD=超排(同实验室同时段多课) / IDLE=闲置(整学期无排课) / HOURS=课时异常。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyItem {
        private String type;
        private String severity;
        private String target;
        private String detail;
    }

    /**
     * 排课冲突项：同一资源（实验室/教师）在同一时段被多门课占用。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictItem {
        /** LAB=实验室冲突 / TEACHER=教师冲突 */
        private String resourceType;
        private String resourceName;
        private int dayOfWeek;
        private int startSection;
        private int endSection;
        private String overlapWeeks;
        private List<String> courseCodes;
    }
}
