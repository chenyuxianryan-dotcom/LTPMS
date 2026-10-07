package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.project.TaskCreateRequest;
import com.ltpms.dto.project.TaskReviewRequest;
import com.ltpms.dto.project.TaskSubmitRequest;
import com.ltpms.dto.project.TaskUpdateRequest;
import com.ltpms.service.RevisionService;
import com.ltpms.service.TaskService;
import com.ltpms.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课题任务接口：编辑/删除（规则①）、状态机流转（学生）、验收（老师）、我的任务、
 * 提交历史（Q6 半透明后端强制）与 deadline 修订留痕（r2-B2）。
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 编辑任务（归属老师；管理员只读——2026-09-20 权限边界）；关键字段修改自动写 LAB_TASK 修订留痕 */
    @PutMapping("/{taskId}")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<TaskService.TaskView> update(@PathVariable Long taskId,
                                               @RequestBody TaskUpdateRequest request) {
        return Result.success(taskService.update(taskId, request,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 删除/关闭任务（规则①）：未提交软删；已提交拒绝；已驳回置 CLOSED */
    @DeleteMapping("/{taskId}")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Void> delete(@PathVariable Long taskId) {
        taskService.delete(taskId, SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        return Result.success();
    }

    /** 开始任务（负责学生）：→IN_PROGRESS */
    @PostMapping("/{taskId}/start")
    @RequireRole("STUDENT")
    public Result<TaskService.TaskView> start(@PathVariable Long taskId) {
        return Result.success(taskService.start(taskId, SecurityUtils.currentUserId()));
    }

    /** 提交任务（负责学生）：追加提交行 →SUBMITTED，通知归属老师 */
    @PostMapping("/{taskId}/submit")
    @RequireRole("STUDENT")
    public Result<TaskService.SubmissionView> submit(@PathVariable Long taskId,
                                                     @RequestBody TaskSubmitRequest request) {
        return Result.success(taskService.submit(taskId, request, SecurityUtils.currentUserId()));
    }

    /** 验收（归属老师；管理员只读）：ACCEPT→ACCEPTED / REJECT→REJECTED(+意见)，通知学生 */
    @PostMapping("/{taskId}/review")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<TaskService.TaskView> review(@PathVariable Long taskId,
                                               @RequestBody TaskReviewRequest request) {
        return Result.success(taskService.review(taskId, request,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 提交历史：归属老师/ADMIN/负责学生本人可见，他人 403（Q6） */
    @GetMapping("/{taskId}/submissions")
    public Result<List<TaskService.SubmissionView>> submissions(@PathVariable Long taskId) {
        return Result.success(taskService.submissions(taskId,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** deadline 等字段修订明细（时间线） */
    @GetMapping("/{taskId}/revisions")
    public Result<List<RevisionService.RevisionView>> revisions(@PathVariable Long taskId) {
        return Result.success(taskService.listRevisions(taskId,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 学生我的任务待办：按课题分组一次拉全，可按状态过滤 */
    @GetMapping("/my")
    @RequireRole("STUDENT")
    public Result<List<TaskService.MyTaskGroup>> myTasks(@RequestParam(required = false) String status) {
        return Result.success(taskService.myTasks(SecurityUtils.currentUserId(), status));
    }

    /** 全局验收收件箱（T01）：教师=名下全部课题待验收队列（FIFO）；ADMIN 全量只读；学生无 */
    @GetMapping("/review-inbox")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<TaskService.InboxItem>> reviewInbox(@RequestParam(required = false) Long projectId) {
        return Result.success(taskService.reviewInbox(
                SecurityUtils.currentUserId(), SecurityUtils.currentRole(), projectId));
    }
}
