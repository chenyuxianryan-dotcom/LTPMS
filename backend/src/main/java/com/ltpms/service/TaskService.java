package com.ltpms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.project.TaskCreateRequest;
import com.ltpms.dto.project.TaskReviewRequest;
import com.ltpms.dto.project.TaskSubmitRequest;
import com.ltpms.dto.project.TaskUpdateRequest;
import com.ltpms.entity.LabProject;
import com.ltpms.entity.LabTaskSubmission;
import com.ltpms.entity.LabProjectMember;
import com.ltpms.entity.LabProjectTask;
import com.ltpms.entity.Student;
import com.ltpms.mapper.LabProjectMapper;
import com.ltpms.mapper.LabTaskSubmissionMapper;
import com.ltpms.mapper.LabProjectMemberMapper;
import com.ltpms.mapper.LabProjectTaskMapper;
import com.ltpms.mapper.StudentMapper;
import com.ltpms.mapper.UserMapper;
import com.ltpms.entity.User;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课题任务服务（S2）：派发（多人=复制多条）、状态机流转、提交追加式留痕、
 * 验收 gate、deadline 等字段级修订留痕（r2-B2）、规则①删除策略。
 * 权限判定收口 ProjectAuthService；逾期为派生不落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LabProjectTaskMapper taskMapper;
    private final LabProjectMapper projectMapper;
    private final LabTaskSubmissionMapper submissionMapper;
    private final LabProjectMemberMapper memberMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ProjectAuthService authService;
    private final NotificationService notificationService;
    private final RevisionService revisionService;
    private final ProjectStatsService statsService;

    /** 任务视图：学生侧不含提交内容/评语（字段级裁剪在提交接口做） */
    public record TaskView(Long id, Long projectId, String title, String description,
                           Long assigneeStudentId, String assigneeName, String status,
                           LocalDateTime deadline, boolean overdue, Long datasetId,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 提交记录视图（半透明裁剪后） */
    public record SubmissionView(Long id, Long taskId, Long submitterStudentId, String submitterName,
                                 String content, Long datasetVersionId, LocalDateTime submittedAt,
                                 String reviewComment, LocalDateTime reviewedAt) {
    }

    /** 学生「我的任务」按课题分组 */
    public record MyTaskGroup(Long projectId, String projectName, List<TaskView> tasks) {
    }

    // ---------------------------------------------------------------- 列表

    /** 项目任务列表（成员可读；学生与老师同字段——提交详情另接口裁剪） */
    public List<TaskView> listByProject(Long projectId, Long userId, String role) {
        authService.requireReadable(projectId, userId, role);
        return toViews(taskMapper.selectList(
                new LambdaQueryWrapper<LabProjectTask>()
                        .eq(LabProjectTask::getProjectId, projectId)
                        .orderByAsc(LabProjectTask::getId)));
    }

    /** 学生我的任务待办：按课题分组一次拉全（跨项目不串漏），可按状态过滤 */
    public List<MyTaskGroup> myTasks(Long userId, String status) {
        Student student = authService.requireStudentByUserId(userId);
        List<LabProjectTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<LabProjectTask>()
                        .eq(LabProjectTask::getAssigneeStudentId, student.getId())
                        .ne(LabProjectTask::getStatus, "CLOSED")
                        .orderByAsc(LabProjectTask::getId));
        if (StringUtils.hasText(status)) {
            tasks = tasks.stream().filter(t -> status.equals(t.getStatus())).collect(Collectors.toList());
        }
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<LabProject> projects = activeProjectsOf(tasks);
        Map<Long, String> projectNames = projects.stream()
                .collect(Collectors.toMap(LabProject::getId, LabProject::getProjectName));
        List<TaskView> views = toViews(tasks);
        Map<Long, List<TaskView>> grouped = new LinkedHashMap<>();
        for (TaskView view : views) {
            if (!projectNames.containsKey(view.projectId())) {
                continue; // 归档/不可见课题不出现在学生待办
            }
            grouped.computeIfAbsent(view.projectId(), k -> new ArrayList<>()).add(view);
        }
        return grouped.entrySet().stream()
                .map(e -> new MyTaskGroup(e.getKey(), projectNames.get(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }

    private List<LabProject> activeProjectsOf(List<LabProjectTask> tasks) {
        Set<Long> ids = tasks.stream().map(LabProjectTask::getProjectId).collect(Collectors.toSet());
        return new ArrayList<>(projectMapper.selectList(
                new LambdaQueryWrapper<LabProject>()
                        .in(LabProject::getId, ids)
                        .eq(LabProject::getStatus, "ACTIVE")));
    }

    // ---------------------------------------------------------------- 派发

    /**
     * 新建任务：assigneeStudentIds 多选时服务端复制为多条独立任务，逐个通知（S2 验收①）。
     */
    @Transactional
    public List<TaskView> create(Long projectId, TaskCreateRequest request, Long userId, String role) {
        LabProject project = authService.requireOwned(projectId, userId, role);
        authService.requireActive(project);
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务标题不能为空");
        }
        List<Long> assignees = request.getAssigneeStudentIds();
        if (assignees == null || assignees.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少选择一名负责学生");
        }
        Set<Long> memberIds = memberMapper.selectList(
                        new LambdaQueryWrapper<LabProjectMember>().eq(LabProjectMember::getProjectId, projectId)).stream()
                .map(LabProjectMember::getStudentId).collect(Collectors.toSet());
        List<LabProjectTask> created = new ArrayList<>();
        for (Long studentId : assignees.stream().distinct().collect(Collectors.toList())) {
            if (!memberIds.contains(studentId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "学生不在课题成员中：" + studentId);
            }
            LabProjectTask task = new LabProjectTask();
            task.setProjectId(projectId);
            task.setTitle(request.getTitle().trim());
            task.setDescription(request.getDescription());
            task.setAssigneeStudentId(studentId);
            task.setStatus("NOT_STARTED");
            task.setDeadline(request.getDeadline());
            task.setDatasetId(request.getDatasetId());
            task.setDeleted(0);
            task.setCreatedBy(userId);
            taskMapper.insert(task);
            created.add(task);
        }
        Map<Long, String> names = studentNames(created.stream()
                .map(LabProjectTask::getAssigneeStudentId).collect(Collectors.toSet()));
        Map<Long, Long> userIds = studentUserIds(created.stream()
                .map(LabProjectTask::getAssigneeStudentId).collect(Collectors.toSet()));
        for (LabProjectTask task : created) {
            notificationService.createLabTask(userIds.get(task.getAssigneeStudentId()),
                    "新任务待领取",
                    "课题「" + project.getProjectName() + "」给你派发了任务：" + task.getTitle()
                            + deadlineSuffix(task.getDeadline()),
                    projectId);
        }
        log.debug("Created {} tasks in project {} by {}", created.size(), projectId, userId);
        return toViews(created, names);
    }

    // ---------------------------------------------------------------- 编辑（含 r2-B2 留痕）

    /**
     * 归属老师编辑任务；管理员只读（2026-09-20 权限边界）；deadline 等关键考核字段修改自动写 LAB_TASK 字段级修订留痕。
     */
    @Transactional
    public TaskView update(Long taskId, TaskUpdateRequest request, Long userId, String role) {
        LabProjectTask task = getTaskOrThrow(taskId);
        LabProject project = authService.requireOwned(task.getProjectId(), userId, role);
        authService.requireActive(project);

        LabProjectTask update = new LabProjectTask();
        update.setId(taskId);
        Map<String, String[]> changes = new LinkedHashMap<>();
        if (request.getTitle() != null && !request.getTitle().isBlank()
                && !request.getTitle().trim().equals(task.getTitle())) {
            changes.put("title", new String[]{task.getTitle(), request.getTitle().trim()});
            update.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null
                && !java.util.Objects.equals(request.getDescription(), task.getDescription())) {
            changes.put("description", new String[]{task.getDescription(), request.getDescription()});
            update.setDescription(request.getDescription());
        }
        if (request.getDeadline() != null && !request.getDeadline().equals(task.getDeadline())) {
            changes.put("deadline", new String[]{format(task.getDeadline()), format(request.getDeadline())});
            update.setDeadline(request.getDeadline());
        }
        if (request.getDatasetId() != null && !request.getDatasetId().equals(task.getDatasetId())) {
            changes.put("datasetId", new String[]{format(task.getDatasetId()), format(request.getDatasetId())});
            update.setDatasetId(request.getDatasetId());
        }
        if (changes.isEmpty()) {
            return toView(task);
        }
        taskMapper.updateById(update);
        revisionService.record(RevisionService.TARGET_LAB_TASK, taskId, changes, role, userId);
        return toView(taskMapper.selectById(taskId));
    }

    /** deadline 修订明细（老师端时间线；权限=可读课题） */
    public List<RevisionService.RevisionView> listRevisions(Long taskId, Long userId, String role) {
        LabProjectTask task = getTaskOrThrow(taskId);
        authService.requireReadable(task.getProjectId(), userId, role);
        return revisionService.list(RevisionService.TARGET_LAB_TASK, taskId);
    }

    // ---------------------------------------------------------------- 规则①删除/关闭

    /**
     * 删除任务（规则①，已批准）：
     * 未提交（NOT_STARTED/IN_PROGRESS）→ 软删留痕；
     * 已提交/已验收 → 400 拒绝，只能先驳回；
     * 已驳回（REJECTED）→ 老师关闭置 CLOSED（不进分母，历史可复现）。
     */
    @Transactional
    public void delete(Long taskId, Long userId, String role) {
        LabProjectTask task = getTaskOrThrow(taskId);
        LabProject project = authService.requireOwned(task.getProjectId(), userId, role);
        authService.requireActive(project);
        switch (task.getStatus()) {
            case "SUBMITTED", "ACCEPTED" -> throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "已提交任务不可删除，请先驳回后关闭");
            case "REJECTED" -> closeInternal(taskId);
            case "CLOSED" -> {
                // 已关闭：幂等返回
            }
            default -> taskMapper.deleteById(taskId);
        }
    }

    /** 规则①：驳回后由老师关闭（置 CLOSED）；公开入口仅 delete()，归属权限已在其内校验 */
    private void closeInternal(Long taskId) {
        LabProjectTask update = new LabProjectTask();
        update.setId(taskId);
        update.setStatus("CLOSED");
        taskMapper.updateById(update);
    }

    // ---------------------------------------------------------------- 状态机流转

    /** 负责学生开始任务：NOT_STARTED/REJECTED → IN_PROGRESS */
    @Transactional
    public TaskView start(Long taskId, Long userId) {
        LabProjectTask task = getTaskOrThrow(taskId);
        Student student = requireAssignee(task, userId);
        if (!"NOT_STARTED".equals(task.getStatus()) && !"REJECTED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可开始任务");
        }
        LabProjectTask update = new LabProjectTask();
        update.setId(taskId);
        update.setStatus("IN_PROGRESS");
        taskMapper.updateById(update);
        return toView(taskMapper.selectById(taskId));
    }

    /** 负责学生提交：IN_PROGRESS/REJECTED → SUBMITTED，追加提交行，通知归属老师 */
    @Transactional
    public SubmissionView submit(Long taskId, TaskSubmitRequest request, Long userId) {
        LabProjectTask task = getTaskOrThrow(taskId);
        Student student = requireAssignee(task, userId);
        LabProject project = authService.getProjectOrThrow(task.getProjectId());
        authService.requireActive(project);
        if (!"IN_PROGRESS".equals(task.getStatus()) && !"REJECTED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先开始任务再提交");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提交说明不能为空");
        }
        LabTaskSubmission submission = new LabTaskSubmission();
        submission.setTaskId(taskId);
        submission.setSubmitterStudentId(student.getId());
        submission.setContent(request.getContent());
        submission.setDatasetVersionId(request.getDatasetVersionId());
        submission.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(submission);

        LabProjectTask update = new LabProjectTask();
        update.setId(taskId);
        update.setStatus("SUBMITTED");
        taskMapper.updateById(update);

        notificationService.createLabTask(project.getTeacherId(), "任务已提交待验收",
                "学生 " + displayName(student.getId()) + " 提交了任务：" + task.getTitle(),
                project.getId());
        return toSubmissionView(submission, true);
    }

    /** 归属老师验收：SUBMITTED → ACCEPTED / REJECTED(→IN_PROGRESS 由 start 承接)，通知学生 */
    @Transactional
    public TaskView review(Long taskId, TaskReviewRequest request, Long userId, String role) {
        LabProjectTask task = getTaskOrThrow(taskId);
        LabProject project = authService.requireOwned(task.getProjectId(), userId, role);
        authService.requireActive(project);
        if (!"SUBMITTED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已提交任务可验收");
        }
        boolean accept = "ACCEPT".equalsIgnoreCase(request.getDecision());
        boolean reject = "REJECT".equalsIgnoreCase(request.getDecision());
        if (!accept && !reject) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验收结论必须为 ACCEPT 或 REJECT");
        }
        if (reject && !StringUtils.hasText(request.getComment())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "驳回必须填写意见");
        }
        LabTaskSubmission latest = findLatestPendingSubmission(taskId);
        if (latest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到待验收的提交记录");
        }
        LabTaskSubmission subUpdate = new LabTaskSubmission();
        subUpdate.setId(latest.getId());
        subUpdate.setReviewedBy(userId);
        subUpdate.setReviewComment(request.getComment());
        subUpdate.setReviewedAt(LocalDateTime.now());
        submissionMapper.updateById(subUpdate);

        LabProjectTask taskUpdate = new LabProjectTask();
        taskUpdate.setId(taskId);
        taskUpdate.setStatus(accept ? "ACCEPTED" : "REJECTED");
        taskMapper.updateById(taskUpdate);

        Long studentUserId = studentUserIds(Collections.singleton(task.getAssigneeStudentId()))
                .get(task.getAssigneeStudentId());
        notificationService.createLabTask(studentUserId,
                accept ? "任务已验收通过" : "任务被驳回",
                "任务「" + task.getTitle() + "」"
                        + (accept ? "已验收通过。" : "被驳回：" + request.getComment()),
                project.getId());
        return toView(taskMapper.selectById(taskId));
    }

    // ---------------------------------------------------------------- 提交记录（Q6 半透明）

    /**
     * 任务提交历史。归属老师/ADMIN 全量；负责学生本人全量；
     * 其他学生访问他人任务的提交详情 → 403（后端强制，不靠前端隐藏）。
     */
    public List<SubmissionView> submissions(Long taskId, Long userId, String role) {
        LabProjectTask task = getTaskOrThrow(taskId);
        LabProject project = authService.requireReadable(task.getProjectId(), userId, role);
        boolean privileged = SecurityUtils.ROLE_ADMIN.equals(role)
                || (SecurityUtils.ROLE_TEACHER.equals(role) && project.getTeacherId().equals(userId));
        if (!privileged) {
            Student student = authService.requireStudentByUserId(userId);
            if (!student.getId().equals(task.getAssigneeStudentId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        List<LabTaskSubmission> list = submissionMapper.selectList(
                new LambdaQueryWrapper<LabTaskSubmission>()
                        .eq(LabTaskSubmission::getTaskId, taskId)
                        .orderByAsc(LabTaskSubmission::getSubmittedAt));
        return list.stream().map(s -> toSubmissionView(s, true)).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- 内部工具

    public LabProjectTask getTaskOrThrow(Long taskId) {
        LabProjectTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private Student requireAssignee(LabProjectTask task, Long userId) {
        Student student = authService.requireStudentByUserId(userId);
        if (!student.getId().equals(task.getAssigneeStudentId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅负责学生可操作该任务");
        }
        return student;
    }

    private LabTaskSubmission findLatestPendingSubmission(Long taskId) {
        return submissionMapper.selectOne(
                new LambdaQueryWrapper<LabTaskSubmission>()
                        .eq(LabTaskSubmission::getTaskId, taskId)
                        .isNull(LabTaskSubmission::getReviewedAt)
                        .orderByDesc(LabTaskSubmission::getSubmittedAt)
                        .orderByDesc(LabTaskSubmission::getId)
                        .last("LIMIT 1"));
    }

    private List<TaskView> toViews(List<LabProjectTask> tasks) {
        return toViews(tasks, studentNames(tasks.stream()
                .map(LabProjectTask::getAssigneeStudentId).collect(Collectors.toSet())));
    }

    private List<TaskView> toViews(List<LabProjectTask> tasks, Map<Long, String> names) {
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream()
                .map(t -> new TaskView(t.getId(), t.getProjectId(), t.getTitle(), t.getDescription(),
                        t.getAssigneeStudentId(), names.get(t.getAssigneeStudentId()), t.getStatus(),
                        t.getDeadline(), ProjectStatsService.isOverdue(t, now), t.getDatasetId(),
                        t.getCreatedAt(), t.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    private TaskView toView(LabProjectTask task) {
        return toViews(List.of(task)).get(0);
    }

    private SubmissionView toSubmissionView(LabTaskSubmission s, boolean withNames) {
        return new SubmissionView(s.getId(), s.getTaskId(), s.getSubmitterStudentId(),
                withNames ? displayName(s.getSubmitterStudentId()) : null,
                s.getContent(), s.getDatasetVersionId(), s.getSubmittedAt(),
                s.getReviewComment(), s.getReviewedAt());
    }

    private String displayName(Long studentId) {
        return studentNames(Collections.singleton(studentId)).get(studentId);
    }

    private Map<Long, String> studentNames(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        List<Student> students = studentMapper.selectBatchIds(studentIds);
        if (students.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> toUser = students.stream()
                .collect(Collectors.toMap(Student::getId, Student::getUserId));
        Map<Long, User> users = userMapper.selectBatchIds(toUser.values()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, String> names = new HashMap<>();
        toUser.forEach((sid, uid) -> {
            User u = users.get(uid);
            names.put(sid, u == null ? "学生#" + sid
                    : (StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
        });
        return names;
    }

    private Map<Long, Long> studentUserIds(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getUserId));
    }

    private static String format(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dt) {
            return dt.format(DEADLINE_FMT);
        }
        return String.valueOf(value);
    }

    private static String deadlineSuffix(LocalDateTime deadline) {
        return deadline == null ? "" : "，截止 " + deadline.format(DEADLINE_FMT);
    }
}
