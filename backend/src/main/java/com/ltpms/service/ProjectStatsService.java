package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.entity.LabProjectMember;
import com.ltpms.entity.LabProjectTask;
import com.ltpms.entity.Student;
import com.ltpms.entity.User;
import com.ltpms.mapper.LabProjectMemberMapper;
import com.ltpms.mapper.LabProjectTaskMapper;
import com.ltpms.mapper.StudentMapper;
import com.ltpms.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课题统计单一事实源（r2-B1 冻结口径）：
 * 完成度% = ACCEPTED / (总任务 − CLOSED)，软删行由 MyBatis-Plus 逻辑删除自动剔除；
 * 已验收任务永久计入分子分母，CLOSED（成员移出/规则①关闭）不进分母；
 * 逾期为查询时派生（deadline &lt; now 且状态 ∈ 未开始/进行中），不落库、不进分母。
 * 列表页 / 详情汇总条 / 学生×任务矩阵全部走本类 stats()，杜绝双口径。
 */
@Service
@RequiredArgsConstructor
public class ProjectStatsService {

    private final LabProjectTaskMapper taskMapper;
    private final LabProjectMemberMapper memberMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;

    /** 单课题完整统计（汇总+学生行+任务列） */
    public ProjectStats stats(Long projectId) {
        List<LabProjectTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<LabProjectTask>()
                        .eq(LabProjectTask::getProjectId, projectId)
                        .orderByAsc(LabProjectTask::getId));
        List<LabProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<LabProjectMember>()
                        .eq(LabProjectMember::getProjectId, projectId)
                        .orderByAsc(LabProjectMember::getId));
        Map<Long, String> studentNames = studentNames(members.stream()
                .map(LabProjectMember::getStudentId).collect(Collectors.toSet()));

        LocalDateTime now = LocalDateTime.now();
        int effective = 0;
        int accepted = 0;
        int overdue = 0;
        for (LabProjectTask task : tasks) {
            boolean closed = "CLOSED".equals(task.getStatus());
            if (!closed) {
                effective++;
                if ("ACCEPTED".equals(task.getStatus())) {
                    accepted++;
                }
            }
            if (isOverdue(task, now)) {
                overdue++;
            }
        }

        Map<Long, List<LabProjectTask>> byStudent = tasks.stream()
                .collect(Collectors.groupingBy(LabProjectTask::getAssigneeStudentId,
                        LinkedHashMap::new, Collectors.toList()));

        List<StudentRow> studentRows = new ArrayList<>();
        for (LabProjectMember member : members) {
            List<LabProjectTask> own = byStudent.getOrDefault(member.getStudentId(), List.of());
            long ownEffective = own.stream().filter(t -> !"CLOSED".equals(t.getStatus())).count();
            long ownAccepted = own.stream().filter(t -> "ACCEPTED".equals(t.getStatus())).count();
            List<Cell> cells = own.stream()
                    .map(t -> new Cell(t.getId(), t.getTitle(), t.getStatus(), isOverdue(t, now)))
                    .collect(Collectors.toList());
            studentRows.add(new StudentRow(member.getStudentId(),
                    studentNames.getOrDefault(member.getStudentId(), "学生#" + member.getStudentId()),
                    rate(ownAccepted, ownEffective), cells));
        }
        studentRows.sort((a, b) -> Double.compare(b.rate(), a.rate()));

        List<TaskColumn> taskCols = tasks.stream()
                .map(t -> new TaskColumn(t.getId(), t.getTitle(), t.getStatus(),
                        t.getDeadline(), isOverdue(t, now)))
                .collect(Collectors.toList());

        return new ProjectStats(rate(accepted, effective), effective,
                accepted, overdue, members.size(), studentRows, taskCols);
    }

    /** 多课题轻量汇总（列表页行内完成度条），与 stats() 同口径 */
    public Map<Long, ProjectSummary> summaries(Collection<Long> projectIds) {
        Map<Long, ProjectSummary> result = new LinkedHashMap<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return result;
        }
        for (Long id : projectIds) {
            result.put(id, ProjectSummary.empty());
        }
        List<LabProjectTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<LabProjectTask>().in(LabProjectTask::getProjectId, projectIds));
        List<LabProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<LabProjectMember>().in(LabProjectMember::getProjectId, projectIds));
        LocalDateTime now = LocalDateTime.now();
        Map<Long, int[]> agg = new HashMap<>();
        for (LabProjectTask task : tasks) {
            int[] v = agg.computeIfAbsent(task.getProjectId(), k -> new int[3]);
            if (!"CLOSED".equals(task.getStatus())) {
                v[0]++;
                if ("ACCEPTED".equals(task.getStatus())) {
                    v[1]++;
                }
            }
            if (isOverdue(task, now)) {
                v[2]++;
            }
        }
        Map<Long, Long> memberCount = members.stream()
                .collect(Collectors.groupingBy(LabProjectMember::getProjectId, Collectors.counting()));
        for (Long id : projectIds) {
            int[] v = agg.getOrDefault(id, new int[3]);
            result.put(id, new ProjectSummary(rate(v[1], v[0]), v[2],
                    memberCount.getOrDefault(id, 0L).intValue()));
        }
        return result;
    }

    public static boolean isOverdue(LabProjectTask task, LocalDateTime now) {
        if (task.getDeadline() == null || !task.getDeadline().isBefore(now)) {
            return false;
        }
        return "NOT_STARTED".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus());
    }

    /** 除零保护：分母为 0 时完成率 0 */
    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0d : Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    private Map<Long, String> studentNames(Set<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Student> students = studentMapper.selectBatchIds(studentIds);
        Map<Long, Long> studentToUser = students.stream()
                .collect(Collectors.toMap(Student::getId, Student::getUserId));
        Map<Long, User> users = userMapper.selectBatchIds(studentToUser.values()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> names = new HashMap<>();
        for (Map.Entry<Long, Long> entry : studentToUser.entrySet()) {
            User user = users.get(entry.getValue());
            names.put(entry.getKey(), user == null ? null
                    : (user.getRealName() != null ? user.getRealName() : user.getUsername()));
        }
        return names;
    }

    /** 矩阵格（任务维度） */
    public record Cell(Long taskId, String taskTitle, String status, boolean overdue) {
    }

    /** 矩阵学生行 */
    public record StudentRow(Long studentId, String name, double rate, List<Cell> cells) {
    }

    /** 矩阵任务列（表头） */
    public record TaskColumn(Long taskId, String title, String status,
                             LocalDateTime deadline, boolean overdue) {
    }

    /** 单课题完整统计响应（total=任务总数，已剔除 CLOSED） */
    public record ProjectStats(double projectRate, int total, int accepted, int overdueCount,
                               int memberCount, List<StudentRow> students, List<TaskColumn> tasks) {
    }

    /** 列表页轻量汇总 */
    public record ProjectSummary(double projectRate, int overdueCount, int memberCount) {

        public static ProjectSummary empty() {
            return new ProjectSummary(0, 0, 0);
        }
    }
}
