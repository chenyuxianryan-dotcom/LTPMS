package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.entity.*;
import com.ltpms.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 事件预警服务：定时巡检（逾期 / 停滞 / 积压）+ 内联触发（提交到达 / 数据集新版本）。
 * 所有方法均遵循：偏好检查 → 频控去重 → 发通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAlertService {

    private final NotificationService notificationService;
    private final NotificationPreferenceService prefService;
    private final LabProjectTaskMapper taskMapper;
    private final LabTaskSubmissionMapper submissionMapper;
    private final LabProjectMapper projectMapper;
    private final LabProjectMemberMapper memberMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;

    // ===== 定时巡检（SchedulingConfig 每 4 小时调用） =====

    /** 逾期预警：通知教师"你的课题有 N 项逾期"。 */
    public void scanOverdueTasks() {
        List<LabProjectTask> overdue = taskMapper.selectList(
                new LambdaQueryWrapper<LabProjectTask>()
                        .eq(LabProjectTask::getDeleted, 0)
                        .lt(LabProjectTask::getDeadline, LocalDateTime.now())
                        .notIn(LabProjectTask::getStatus, "ACCEPTED", "CLOSED", "REJECTED"));
        if (overdue.isEmpty()) return;

        // 按 projectId 分组
        Map<Long, List<LabProjectTask>> byProject = overdue.stream()
                .collect(Collectors.groupingBy(LabProjectTask::getProjectId));

        for (Map.Entry<Long, List<LabProjectTask>> e : byProject.entrySet()) {
            Long projectId = e.getKey();
            LabProject project = projectMapper.selectById(projectId);
            if (project == null) continue;
            Long teacherUserId = project.getTeacherId();
            if (!prefService.isEnabled(teacherUserId, "DEADLINE_WARNING")) continue;
            if (hasRecentDuplicate(teacherUserId, "DEADLINE_WARNING", 24)) continue;

            String title = "课题逾期预警";
            String content = "课题「" + project.getProjectName() + "」有 " + e.getValue().size() + " 项任务已逾期";
            notificationService.createNotification(teacherUserId, title, content, "DEADLINE_WARNING", "/experiments/" + projectId);
        }
    }

    /** 停滞提醒：IN_PROGRESS 且距截止 ≤3 天但无提交，提醒学生。 */
    public void scanStagnation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysLater = now.plusDays(3);

        List<LabProjectTask> candidates = taskMapper.selectList(
                new LambdaQueryWrapper<LabProjectTask>()
                        .eq(LabProjectTask::getDeleted, 0)
                        .eq(LabProjectTask::getStatus, "IN_PROGRESS")
                        .isNotNull(LabProjectTask::getDeadline)
                        .ge(LabProjectTask::getDeadline, now)
                        .le(LabProjectTask::getDeadline, threeDaysLater));
        if (candidates.isEmpty()) return;

        for (LabProjectTask task : candidates) {
            // 检查是否有提交
            long submissionCount = submissionMapper.selectCount(
                    new LambdaQueryWrapper<LabTaskSubmission>()
                            .eq(LabTaskSubmission::getTaskId, task.getId()));
            if (submissionCount > 0) continue; // 有提交，不属停滞

            // 查学生 userId
            Student student = studentMapper.selectById(task.getAssigneeStudentId());
            if (student == null || student.getUserId() == null) continue;
            Long studentUserId = student.getUserId();

            if (!prefService.isEnabled(studentUserId, "STAGNATION_ALERT")) continue;
            if (hasRecentDuplicate(studentUserId, "STAGNATION_ALERT", 24)) continue;

            long daysLeft = java.time.Duration.between(now, task.getDeadline()).toDays();
            String title = "任务停滞提醒";
            String content = "任务「" + task.getTitle() + "」距离截止仅剩 " + daysLeft + " 天，尚未提交任何进展";
            notificationService.createNotification(studentUserId, title, content, "STAGNATION_ALERT", "/experiments/" + task.getProjectId());
        }
    }

    /** 积压提醒：提交等待 >24h 未处理，按教师汇总通知。 */
    public void scanPendingReviews() {
        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        List<LabTaskSubmission> stale = submissionMapper.selectList(
                new LambdaQueryWrapper<LabTaskSubmission>()
                        .isNull(LabTaskSubmission::getReviewedAt)
                        .lt(LabTaskSubmission::getSubmittedAt, yesterday));
        if (stale.isEmpty()) return;

        // 按 task → project → teacher 分组
        Map<Long, List<LabTaskSubmission>> byTeacher = new HashMap<>();
        for (LabTaskSubmission sub : stale) {
            LabProjectTask task = taskMapper.selectById(sub.getTaskId());
            if (task == null) continue;
            LabProject project = projectMapper.selectById(task.getProjectId());
            if (project == null) continue;
            byTeacher.computeIfAbsent(project.getTeacherId(), k -> new ArrayList<>()).add(sub);
        }

        for (Map.Entry<Long, List<LabTaskSubmission>> e : byTeacher.entrySet()) {
            Long teacherUserId = e.getKey();
            if (!prefService.isEnabled(teacherUserId, "REVIEW_PENDING")) continue;
            if (hasRecentDuplicate(teacherUserId, "REVIEW_PENDING", 24)) continue;

            String title = "待验收提醒";
            String content = "有 " + e.getValue().size() + " 项提交已等待超过 24 小时未处理";
            notificationService.createNotification(teacherUserId, title, content, "REVIEW_PENDING", "/dashboard");
        }
    }

    // ===== 内联触发 =====

    /** 学生提交任务后，通知教师。 */
    @Transactional
    public void onSubmissionArrived(Long projectId, Long taskId, Long teacherUserId, String taskTitle, String studentName) {
        if (teacherUserId == null) return;
        if (!prefService.isEnabled(teacherUserId, "SUBMISSION_ARRIVED")) return;
        if (hasRecentDuplicate(teacherUserId, "SUBMISSION_ARRIVED", 1)) return;

        String title = "新提交待验收";
        String content = "学生 " + studentName + " 提交了任务：" + taskTitle;
        notificationService.createNotification(teacherUserId, title, content, "SUBMISSION_ARRIVED", "/experiments/" + projectId);
    }

    /** 数据集新版本上传，通知课题全体成员。 */
    @Transactional
    public void onDatasetNewVersion(Long projectId, String datasetName, int versionNo) {
        LabProject project = projectMapper.selectById(projectId);
        if (project == null) return;

        // 收集接收人：教师 + 全部学生成员
        Set<Long> userIds = new HashSet<>();
        userIds.add(project.getTeacherId());

        List<LabProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<LabProjectMember>()
                        .eq(LabProjectMember::getProjectId, projectId));
        for (LabProjectMember m : members) {
            Student s = studentMapper.selectById(m.getStudentId());
            if (s != null && s.getUserId() != null) {
                userIds.add(s.getUserId());
            }
        }

        String title = "数据集新版本";
        String content = "课题「" + project.getProjectName() + "」的数据集「" + datasetName + "」已更新至 v" + versionNo;

        for (Long uid : userIds) {
            if (!prefService.isEnabled(uid, "DATASET_NEW_VERSION")) continue;
            if (hasRecentDuplicate(uid, "DATASET_NEW_VERSION", 1)) continue;
            notificationService.createNotification(uid, title, content, "DATASET_NEW_VERSION", "/experiments/" + projectId);
        }
    }

    // ===== 工具方法 =====

    /** 频控去重：用户 + 类型 + 时间窗口内已有通知则跳过。 */
    private boolean hasRecentDuplicate(Long userId, String type, int hours) {
        long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getType, type)
                        .ge(Notification::getCreatedAt, LocalDateTime.now().minusHours(hours)));
        return count > 0;
    }
}
