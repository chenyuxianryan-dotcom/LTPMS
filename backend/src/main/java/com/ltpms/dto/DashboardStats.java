package com.ltpms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 首页仪表盘统计数据 DTO。
 * 总数字段用于副标题汇总；Cell 明细用于概览页方格图可视化（used=true 蓝色 / false 灰色）。
 */
public class DashboardStats {

    // ==================== 总数（管理员视角） ====================
    private Long labCount;
    private Long courseCount;
    private Long experimentCount;
    private Long teacherCount;

    // ==================== 总数（教师视角） ====================
    private Long myCourseCount;
    private Long myExperimentCount;

    private List<RecentQuery> recentQueries;

    // ==================== 方格图明细（管理员视角，绑定当前学期） ====================
    /** 实验室方格：used=当前学期有排课 */
    private List<Cell> labCells;
    /** 课程方格：used=该课程已建实验项目（开课 vs 实验项目就绪度） */
    private List<Cell> courseCells;
    /** 实验项目方格：格=实验项目本体，used=已建成 */
    private List<Cell> experimentCells;
    /** 教师方格：used=当前学期有排课 */
    private List<Cell> teacherCells;

    // ==================== 方格图明细（教师视角，仅本人数据） ====================
    /** 我的课程方格：used=该课程已建实验项目（就绪度） */
    private List<Cell> myCourseCells;
    /** 我的实验项目方格：used=所属课程在当前学期 */
    private List<Cell> myExperimentCells;
    /** 我的排课周历：每周一格，used=该周有排课 */
    private List<WeekCell> weekCells;

    /**
     * 最近查询记录。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentQuery {
        private String question;
        private String time;
        /** 查询是否成功（1=成功 0=失败） */
        private Integer success;
    }

    /**
     * 方格图单元：一个实体对应一格。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cell {
        /** 实体 ID */
        private Long id;
        /** 方格内短文本（编号 / 首字） */
        private String code;
        /** 完整名称（tooltip） */
        private String name;
        /** 是否使用中（蓝色） */
        private boolean used;
        /** 附加信息（tooltip 第二行） */
        private String extra;
    }

    /**
     * 排课周历单元：一周一格。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekCell {
        /** 周次（从 1 开始） */
        private int week;
        /** 该周是否有排课 */
        private boolean used;
    }
}
