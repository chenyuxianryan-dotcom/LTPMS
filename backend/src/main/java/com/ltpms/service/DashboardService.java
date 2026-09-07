package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.dto.DashboardStats;
import com.ltpms.entity.*;
import com.ltpms.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 仪表盘统计服务，分别面向管理员与教师聚合首页所需的统计数据。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final LaboratoryMapper laboratoryMapper;
    private final CourseMapper courseMapper;
    private final ExperimentMapper experimentMapper;
    private final UserMapper userMapper;
    private final QueryHistoryMapper queryHistoryMapper;
    private final SemesterService semesterService;

    /**
     * 聚合管理员首页统计数据，含实验室、课程、实验、教师等全局数量。
     *
     * @return 管理员视角仪表盘统计数据
     */
    public DashboardStats adminStats() {
        Semester current = semesterService.getCurrentSemester();
        Long semesterId = current != null ? current.getId() : null;

        Long labCount = laboratoryMapper.selectCount(
                new LambdaQueryWrapper<Laboratory>().eq(Laboratory::getStatus, 1));
        Long courseCount = courseMapper.selectCount(
                semesterId != null
                        ? new LambdaQueryWrapper<Course>().eq(Course::getSemesterId, semesterId)
                        : new LambdaQueryWrapper<>());
        Long experimentCount = experimentMapper.selectCount(new LambdaQueryWrapper<>());
        Long teacherCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, "TEACHER"));

        return new DashboardStats(labCount, courseCount, experimentCount, teacherCount, null, null, null);
    }

    /**
     * 聚合当前教师首页统计数据，含本人负责课程数、实验数及最近查询记录。
     *
     * @param userId 当前教师（用户）ID
     * @return 教师视角仪表盘统计数据
     */
    public DashboardStats teacherStats(Long userId) {
        Semester current = semesterService.getCurrentSemester();
        Long semesterId = current != null ? current.getId() : null;

        LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, userId);
        if (semesterId != null) {
            courseWrapper.eq(Course::getSemesterId, semesterId);
        }
        Long myCourseCount = courseMapper.selectCount(courseWrapper);

        List<Long> courseIds = courseMapper.selectList(courseWrapper)
                .stream().map(Course::getId).toList();
        Long myExperimentCount = courseIds.isEmpty() ? 0L :
                experimentMapper.selectCount(
                        new LambdaQueryWrapper<Experiment>().in(Experiment::getCourseId, courseIds));

        List<QueryHistory> histories = queryHistoryMapper.selectList(
                new LambdaQueryWrapper<QueryHistory>()
                        .eq(QueryHistory::getUserId, userId)
                        .orderByDesc(QueryHistory::getCreatedAt)
                        .last("LIMIT 5"));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        List<DashboardStats.RecentQuery> recentQueries = histories.stream()
                .map(h -> new DashboardStats.RecentQuery(
                        h.getQuestion(),
                        h.getCreatedAt() != null ? h.getCreatedAt().format(formatter) : ""))
                .toList();

        return new DashboardStats(null, null, null, null, myCourseCount, myExperimentCount, recentQueries);
    }
}
