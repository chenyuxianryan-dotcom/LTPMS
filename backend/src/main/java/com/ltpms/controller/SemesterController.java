package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.SemesterRequest;
import com.ltpms.entity.Semester;
import com.ltpms.service.SemesterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学期管理接口，提供学期列表查询、当前学期获取及学期的增改（仅管理员）等功能。
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
}
