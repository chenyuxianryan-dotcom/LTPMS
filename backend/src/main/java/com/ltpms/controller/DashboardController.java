package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.DashboardStats;
import com.ltpms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘统计接口，提供管理员与教师两类首页统计数据。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取管理员仪表盘统计数据（仅管理员可访问）。
     *
     * @return 管理员视角的整体统计信息
     */
    @GetMapping("/admin")
    @RequireRole("ADMIN")
    public Result<DashboardStats> admin() {
        return Result.success(dashboardService.adminStats());
    }

    /**
     * 获取当前教师仪表盘统计数据（仅教师可访问）。
     *
     * @return 当前教师视角的统计信息
     */
    @GetMapping("/teacher")
    @RequireRole("TEACHER")
    public Result<DashboardStats> teacher() {
        return Result.success(dashboardService.teacherStats(getCurrentUserId()));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }
        return null;
    }
}
