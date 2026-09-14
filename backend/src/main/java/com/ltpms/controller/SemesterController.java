package com.ltpms.controller;

import com.ltpms.annotation.OperationLog;
import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.SemesterCalendar;
import com.ltpms.dto.SemesterRequest;
import com.ltpms.dto.Week1StartDateRequest;
import com.ltpms.entity.Semester;
import com.ltpms.service.SemesterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学期管理接口，提供学期列表查询、当前学期获取、学期的增改（仅管理员）
 * 以及周历（第 1 周起始日 + 周次↔日期映射）的设置与查询等功能。
 */
@RestController
@RequestMapping("/api/v1/semesters")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    /**
     * 查询学期列表，可按状态筛选。
     *
     * @param status 学期状态，可选；为空时返回全部
     * @return 学期列表
     */
    @GetMapping
    public Result<List<Semester>> list(@RequestParam(required = false) String status) {
        return Result.success(semesterService.list(status));
    }

    /**
     * 获取当前生效的学期。
     *
     * @return 当前学期信息
     */
    @GetMapping("/current")
    public Result<Semester> current() {
        return Result.success(semesterService.getCurrentSemester());
    }

    /**
     * 新增学期（仅管理员）。
     *
     * @param request 学期信息
     * @return 操作结果
     */
    @PostMapping
    @RequireRole("ADMIN")
    public Result<Void> create(@Valid @RequestBody SemesterRequest request) {
        semesterService.create(request);
        return Result.success();
    }

    /**
     * 更新指定学期（仅管理员）。
     *
     * @param id      学期 ID
     * @param request 学期更新信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SemesterRequest request) {
        semesterService.update(id, request);
        return Result.success();
    }

    /**
     * 获取学期周历（第 1 周起始日 + 周次↔日期映射表，含每周节假日标记）。
     *
     * @param id 学期 ID
     * @return 周历信息
     */
    @GetMapping("/{id}/calendar")
    public Result<SemesterCalendar> calendar(@PathVariable Long id) {
        return Result.success(semesterService.getCalendar(id));
    }

    /**
     * 设置学期第 1 周起始日（仅管理员；不受「已开始不可编辑」限制，ACTIVE 学期亦可配置）。
     *
     * @param id      学期 ID
     * @param request 第 1 周起始日
     * @return 操作结果
     */
    @PutMapping("/{id}/calendar")
    @RequireRole("ADMIN")
    @OperationLog(action = "SEMESTER_CALENDAR_SET", targetType = "SEMESTER")
    public Result<Void> setCalendar(@PathVariable Long id, @Valid @RequestBody Week1StartDateRequest request) {
        semesterService.setWeek1StartDate(id, request.getWeek1StartDate());
        return Result.success();
    }
}
