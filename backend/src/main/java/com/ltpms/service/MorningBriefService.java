package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.entity.*;
import com.ltpms.mapper.*;
import com.ltpms.util.WeekMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 晨间简报服务：每日 7:30 生成聚合摘要通知。
 * 周一版含全项目健康检查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MorningBriefService {

    private final UserMapper userMapper;
    private final ScheduleService scheduleService;
    private final LabProjectTaskMapper taskMapper;
    private final LabProjectMapper projectMapper;
    private final LabTaskSubmissionMapper submissionMapper;
    private final LabProjectMemberMapper memberMapper;
    private final StudentMapper studentMapper;
    private final NotificationService notificationService;
    private final NotificationPreferenceService prefService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** 定时入口：SchedulingConfig 每日 7:30 调用。 */
    public void generateAll() {
        boolean isMonday = LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY;
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        int sent = 0;
        for (User user : users) {
            if (!prefService.isEnabled(user.getId(), "MORNING_BRIEF")) {
                continue;
            }
            try {
                generateForUser(user.getId(), user.getRole(), isMonday);
                sent++;
            } catch (Exception e) {
                log.warn("晨报生成失败 userId={}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("晨报发送完毕：{}/{} 人", sent, users.size());
    }

    /** 为单个用户生成晨报。 */
    public void generateForUser(Long userId, String role, boolean isMonday) {
        switch (role) {
            case "TEACHER" -> generateForTeacher(userId, isMonday);
            case "ADMIN"   -> generateForAdmin(isMonday);
            case "STUDENT" -> generateForStudent(userId);
        }
    }

    // ---- TEACHER ----
    private void generateForTeacher(Long userId, boolean isMonday) {
        // 今日课表
        List<?> todaySlots = scheduleService.getTodaySchedule(userId);
        int classCount = todaySlots.size();

        // 本人项目的待验收提交数
        List<LabProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<LabProject>()
                        .eq(LabProject::getTeacherId, userId)
                        .eq(LabProject::getStatus, "ACTIVE"));
        Set<Long> projectIds = projects.stream().map(LabProject::getId).collect(Collectors.toSet());

        int pendingReview = 0;
        int overdueTasks = 0;
        if (!projectIds.isEmpty()) {
            // 待验收：提交未 reviewed 且任务属于本人项目
            List<LabProjectTask> allTasks = taskMapper.selectList(
                    new LambdaQueryWrapper<LabProjectTask>()
                            .in(LabProjectTask::getProjectId, projectIds)
                            .eq(LabProjectTask::getDeleted, 0));
            Set<Long> taskIds = allTasks.stream().map(LabProjectTask::getId).collect(Collectors.toSet());
            if (!taskIds.isEmpty()) {
                pendingReview = Math.toIntExact(submissionMapper.selectCount(
                        new LambdaQueryWrapper<LabTaskSubmission>()
                                .in(LabTaskSubmission::getTaskId, taskIds)
                                .isNull(LabTaskSubmission::getReviewedAt)));
            }
            // 逾期任务
            overdueTasks = (int) allTasks.stream()
                    .filter(t -> t.getDeadline() != null
                            && t.getDeadline().isBefore(java.time.LocalDateTime.now())
                            && !"ACCEPTED".equals(t.getStatus())
                            && !"CLOSED".equals(t.getStatus())
                            && !"REJECTED".equals(t.getStatus()))
                    .count();
        }

        String title = String.format("今日概览：%d 节课、%d 项待验收、%d 项逾期",
                classCount, pendingReview, overdueTasks);

        StringBuilder content = new StringBuilder();
        if (classCount > 0) {
            content.append("今日课程：").append(classCount).append(" 节。");
        } else {
            content.append("今日无课程安排。");
        }
        if (pendingReview > 0) {
            content.append(pendingReview).append(" 项提交等待验收。");
        }
        if (overdueTasks > 0) {
            content.append(overdueTasks).append(" 项任务已逾期。");
        }
        if (isMonday && !projects.isEmpty()) {
            content.append("本周关注：").append(projects.size()).append(" 个课题进行中。");
        }

        notificationService.createNotification(userId, title, content.toString(), "MORNING_BRIEF", "/dashboard");
    }

    // ---- ADMIN ----
    private void generateForAdmin(boolean isMonday) {
        long activeProjects = projectMapper.selectCount(
                new LambdaQueryWrapper<LabProject>().eq(LabProject::getStatus, "ACTIVE"));

        List<LabProjectTask> allActiveTasks = taskMapper.selectList(
                new LambdaQueryWrapper<LabProjectTask>()
                        .eq(LabProjectTask::getDeleted, 0)
                        .notIn(LabProjectTask::getStatus, "ACCEPTED", "CLOSED", "REJECTED"));
        long overdueCount = allActiveTasks.stream()
                .filter(t -> t.getDeadline() != null && t.getDeadline().isBefore(java.time.LocalDateTime.now()))
                .count();

        long pendingReview = submissionMapper.selectCount(
                new LambdaQueryWrapper<LabTaskSubmission>().isNull(LabTaskSubmission::getReviewedAt));

        String title = String.format("全局概览：%d 个课题、%d 项逾期、%d 项待验收",
                activeProjects, overdueCount, pendingReview);

        List<User> admins = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN").eq(User::getStatus, 1));
        for (User admin : admins) {
            notificationService.createNotification(admin.getId(), title, title, "MORNING_BRIEF", "/dashboard");
        }
    }

    // ---- STUDENT ----
    private void generateForStudent(Long userId) {
        // userId → studentId
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, userId));
        if (student == null) return;

        List<LabProjectTask> myTasks = taskMapper.selectList(
                new LambdaQueryWrapper<LabProjectTask>()
                        .eq(LabProjectTask::getAssigneeStudentId, student.getId())
                        .eq(LabProjectTask::getDeleted, 0)
                        .notIn(LabProjectTask::getStatus, "ACCEPTED", "CLOSED", "REJECTED"));

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime threeDaysLater = now.plusDays(3);

        int dueSoon = 0;
        int overdue = 0;
        for (LabProjectTask t : myTasks) {
            if (t.getDeadline() == null) continue;
            if (t.getDeadline().isBefore(now)) {
                overdue++;
            } else if (t.getDeadline().isBefore(threeDaysLater)) {
                dueSoon++;
            }
        }

        if (dueSoon == 0 && overdue == 0) {
            // 无待办，不发
            return;
        }

        String title = String.format("今日待办：%d 项即将到期、%d 项已逾期", dueSoon, overdue);
        notificationService.createNotification(userId, title, title, "MORNING_BRIEF", "/my-tasks");
    }
}
