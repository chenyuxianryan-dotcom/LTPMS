package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.PageResult;
import com.ltpms.common.Result;
import com.ltpms.dto.LaboratoryRequest;
import com.ltpms.entity.Laboratory;
import com.ltpms.service.LaboratoryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 实验室管理接口，提供实验室的分页查询、增删改、Excel 导入及模板下载（仅管理员）等功能。
 */
@RestController
@RequestMapping("/api/v1/laboratories")
@RequiredArgsConstructor
public class LaboratoryController {

    private final LaboratoryService laboratoryService;

    /**
     * 分页查询实验室列表，支持关键字模糊搜索（仅管理员）。
     *
     * @param page   页码，默认 1
     * @param size   每页大小，默认 20
     * @param keyword 实验室名称/编号关键字，可选
     * @return 分页后的实验室列表
     */
    @GetMapping
    @RequireRole("ADMIN")
    public Result<PageResult<Laboratory>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(laboratoryService.list(page, size, keyword));
    }

    /**
     * 新增实验室（仅管理员）。
     *
     * @param request 实验室信息
     * @return 操作结果
     */
    @PostMapping
    @RequireRole("ADMIN")
    public Result<Void> create(@Valid @RequestBody LaboratoryRequest request) {
        laboratoryService.create(request);
        return Result.success();
    }

    /**
     * 更新指定实验室（仅管理员）。
     *
     * @param id      实验室 ID
     * @param request 实验室更新信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LaboratoryRequest request) {
        laboratoryService.update(id, request);
        return Result.success();
    }

    /**
     * 删除指定实验室（仅管理员）。
     *
     * @param id 实验室 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        laboratoryService.delete(id);
        return Result.success();
    }

    /**
     * 批量删除实验室（仅管理员）。逐条校验关联课表，存在关联的跳过并返回明细。
     *
     * @param ids 实验室 ID 列表
     * @return 批量删除结果（成功/失败数量与错误明细）
     */
    @PostMapping("/batch-delete")
    @RequireRole("ADMIN")
    public Result<LaboratoryService.BatchDeleteResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.success(laboratoryService.deleteBatch(ids));
    }

    /**
     * 从 Excel 文件批量导入实验室（仅管理员）。
     *
     * @param file 上传的 Excel 文件
     * @return 导入结果（成功/失败数量等）
     * @throws IOException 读取文件失败时抛出
     */
    @PostMapping("/import")
    @RequireRole("ADMIN")
    public Result<LaboratoryService.ImportResult> importExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(laboratoryService.importFromExcel(file));
    }

    /**
     * 下载实验室 Excel 导入模板（仅管理员）。
     *
     * @param response HTTP 响应，用于写入模板文件流
     * @throws IOException 写入响应流失败时抛出
     */
    @GetMapping("/import/template")
    @RequireRole("ADMIN")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] data = laboratoryService.downloadTemplate();
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode("实验室导入模板.xlsx", StandardCharsets.UTF_8));
        response.getOutputStream().write(data);
    }
}
