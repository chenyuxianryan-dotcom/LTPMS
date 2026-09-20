package com.ltpms.controller;

import com.ltpms.annotation.OperationLog;
import com.ltpms.annotation.RequireRole;
import com.ltpms.common.ErrorCode;
import com.ltpms.common.Result;
import com.ltpms.dto.project.EnsureContainerRequest;
import com.ltpms.dto.project.MemberAddRequest;
import com.ltpms.dto.project.ProjectRequest;
import com.ltpms.entity.Experiment;
import com.ltpms.entity.LabProject;
import com.ltpms.service.DatasetService;
import com.ltpms.service.ExperimentService;
import com.ltpms.service.ProjectService;
import com.ltpms.service.ProjectStatsService;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 课题接口：列表/详情/成员/任务列表与派发/进度统计/数据集入口。
 * 行级权限全部收口 ProjectAuthService；学生仅可达"详情/成员/任务列表/进度/数据读取"。
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final DatasetService datasetService;
    private final ExperimentService experimentService;
    private final ProjectStatsService statsService;

    /** 统一实验项目列表（含完成度%/逾期数/成员数，同源 stats）——教师/管理员/学生（学生仅所在课题） */
    @GetMapping
    @RequireRole({"ADMIN", "TEACHER", "STUDENT"})
    public Result<List<ProjectService.ProjectView>> list(
            @RequestParam(value = "source", required = false) String source) {
        return Result.success(projectService.list(SecurityUtils.currentUserId(), SecurityUtils.currentRole(), source));
    }

    /** 新建课题（仅教师；管理员只读——2026-09-20 权限边界；自建课题仅独立存在，课程/学期由容器承载——M-Q7 收口） */
    @PostMapping
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<LabProject> create(@RequestBody ProjectRequest request) {
        return Result.success(projectService.create(request,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /**
     * 课程容器懒生成（R1：幂等 GET-or-create；师生双侧读侧显式调用，禁止裸 GET 副作用）。
     * 并发命中已有容器直接 200 返回；生成即快照课程名单（source=AUTO_SNAPSHOT）。
     * 管理员只读（2026-09-20 权限边界）：命中已有返回，未生成 403。
     */
    @PostMapping("/ensure-course-container")
    @RequireRole({"ADMIN", "TEACHER", "STUDENT"})
    @OperationLog(action = "PROJECT_ENSURE_CONTAINER", targetType = "LAB_PROJECT")
    public Result<LabProject> ensureCourseContainer(@RequestBody EnsureContainerRequest request) {
        return Result.success(projectService.ensureCourseContainer(request.getCourseId(),
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()).project());
    }

    /** 批量 ensure 本人当前学期课程容器（统一列表页入口；ADMIN 不被动建容器，返回空列表） */
    @PostMapping("/ensure-my-course-containers")
    @RequireRole({"ADMIN", "TEACHER", "STUDENT"})
    @OperationLog(action = "PROJECT_ENSURE_MY_CONTAINERS", targetType = "LAB_PROJECT")
    public Result<List<LabProject>> ensureMyCourseContainers() {
        return Result.success(projectService.ensureMyCourseContainers(
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 课题详情（成员可读：归属老师/ADMIN/在组学生；头部汇总字段与列表同源） */
    @GetMapping("/{id}")
    public Result<ProjectService.ProjectView> detail(@PathVariable Long id) {
        return Result.success(projectService.view(id,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 编辑课题/容器（归属老师；管理员只读；名称与描述可改，course/semester 锁定不动） */
    @PutMapping("/{id}")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<LabProject> update(@PathVariable Long id, @RequestBody ProjectRequest request) {
        return Result.success(projectService.update(id, request,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 归档定格（S7）：归档后全写接口 403，仅只读可查；已归档幂等返回 */
    @PostMapping("/{id}/archive")
    @RequireRole({"ADMIN", "TEACHER"})
    @OperationLog(action = "PROJECT_ARCHIVE", targetType = "LAB_PROJECT")
    public Result<LabProject> archive(@PathVariable Long id) {
        return Result.success(projectService.archive(id,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 删除课题：SYSTEM 容器 403（可归档）；自建课题仅空壳可删（无成员/任务/数据） */
    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "TEACHER"})
    @OperationLog(action = "PROJECT_DELETE", targetType = "LAB_PROJECT")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id, SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        return Result.success();
    }

    // ---------------------------------------------------------------- 成员

    /** 成员列表（可读视角） */
    @GetMapping("/{id}/members")
    public Result<List<ProjectService.MemberView>> members(@PathVariable Long id) {
        return Result.success(projectService.members(id,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 学生库勾选批量带入（source=MANUAL，快照） */
    @PostMapping("/{id}/members")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Integer> addMembers(@PathVariable Long id, @RequestBody MemberAddRequest request) {
        return Result.success(projectService.addMembers(id, request,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 项目内导入名单 Excel：陌生学号自动建档并带入（source=CREATE） */
    @PostMapping("/{id}/members/import")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<com.ltpms.service.StudentService.ImportResult> importMembers(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(projectService.importMembers(id, file,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 移出成员：其未完成任务置 CLOSED 留痕（r2-B1） */
    @DeleteMapping("/{id}/members/{studentId}")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long studentId) {
        projectService.removeMember(id, studentId,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        return Result.success();
    }

    // ---------------------------------------------------------------- 任务（项目侧）

    /** 项目任务列表（成员可读，学生侧提交详情另接口裁剪） */
    @GetMapping("/{id}/tasks")
    public Result<List<TaskService.TaskView>> tasks(@PathVariable Long id) {
        return Result.success(taskService.listByProject(id,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /** 派发任务：多人=复制多条独立任务（归属老师；管理员只读） */
    @PostMapping("/{id}/tasks")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<TaskService.TaskView>> createTasks(@PathVariable Long id,
                                                          @RequestBody com.ltpms.dto.project.TaskCreateRequest request) {
        return Result.success(taskService.create(id, request,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    // ---------------------------------------------------------------- 实验目录（容器投影）

    /** 容器实验目录（成员可读：归属老师/ADMIN/在组学生；目录=容器课程 experiment 行） */
    @GetMapping("/{id}/experiments")
    public Result<List<Experiment>> experiments(@PathVariable Long id) {
        return Result.success(experimentService.listByContainer(id,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    // ---------------------------------------------------------------- 进度（单一事实源）

    /** 汇总条+学生×任务矩阵+任务列，一次请求出全（r2-B1，后端仅此一个 stats 出口） */
    @GetMapping("/{id}/progress")
    public Result<ProjectStatsService.ProjectStats> progress(@PathVariable Long id) {
        Long userId = SecurityUtils.currentUserId();
        String role = SecurityUtils.currentRole();
        com.ltpms.entity.LabProject project = projectService.detail(id, userId, role);
        return Result.success(statsService.stats(project.getId()));
    }

    // ---------------------------------------------------------------- 数据集（项目侧）

    /** 项目数据集列表（成员可见） */
    @GetMapping("/{id}/datasets")
    public Result<List<DatasetService.DatasetView>> datasets(@PathVariable Long id) {
        return Result.success(datasetService.listByProject(id,
                SecurityUtils.currentUserId(), SecurityUtils.currentRole()));
    }

    /**
     * multipart 新建数据集 v1。同名已存在 → 业务码 46001 + 既有摘要，
     * 前端二选一"挂为 v(N+1) / 换名新建"（r2-C4）。
     */
    @PostMapping("/{id}/datasets")
    public Result<DatasetService.DatasetView> createDataset(
            @PathVariable Long id,
            @RequestParam(value = "datasetName", required = false) String datasetName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file) {
        DatasetService.CreateOutcome outcome = datasetService.create(id, datasetName, description,
                file, SecurityUtils.currentUserId(), SecurityUtils.currentRole());
        if (outcome.conflict()) {
            return new Result<>(ErrorCode.DATASET_NAME_CONFLICT.getCode(),
                    ErrorCode.DATASET_NAME_CONFLICT.getMessage(),
                    datasetService.toView(outcome.existing()));
        }
        return Result.success(datasetService.toView(outcome.created()));
    }
}
