package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.dto.DashboardStats;
import com.ltpms.entity.*;
import com.ltpms.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 仪表盘统计服务，分别面向管理员与教师聚合首页所需的统计数据。
 * 方格图明细（Cell/WeekCell）以当前学期为数据锚点：
 * 实验室/教师"使用中"= 当前学期课表（schedule）中有对应记录；
 * 课程"就绪"= 该课程已建实验项目（开课 vs 实验项目就绪度）；
 * 实验项目格=项目本体，used=已建成。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final LaboratoryMapper laboratoryMapper;
    private final CourseMapper courseMapper;
    private final ExperimentMapper experimentMapper;
    private final UserMapper userMapper;
    private final QueryHistoryMapper queryHistoryMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleTimeMapper scheduleTimeMapper;
    private final SemesterService semesterService;

    /**
     * 聚合管理员首页统计数据，含全局数量与四类方格图明细。
     *
     * @return 管理员视角仪表盘统计数据
     */
    public DashboardStats adminStats() {
        Semester current = semesterService.getCurrentSemester();
        Long semesterId = current != null ? current.getId() : null;

        // 一次取出当前学期全部排课，内存中按 labCode / courseCode / teacherId 分组计数
        List<Schedule> schedules = semesterId != null
                ? scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getSemesterId, semesterId))
                : Collections.emptyList();
        Map<String, Long> labScheduleCount = schedules.stream()
                .filter(s -> s.getLabCode() != null)
                .collect(Collectors.groupingBy(Schedule::getLabCode, Collectors.counting()));
        Map<String, Long> courseScheduleCount = schedules.stream()
                .filter(s -> s.getCourseCode() != null)
                .collect(Collectors.groupingBy(Schedule::getCourseCode, Collectors.counting()));
        Map<Long, Long> teacherScheduleCount = schedules.stream()
                .filter(s -> s.getTeacherId() != null)
                .collect(Collectors.groupingBy(Schedule::getTeacherId, Collectors.counting()));

        // 教师 ID → 姓名（课程方格 extra 显示任课教师）
        Map<Long, String> teacherNames = userMapper.selectList(
                        new LambdaQueryWrapper<User>().eq(User::getRole, "TEACHER"))
                .stream().collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));

        // 实验室方格：used = 当前学期有排课
        List<DashboardStats.Cell> labCells = laboratoryMapper.selectList(
                        new LambdaQueryWrapper<Laboratory>().eq(Laboratory::getStatus, 1))
                .stream().map(lab -> new DashboardStats.Cell(
                        lab.getId(), lab.getLabCode(), lab.getLabName(),
                        labScheduleCount.getOrDefault(lab.getLabCode(), 0L) > 0,
                        "排课 " + labScheduleCount.getOrDefault(lab.getLabCode(), 0L) + " 条"))
                .toList();

        // 当前学期课程（课程卡口径：开课 vs 实验项目就绪度——蓝=已建实验项目，灰=已排课未建实验项目）
        List<Course> semesterCourses = semesterId != null
                ? courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getSemesterId, semesterId))
                : Collections.emptyList();
        Map<Long, Long> expCountByCourse = semesterCourses.isEmpty()
                ? Collections.emptyMap()
                : experimentMapper.selectList(new LambdaQueryWrapper<Experiment>()
                        .in(Experiment::getCourseId,
                                semesterCourses.stream().map(Course::getId).toList()))
                    .stream()
                    .collect(Collectors.groupingBy(Experiment::getCourseId, Collectors.counting()));
        List<DashboardStats.Cell> courseCells = semesterCourses.stream().map(course -> {
            long expCount = expCountByCourse.getOrDefault(course.getId(), 0L);
            String teacherName = teacherNames.get(course.getTeacherId());
            return new DashboardStats.Cell(
                    course.getId(), course.getCourseCode(), course.getCourseName(),
                    expCount > 0,
                    (teacherName != null ? "任课 " + teacherName + " · " : "")
                            + "实验项目 " + expCount + " 个");
        }).toList();

        // 实验项目方格：格=实验项目本体，used=已建成（空态由前端引导"由教师创建"）
        Map<Long, String> courseNamesById = courseMapper.selectList(new LambdaQueryWrapper<Course>())
                .stream().collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));
        List<DashboardStats.Cell> experimentCells = experimentMapper.selectList(
                        new LambdaQueryWrapper<Experiment>())
                .stream().map(exp -> new DashboardStats.Cell(
                        exp.getId(), serialOf(exp.getExperimentCode(), exp.getExperimentName()),
                        exp.getExperimentName(), true,
                        "所属课程 " + courseNamesById.getOrDefault(exp.getCourseId(), "未知")))
                .toList();

        // 教师方格：used = 当前学期有排课（复用 teacherNames 查询结果，避免重复查库）；格内显示姓名
        List<DashboardStats.Cell> teacherCellList = userMapper.selectList(
                        new LambdaQueryWrapper<User>().eq(User::getRole, "TEACHER"))
                .stream().map(u -> {
                    long count = teacherScheduleCount.getOrDefault(u.getId(), 0L);
                    return new DashboardStats.Cell(
                            u.getId(), u.getRealName(), u.getRealName(),
                            count > 0, "本学期排课 " + count + " 条");
                }).toList();

        return DashboardStats.builder()
                .labCount((long) labCells.size())
                .courseCount((long) courseCells.size())
                .experimentCount((long) experimentCells.size())
                .teacherCount((long) teacherCellList.size())
                .labCells(labCells)
                .courseCells(courseCells)
                .experimentCells(experimentCells)
                .teacherCells(teacherCellList)
                .build();
    }

    /**
     * 聚合当前教师首页统计数据，含本人课程/实验明细、排课周历及最近查询记录。
     *
     * @param userId 当前教师（用户）ID
     * @return 教师视角仪表盘统计数据
     */
    public DashboardStats teacherStats(Long userId) {
        Semester current = semesterService.getCurrentSemester();
        Long semesterId = current != null ? current.getId() : null;

        // 我的当前学期课程（就绪度口径：蓝=已建实验项目，灰=已排课未建实验项目）
        LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, userId);
        if (semesterId != null) {
            courseWrapper.eq(Course::getSemesterId, semesterId);
        }
        List<Course> myCourses = courseMapper.selectList(courseWrapper);

        List<Long> myCourseIdList = myCourses.stream().map(Course::getId).toList();
        Map<Long, Long> myExpCountByCourse = myCourseIdList.isEmpty()
                ? Collections.emptyMap()
                : experimentMapper.selectList(new LambdaQueryWrapper<Experiment>()
                        .in(Experiment::getCourseId, myCourseIdList))
                    .stream()
                    .collect(Collectors.groupingBy(Experiment::getCourseId, Collectors.counting()));
        List<DashboardStats.Cell> myCourseCells = myCourses.stream().map(course -> {
            long expCount = myExpCountByCourse.getOrDefault(course.getId(), 0L);
            return new DashboardStats.Cell(
                    course.getId(), course.getCourseCode(), course.getCourseName(),
                    expCount > 0,
                    expCount > 0 ? "实验项目 " + expCount + " 个" : "未建实验项目");
        }).toList();

        // 我的实验项目：used = 所属课程在当前学期；格内显示实验编号流水号
        Map<Long, String> myCourseNames = myCourses.stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));
        List<Long> myAllCourseIds = courseMapper.selectList(
                        new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId))
                .stream().map(Course::getId).toList();
        List<Experiment> myExperiments = myAllCourseIds.isEmpty() ? Collections.emptyList()
                : experimentMapper.selectList(new LambdaQueryWrapper<Experiment>()
                        .in(Experiment::getCourseId, myAllCourseIds));
        List<DashboardStats.Cell> myExperimentCells = myExperiments.stream().map(exp -> {
            String belongName = myCourseNames.get(exp.getCourseId());
            return new DashboardStats.Cell(
                    exp.getId(), serialOf(exp.getExperimentCode(), exp.getExperimentName()),
                    exp.getExperimentName(),
                    belongName != null,
                    belongName != null ? "所属课程 " + belongName : "所属课程不在当前学期");
        }).toList();

        // 我的排课周历：每周一格
        List<DashboardStats.WeekCell> weekCells = buildWeekCells(current, userId);

        // 最近查询记录
        List<QueryHistory> histories = queryHistoryMapper.selectList(
                new LambdaQueryWrapper<QueryHistory>()
                        .eq(QueryHistory::getUserId, userId)
                        .orderByDesc(QueryHistory::getCreatedAt)
                        .last("LIMIT 5"));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        List<DashboardStats.RecentQuery> recentQueries = histories.stream()
                .map(h -> new DashboardStats.RecentQuery(
                        h.getQuestion(),
                        h.getCreatedAt() != null ? h.getCreatedAt().format(formatter) : "",
                        h.getSuccess()))
                .toList();

        return DashboardStats.builder()
                .myCourseCount((long) myCourseCells.size())
                .myExperimentCount((long) myExperimentCells.size())
                .recentQueries(recentQueries)
                .myCourseCells(myCourseCells)
                .myExperimentCells(myExperimentCells)
                .weekCells(weekCells)
                .build();
    }

    /**
     * 构建当前教师的本学期排课周历（每周一格）。
     * 总周数按学期起止日期估算（1~25 周封顶，无学期时默认 20 周）。
     */
    private List<DashboardStats.WeekCell> buildWeekCells(Semester current, Long userId) {
        int totalWeeks = 20;
        if (current != null && current.getStartDate() != null && current.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(current.getStartDate(), current.getEndDate());
            totalWeeks = (int) Math.min(25, Math.max(1, days / 7 + 1));
        }
        boolean[] usedWeeks = new boolean[totalWeeks + 1];

        if (current != null) {
            List<Schedule> mySchedules = scheduleMapper.selectList(
                    new LambdaQueryWrapper<Schedule>()
                            .eq(Schedule::getSemesterId, current.getId())
                            .eq(Schedule::getTeacherId, userId));
            if (!mySchedules.isEmpty()) {
                List<ScheduleTime> times = scheduleTimeMapper.selectList(
                        new LambdaQueryWrapper<ScheduleTime>()
                                .in(ScheduleTime::getScheduleId,
                                        mySchedules.stream().map(Schedule::getId).toList()));
                if (!times.isEmpty()) {
                    // 优先使用结构化时间段数据
                    for (ScheduleTime st : times) {
                        for (int week : parseWeekRange(st.getWeekRange(), st.getWeekType())) {
                            if (week >= 1 && week <= totalWeeks) {
                                usedWeeks[week] = true;
                            }
                        }
                    }
                } else {
                    // schedule_time 为空时回退解析 raw_schedule_text 中的周次段（如 "(1-16周)"）
                    for (Schedule s : mySchedules) {
                        for (int week : parseWeeksFromRawText(s.getRawScheduleText())) {
                            if (week >= 1 && week <= totalWeeks) {
                                usedWeeks[week] = true;
                            }
                        }
                    }
                }
            }
        }

        List<DashboardStats.WeekCell> cells = new ArrayList<>(totalWeeks);
        for (int w = 1; w <= totalWeeks; w++) {
            cells.add(new DashboardStats.WeekCell(w, usedWeeks[w]));
        }
        return cells;
    }

    /**
     * 解析周次范围字符串（如 "1-16"、"1,3,5"、"8"），并结合单双周类型过滤。
     */
    private Set<Integer> parseWeekRange(String weekRange, String weekType) {
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
                    // 非法周次段直接跳过，不影响其余解析
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

    /**
     * 生成方格内短文本：取名称前两个字符，超长截断。
     */
    private String abbreviate(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.length() <= 2 ? name : name.substring(0, 2);
    }

    /**
     * 实验项目方格内短文本：优先取实验编号末段流水号（如 CS101-20261-001 → 001），
     * 编号缺失或格式不符时回退名称缩写。
     */
    private String serialOf(String experimentCode, String name) {
        if (experimentCode != null && experimentCode.contains("-")) {
            String tail = experimentCode.substring(experimentCode.lastIndexOf('-') + 1);
            if (tail.matches("\\d{1,4}")) {
                return tail;
            }
        }
        return abbreviate(name);
    }

    /**
     * 从原始课表文本（如 "周一第1-4节(1-16周)"）中解析周次集合，
     * 作为 schedule_time 结构化数据缺失时的回退方案。
     * 只解析括号内的周次段，避免把括号外的节次（第1-4节）误判为周次。
     */
    private Set<Integer> parseWeeksFromRawText(String raw) {
        Set<Integer> weeks = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return weeks;
        }
        String type = "ALL";
        if (raw.contains("单周")) {
            type = "ODD";
        } else if (raw.contains("双周")) {
            type = "EVEN";
        }
        Matcher bracketMatcher = Pattern.compile("\\(([^()]+)\\)").matcher(raw);
        while (bracketMatcher.find()) {
            String segment = bracketMatcher.group(1);
            Matcher weekMatcher = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)|(\\d+)").matcher(segment);
            while (weekMatcher.find()) {
                if (weekMatcher.group(1) != null) {
                    int from = Integer.parseInt(weekMatcher.group(1));
                    int to = Integer.parseInt(weekMatcher.group(2));
                    for (int w = from; w <= to; w++) {
                        weeks.add(w);
                    }
                } else {
                    weeks.add(Integer.parseInt(weekMatcher.group(3)));
                }
            }
        }
        if ("ODD".equals(type)) {
            weeks.removeIf(w -> w % 2 == 0);
        } else if ("EVEN".equals(type)) {
            weeks.removeIf(w -> w % 2 == 1);
        }
        return weeks;
    }
}
