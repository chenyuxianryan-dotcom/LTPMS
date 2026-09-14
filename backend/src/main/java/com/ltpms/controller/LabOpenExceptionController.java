package com.ltpms.controller;

import com.ltpms.annotation.OperationLog;
import com.ltpms.annotation.RequireRole;
import com.ltpms.common.Result;
import com.ltpms.dto.LabOpenExceptionRequest;
import com.ltpms.entity.LabOpenException;
import com.ltpms.service.LabOpeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实验室开放例外日接口：查询（登录可见）与增删（仅管理员）。
 */
@RestController
@RequestMapping("/api/v1/lab-open-exceptions")
@RequiredArgsConstructor
public class LabOpenExceptionController {

    private final LabOpeningService labOpeningService;

    /**
     * 查询例外日列表，可按学期与实验室过滤。
     *
     * @param semesterId 学期 ID，可选
     * @param labCode    实验室编号，可选
     * @return 例外日列表
     */
    @GetMapping
    public Result<List<LabOpenException>> list(@RequestParam(required = false) Long semesterId,
                                               @RequestParam(required = false) String labCode) {
        return Result.success(labOpeningService.listExceptions(semesterId, labCode));
    }

    /**
     * 新增例外日（仅管理员）。
     *
     * @param request 例外日信息
     * @return 操作结果
     */
    @PostMapping
    @RequireRole("ADMIN")
    @OperationLog(action = "LAB_OPEN_EXCEPTION_CREATE", targetType = "LAB_OPEN_EXCEPTION")
    public Result<Void> create(@Valid @RequestBody LabOpenExceptionRequest request) {
        labOpeningService.createException(request);
        return Result.success();
    }

    /**
     * 删除例外日（仅管理员）。
     *
     * @param id 记录 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    @OperationLog(action = "LAB_OPEN_EXCEPTION_DELETE", targetType = "LAB_OPEN_EXCEPTION")
    public Result<Void> delete(@PathVariable Long id) {
        labOpeningService.deleteException(id);
        return Result.success();
    }
}
