package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.CourseEditRequest;
import com.ltpms.entity.Course;
import com.ltpms.service.CourseService;
import com.ltpms.service.RevisionService;
import com.ltpms.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程管理接口，提供课程列表查询、课程详情、聚合行编辑与修订留痕等功能。
 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final JwtUtil jwtUtil;

    /**
     * 查询课程列表，可按学期筛选，结果受当前用户角色与权限约束。
     *
     * @param semesterId 学期 ID，可选；为空时返回全部可见课程
     * @return 课程列表
     */
    @GetMapping
    public Result<List<Course>> list(@RequestParam(required = false) Long semesterId) {
        return Result.success(courseService.list(semesterId, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 查询课程详情，包含课程基本信息。
     *
     * @param id 课程 ID
     * @return 课程详情
     */
    @GetMapping("/{id}")
    public Result<CourseService.CourseDetail> detail(@PathVariable Long id) {
        return Result.success(courseService.getDetail(id));
    }

    /**
     * 查询课程下的实验项目列表（基于课程详情）。
     *
     * @param id 课程 ID
     * @return 包含实验项目信息的课程详情
     */
    @GetMapping("/{id}/experiments")
    public Result<CourseService.CourseDetail> experiments(@PathVariable Long id) {
        return Result.success(courseService.getDetail(id));
    }

    /**
     * 编辑课程聚合行（教师限本人、管理员不限）。
     *
     * @param id      课程 ID
     * @param request 编辑请求，字段为 null 表示不修改
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Void> update(@PathVariable Long id, @RequestBody CourseEditRequest request) {
        courseService.update(id, request, getCurrentUserId(), getCurrentRole());
        return Result.success();
    }

    /**
     * 查询某课程的修订明细（教师限本人、管理员不限）。
     *
     * @param id 课程 ID
     * @return 修订明细列表
     */
    @GetMapping("/{id}/revisions")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<RevisionService.RevisionView>> revisions(@PathVariable Long id) {
        return Result.success(courseService.listRevisions(id, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 批量查询课程修订条数（行级笔形标记数据源）。
     *
     * @param ids 课程 ID 列表，逗号分隔
     * @return 课程 ID → 修订条数
     */
    @GetMapping("/revisions/flags")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Map<Long, Long>> revisionFlags(@RequestParam List<Long> ids) {
        return Result.success(courseService.revisionFlags(ids, getCurrentUserId(), getCurrentRole()));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }
        return null;
    }

    private String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            String authority = auth.getAuthorities().iterator().next().getAuthority();
            return authority.replace("ROLE_", "");
        }
        return null;
    }
}
