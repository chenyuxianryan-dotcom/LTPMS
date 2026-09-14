package com.ltpms.controller;

import com.ltpms.annotation.OperationLog;
import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.HolidayRequest;
import com.ltpms.entity.Holiday;
import com.ltpms.service.HolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 节假日管理接口，提供按学期过滤的查询（登录可见）与新增/删除（仅管理员）功能。
 */
@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    /**
     * 查询节假日列表，可按学期过滤（holiday_date ∈ [学期开始日期, 学期结束日期]）。
     *
     * @param semesterId 学期 ID，可选；为空时返回全部
     * @return 节假日列表
     */
    @GetMapping
    public Result<List<Holiday>> list(@RequestParam(required = false) Long semesterId) {
        return Result.success(holidayService.list(semesterId));
    }

    /**
     * 新增节假日（仅管理员），支持日期区间批量展开。
     *
     * @param request 节假日信息
     * @return 实际新增的天数
     */
    @PostMapping
    @RequireRole("ADMIN")
    @OperationLog(action = "HOLIDAY_CREATE", targetType = "HOLIDAY")
    public Result<Integer> create(@Valid @RequestBody HolidayRequest request) {
        return Result.success(holidayService.create(request));
    }

    /**
     * 删除指定节假日（仅管理员）。
     *
     * @param id 节假日 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    @OperationLog(action = "HOLIDAY_DELETE", targetType = "HOLIDAY")
    public Result<Void> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return Result.success();
    }
}
