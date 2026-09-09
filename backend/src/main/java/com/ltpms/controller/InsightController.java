package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.InsightStats;
import com.ltpms.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据智能聚合接口，统一暴露就绪度体检 / 利用率热力图 / 异常预警 / 冲突检测。
 * 前端仪表盘与阿实 Agent 共用，避免多 controller 重复查询。
 */
@RestController
@RequestMapping("/api/v1/insight")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    /**
     * 课程就绪度体检（管理员=全校，教师=本人课程）。
     */
    @GetMapping("/readiness")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<InsightStats.ReadinessItem>> readiness() {
        return Result.success(insightService.readinessReport(getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 实验室利用率热力图数据（可选按学期筛选）。
     */
    @GetMapping("/utilization")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<InsightStats.UtilizationItem>> utilization(
            @RequestParam(required = false) Long semesterId) {
        return Result.success(insightService.utilizationHeatmap(semesterId));
    }

    /**
     * 异常预警事件（超排/闲置/课时异常）。
     */
    @GetMapping("/anomalies")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<InsightStats.AnomalyItem>> anomalies(
            @RequestParam(required = false) Long semesterId) {
        return Result.success(insightService.detectAnomalies(semesterId));
    }

    /**
     * 排课冲突检测（只读诊断，管理员与教师均可查看）。
     */
    @GetMapping("/conflicts")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<InsightStats.ConflictItem>> conflicts(
            @RequestParam(required = false) Long semesterId) {
        return Result.success(insightService.detectConflicts(semesterId));
    }

    /**
     * 一次返回全部聚合结果（阿实 Agent 与仪表盘一站式拉取）。
     */
    @GetMapping("/all")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Map<String, Object>> all(@RequestParam(required = false) Long semesterId) {
        Long userId = getCurrentUserId();
        String role = getCurrentRole();
        return Result.success(Map.of(
                "readiness", insightService.readinessReport(userId, role),
                "utilization", insightService.utilizationHeatmap(semesterId),
                "anomalies", insightService.detectAnomalies(semesterId),
                "conflicts", insightService.detectConflicts(semesterId)
        ));
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
