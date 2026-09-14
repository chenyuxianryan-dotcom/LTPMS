package com.ltpms.controller;

import com.ltpms.annotation.OperationLog;
import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.LabOpenBatchRequest;
import com.ltpms.dto.LabOpenCopyRequest;
import com.ltpms.dto.LabOpenGrid;
import com.ltpms.dto.LabOpenTimeRequest;
import com.ltpms.dto.LabOpeningOverview;
import com.ltpms.dto.LabOpenToday;
import com.ltpms.entity.LabOpenTime;
import com.ltpms.service.LabOpeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实验室开放管理接口：开放时段增删改查（写操作仅管理员）、批量覆盖式设置、
 * 从上学期复制，以及占用总览 / 单间周网格 / 今日开放视图（登录可见）。
 */
@RestController
@RequestMapping("/api/v1/lab-openings")
@RequiredArgsConstructor
public class LabOpeningController {

    private final LabOpeningService labOpeningService;

    /**
     * 查询开放时段列表，可按学期与实验室过滤。
     *
     * @param semesterId 学期 ID，可选
     * @param labCode    实验室编号，可选
     * @return 开放时段列表
     */
    @GetMapping
    public Result<List<LabOpenTime>> list(@RequestParam(required = false) Long semesterId,
                                          @RequestParam(required = false) String labCode) {
        return Result.success(labOpeningService.list(semesterId, labCode));
    }

    /**
     * 新增单条开放时段（仅管理员）。
     *
     * @param request 开放时段信息
     * @return 操作结果
     */
    @PostMapping
    @RequireRole("ADMIN")
    @OperationLog(action = "LAB_OPENING_CREATE", targetType = "LAB_OPENING")
    public Result<Void> create(@Valid @RequestBody LabOpenTimeRequest request) {
        labOpeningService.create(request);
        return Result.success();
    }

    /**
     * 更新单条开放时段（仅管理员）。
     *
     * @param id      记录 ID
     * @param request 开放时段信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequireRole("ADMIN")
    @OperationLog(action = "LAB_OPENING_UPDATE", targetType = "LAB_OPENING")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LabOpenTimeRequest request) {
        labOpeningService.update(id, request);
        return Result.success();
    }

    /**
     * 删除单条开放时段（仅管理员）。
     *
     * @param id 记录 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    @OperationLog(action = "LAB_OPENING_DELETE", targetType = "LAB_OPENING")
    public Result<Void> delete(@PathVariable Long id) {
        labOpeningService.delete(id);
        return Result.success();
    }

    /**
     * 批量覆盖式设置（仅管理员）：对选中实验室先删后插。
     *
     * @param request 批量设置请求
     * @return 实际写入的时段条数
     */
    @PostMapping("/batch")
    @RequireRole("ADMIN")
    @OperationLog(action = "LAB_OPEN_BATCH", targetType = "LAB_OPENING")
    public Result<Integer> batch(@Valid @RequestBody LabOpenBatchRequest request) {
        return Result.success(labOpeningService.batch(request));
    }

    /**
     * 从上学期复制开放计划（仅管理员）：覆盖式、幂等。
     *
     * @param request 复制请求
     * @return 实际复制的实验室间数
     */
    @PostMapping("/copy")
    @RequireRole("ADMIN")
    @OperationLog(action = "LAB_OPEN_COPY", targetType = "LAB_OPENING")
    public Result<Integer> copy(@Valid @RequestBody LabOpenCopyRequest request) {
        return Result.success(labOpeningService.copy(request));
    }

    /**
     * 占用总览：每间实验室的开放节时 / 占用节时 / 利用率 / 矛盾数 / 是否已配置。
     *
     * @param semesterId 学期 ID
     * @return 占用总览
     */
    @GetMapping("/overview")
    public Result<LabOpeningOverview> overview(@RequestParam Long semesterId) {
        return Result.success(labOpeningService.overview(semesterId));
    }

    /**
     * 单间实验室周网格（7 天 × 12 节四色态 + 闭馆日集合）。
     *
     * @param semesterId 学期 ID
     * @param labCode    实验室编号
     * @param week       周次，可选；缺省取当前周
     * @return 周网格
     */
    @GetMapping("/grid")
    public Result<LabOpenGrid> grid(@RequestParam Long semesterId,
                                    @RequestParam String labCode,
                                    @RequestParam(required = false) Integer week) {
        return Result.success(labOpeningService.grid(semesterId, labCode, week));
    }

    /**
     * 今日开放实验室（仪表盘小块 & 教师端）。
     *
     * @param semesterId 学期 ID，可选；缺省取当前学期
     * @return 今日开放信息
     */
    @GetMapping("/today")
    public Result<LabOpenToday> today(@RequestParam(required = false) Long semesterId) {
        return Result.success(labOpeningService.today(semesterId));
    }
}
