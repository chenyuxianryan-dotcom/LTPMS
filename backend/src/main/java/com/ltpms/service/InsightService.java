package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.dto.DashboardStats;
import com.ltpms.dto.InsightStats;
import com.ltpms.entity.*;
import com.ltpms.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据智能聚合服务（统一入口）。
 *
 * 把阶段 2 的多类数据智能（就绪度体检 / 利用率热力图 / 异常预警 / 排课冲突检测）
 * 归入本服务统一产出，前端仪表盘与阿实 Agent 共用，避免各建一个 controller 重写查询。
 *
 * 就绪度判定复用 Dashboard V3 已收敛的语义（课程卡 = 开课 vs 实验项目就绪度），
 * 本服务只做"导出为红黄绿体检报告"，不另造一套判定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final DashboardService dashboardService;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleTimeMapper scheduleTimeMapper;
    private final LaboratoryMapper laboratoryMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final SemesterService semesterService;

    /**
     * 课程就绪度体检报告：复用 Dashboard V3 的就绪度语义，产出红黄绿三态课程清单。
     *
     * @param userId 当前用户 ID
     * @param role   当前角色（ADMIN=全校 / TEACHER=仅本人课程）
     */
    public List<InsightStats.ReadinessItem> readinessReport(Long userId, String role) {
        boolean admin = "ADMIN".equals(role);
        DashboardStats stats = admin ? dashboardService.adminStats() : dashboardService.teacherStats(userId);
        List<DashboardStats.Cell> cells = admin ? stats.getCourseCells() : stats.getMyCourseCells();
        if (cells == null) {
            return Collections.emptyList();
        }

        List<InsightStats.ReadinessItem> result = new ArrayList<>();
        for (DashboardStats.Cell cell : cells) {
            // used=true 表示该课程已建实验项目（绿）；false 表示已排课未建实验项目（红）
            boolean ready = cell.isUsed();
            int expCount = extractExperimentCount(cell.getExtra());
            String teacherName = extractTeacherName(cell.getExtra());
            String status = ready ? "GREEN" : "RED";
            String advice = ready
                    ? "已建 " + expCount + " 个实验项目，就绪"
                    : "已排课但未建实验项目，建议尽快创建";
            result.add(new InsightStats.ReadinessItem(
                    cell.getId(), cell.getCode(), cell.getName(), teacherName,
                    expCount, status, advice));
        }
        return result;
    }

    /** 从方格 extra 文本（如 "任课 张三 · 实验项目 2 个"）中提取实验项目数。 */
    private int extractExperimentCount(String extra) {
        if (extra == null) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("实验项目\\s*(\\d+)\\s*个")
                .matcher(extra);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** 从方格 extra 文本中提取任课教师名。 */
    private String extractTeacherName(String extra) {
        if (extra == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("任课\\s*([^·]+)").matcher(extra);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * 实验室利用率热力图：按实验室统计当前学期排课条数与时段占用数。
     * 前端据此渲染红黄绿热力（可读性优先，非涨跌语义）。
     *
     * @param semesterId 学期 ID，为空时取当前学期
     */
    public List<InsightStats.UtilizationItem> utilizationHeatmap(Long semesterId) {
        Long sid = resolveSemesterId(semesterId);
        List<Laboratory> labs = laboratoryMapper.selectList(
                new LambdaQueryWrapper<Laboratory>().eq(Laboratory::getStatus, 1));

        Map<String, List<Schedule>> byLab = sid != null
                ? scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>().eq(Schedule::getSemesterId, sid))
                        .stream().filter(s -> s.getLabCode() != null)
                        .collect(Collectors.groupingBy(Schedule::getLabCode))
                : Collections.emptyMap();

        // 时段数 = 该实验室所有 schedule 的 schedule_time 去重时段数
        List<Schedule> allSchedules = sid != null
                ? scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>().eq(Schedule::getSemesterId, sid))
                : Collections.emptyList();
        Map<Long, Integer> slotCountBySchedule = new HashMap<>();
        if (!allSchedules.isEmpty()) {
            List<ScheduleTime> times = scheduleTimeMapper.selectList(
                    new LambdaQueryWrapper<ScheduleTime>()
                            .in(ScheduleTime::getScheduleId, allSchedules.stream().map(Schedule::getId).toList()));
            for (ScheduleTime st : times) {
                slotCountBySchedule.merge(st.getScheduleId(), slotSpan(st), Integer::sum);
            }
        }

        List<InsightStats.UtilizationItem> result = new ArrayList<>();
        for (Laboratory lab : labs) {
            List<Schedule> labSchedules = byLab.getOrDefault(lab.getLabCode(), Collections.emptyList());
            int slotCount = labSchedules.stream()
                    .mapToInt(s -> slotCountBySchedule.getOrDefault(s.getId(), 0))
                    .sum();
            result.add(new InsightStats.UtilizationItem(
                    lab.getId(), lab.getLabCode(), lab.getLabName(),
                    labSchedules.size(), slotCount));
        }
        return result;
    }

    /**
     * 异常预警：实验室超排（复用冲突检测的实验室维度）、闲置实验室、课时异常课程。
     *
     * @param semesterId 学期 ID，为空时取当前学期
     */
    public List<InsightStats.AnomalyItem> detectAnomalies(Long semesterId) {
        Long sid = resolveSemesterId(semesterId);
        List<InsightStats.AnomalyItem> anomalies = new ArrayList<>();

        // 1. 超排：复用冲突检测的实验室维度结果（同一实验室同时段多课）
        List<InsightStats.ConflictItem> conflicts = detectConflicts(sid);
        conflicts.stream()
                .filter(c -> "LAB".equals(c.getResourceType()))
                .forEach(c -> anomalies.add(new InsightStats.AnomalyItem(
                        "OVERLOAD", "HIGH", c.getResourceName(),
                        "周" + c.getDayOfWeek() + " 第" + c.getStartSection() + "-" + c.getEndSection()
                                + "节 被 " + String.join("、", c.getCourseCodes()) + " 同时占用")));

        // 2. 闲置：当前学期无任何排课的实验室
        List<Schedule> schedules = sid != null
                ? scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>().eq(Schedule::getSemesterId, sid))
                : Collections.emptyList();
        Set<String> usedLabs = schedules.stream().map(Schedule::getLabCode)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        laboratoryMapper.selectList(new LambdaQueryWrapper<Laboratory>().eq(Laboratory::getStatus, 1))
                .forEach(lab -> {
                    if (!usedLabs.contains(lab.getLabCode())) {
                        anomalies.add(new InsightStats.AnomalyItem(
                                "IDLE", "LOW", lab.getLabCode(),
                                "实验室 " + lab.getLabName() + " 本学期暂无排课，处于闲置状态"));
                    }
                });

        // 3. 课时异常：当前学期课程总学时为 0 或缺失
        if (sid != null) {
            courseMapper.selectList(new LambdaQueryWrapper<Course>().eq(Course::getSemesterId, sid))
                    .forEach(course -> {
                        BigDecimal hours = course.getTotalHours();
                        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
                            anomalies.add(new InsightStats.AnomalyItem(
                                    "HOURS", "MEDIUM", course.getCourseCode(),
                                    "课程 " + course.getCourseName() + " 总学时为 0 或未填写"));
                        }
                    });
        }

        return anomalies;
    }

    /**
     * 排课冲突检测：同一实验室或同一教师在同一时段（周几+节次+周次重叠）被多门课占用。
     *
     * @param semesterId 学期 ID，为空时取当前学期
     */
    public List<InsightStats.ConflictItem> detectConflicts(Long semesterId) {
        Long sid = resolveSemesterId(semesterId);
        if (sid == null) {
            return Collections.emptyList();
        }

        List<Schedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>().eq(Schedule::getSemesterId, sid));
        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScheduleTime> times = scheduleTimeMapper.selectList(
                new LambdaQueryWrapper<ScheduleTime>()
                        .in(ScheduleTime::getScheduleId, schedules.stream().map(Schedule::getId).toList()));

        // scheduleId -> schedule
        Map<Long, Schedule> scheduleMap = schedules.stream()
                .collect(Collectors.toMap(Schedule::getId, s -> s, (a, b) -> a));

        // 实验室冲突：labCode + (dayOfWeek, section) -> 课程集合
        Map<String, Map<String, List<Slot>>> labSlots = new HashMap<>();
        // 教师冲突：teacherId + (dayOfWeek, section) -> 课程集合
        Map<Long, Map<String, List<Slot>>> teacherSlots = new HashMap<>();

        for (ScheduleTime st : times) {
            Schedule schedule = scheduleMap.get(st.getScheduleId());
            if (schedule == null) {
                continue;
            }
            int day = st.getDayOfWeek() != null ? st.getDayOfWeek() : 0;
            int start = st.getStartSection() != null ? st.getStartSection() : 0;
            int end = st.getEndSection() != null ? st.getEndSection() : start;
            Set<Integer> weeks = parseWeeks(st.getWeekRange(), st.getWeekType());
            if (day <= 0 || start <= 0) {
                continue;
            }

            String sectionKey = day + "-" + start + "-" + end;
            if (schedule.getLabCode() != null) {
                labSlots.computeIfAbsent(schedule.getLabCode(), k -> new HashMap<>())
                        .computeIfAbsent(sectionKey, k -> new ArrayList<>())
                        .add(new Slot(schedule.getCourseCode(), weeks));
            }
            if (schedule.getTeacherId() != null) {
                teacherSlots.computeIfAbsent(schedule.getTeacherId(), k -> new HashMap<>())
                        .computeIfAbsent(sectionKey, k -> new ArrayList<>())
                        .add(new Slot(schedule.getCourseCode(), weeks));
            }
        }

        List<InsightStats.ConflictItem> conflicts = new ArrayList<>();

        // 实验室冲突
        Map<Long, String> teacherNames = userMapper.selectList(
                        new LambdaQueryWrapper<User>().eq(User::getRole, "TEACHER"))
                .stream().collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        for (Map.Entry<String, Map<String, List<Slot>>> labEntry : labSlots.entrySet()) {
            for (Map.Entry<String, List<Slot>> slotEntry : labEntry.getValue().entrySet()) {
                if (slotEntry.getValue().size() < 2) {
                    continue;
                }
                Optional<Overlap> overlap = findOverlap(slotEntry.getValue());
                if (overlap.isPresent()) {
                    String[] dss = slotEntry.getKey().split("-");
                    conflicts.add(new InsightStats.ConflictItem(
                            "LAB", labEntry.getKey(),
                            Integer.parseInt(dss[0]), Integer.parseInt(dss[1]), Integer.parseInt(dss[2]),
                            overlap.get().weeks,
                            overlap.get().courseCodes));
                }
            }
        }

        // 教师冲突
        for (Map.Entry<Long, Map<String, List<Slot>>> tEntry : teacherSlots.entrySet()) {
            for (Map.Entry<String, List<Slot>> slotEntry : tEntry.getValue().entrySet()) {
                if (slotEntry.getValue().size() < 2) {
                    continue;
                }
                Optional<Overlap> overlap = findOverlap(slotEntry.getValue());
                if (overlap.isPresent()) {
                    String[] dss = slotEntry.getKey().split("-");
                    conflicts.add(new InsightStats.ConflictItem(
                            "TEACHER", teacherNames.getOrDefault(tEntry.getKey(), "教师#" + tEntry.getKey()),
                            Integer.parseInt(dss[0]), Integer.parseInt(dss[1]), Integer.parseInt(dss[2]),
                            overlap.get().weeks,
                            overlap.get().courseCodes));
                }
            }
        }

        return conflicts;
    }

    private Long resolveSemesterId(Long semesterId) {
        if (semesterId != null) {
            return semesterId;
        }
        Semester current = semesterService.getCurrentSemester();
        return current != null ? current.getId() : null;
    }

    /** 时段跨度（用于利用率统计的节次数） */
    private int slotSpan(ScheduleTime st) {
        int start = st.getStartSection() != null ? st.getStartSection() : 0;
        int end = st.getEndSection() != null ? st.getEndSection() : start;
        return Math.max(1, end - start + 1);
    }

    /** 解析周次范围字符串（如 "1-16"、"1,3,5"、"8"），结合单双周过滤。 */
    private Set<Integer> parseWeeks(String weekRange, String weekType) {
        Set<Integer> weeks = new HashSet<>();
        if (weekRange == null || weekRange.isBlank()) {
            return weeks;
        }
        for (String part : weekRange.split(",")) {
            String segment = part.trim();
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.contains("-")) {
                String[] bounds = segment.split("-", 2);
                try {
                    int from = Integer.parseInt(bounds[0].trim());
                    int to = Integer.parseInt(bounds[1].trim());
                    for (int w = from; w <= to; w++) {
                        weeks.add(w);
                    }
                } catch (NumberFormatException ignored) {
                    // 非法周次段跳过
                }
            } else {
                try {
                    weeks.add(Integer.parseInt(segment));
                } catch (NumberFormatException ignored) {
                    // 同上
                }
            }
        }
        if ("ODD".equalsIgnoreCase(weekType)) {
            weeks.removeIf(w -> w % 2 == 0);
        } else if ("EVEN".equalsIgnoreCase(weekType)) {
            weeks.removeIf(w -> w % 2 == 1);
        }
        return weeks;
    }

    /** 在同时段的多门课中寻找周次重叠；重叠则返回重叠周与课程号列表。 */
    private Optional<Overlap> findOverlap(List<Slot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                Slot a = slots.get(i);
                Slot b = slots.get(j);
                Set<Integer> overlapWeeks = new TreeSet<>(a.weeks);
                overlapWeeks.retainAll(b.weeks);
                if (!overlapWeeks.isEmpty()) {
                    List<String> codes = new ArrayList<>();
                    codes.add(a.courseCode);
                    codes.add(b.courseCode);
                    return Optional.of(new Overlap(formatWeeks(overlapWeeks), codes));
                }
            }
        }
        return Optional.empty();
    }

    private String formatWeeks(Set<Integer> weeks) {
        if (weeks.isEmpty()) {
            return "";
        }
        if (weeks.size() == 1) {
            return String.valueOf(weeks.iterator().next());
        }
        int min = weeks.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = weeks.stream().mapToInt(Integer::intValue).max().orElse(0);
        return min + "-" + max;
    }

    /** 同时段的一个课程占位（课程号 + 占用周次集合）。 */
    private static class Slot {
        final String courseCode;
        final Set<Integer> weeks;

        Slot(String courseCode, Set<Integer> weeks) {
            this.courseCode = courseCode;
            this.weeks = weeks;
        }
    }

    /** 冲突的重叠结果（重叠周次描述 + 课程号列表）。 */
    private static class Overlap {
        final String weeks;
        final List<String> courseCodes;

        Overlap(String weeks, List<String> courseCodes) {
            this.weeks = weeks;
            this.courseCodes = courseCodes;
        }
    }
}
