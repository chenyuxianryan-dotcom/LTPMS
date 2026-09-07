package com.ltpms.controller;

import com.ltpms.common.Result;
import com.ltpms.entity.Course;
import com.ltpms.service.CourseService;
import com.ltpms.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程管理接口，提供课程列表查询、课程详情及实验项目查询等功能。
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
