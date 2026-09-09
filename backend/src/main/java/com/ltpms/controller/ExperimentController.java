package com.ltpms.controller;

import com.ltpms.common.Result;
import com.ltpms.dto.ExperimentRequest;
import com.ltpms.entity.Experiment;
import com.ltpms.service.ExperimentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 实验项目管理接口，提供实验项目的查询、增删改、Excel 导入导出及上学期复制等功能。
 */
@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService experimentService;

    /**
     * 查询实验项目列表，可按课程筛选，结果受当前用户角色与权限约束。
     *
     * @param courseId 课程 ID，可选；为空时返回全部可见实验项目
     * @return 实验项目列表
     */
    @GetMapping
    public Result<List<Experiment>> list(@RequestParam(required = false) Long courseId) {
        return Result.success(experimentService.list(courseId, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 新增实验项目。
     *
     * @param request 实验项目信息
     * @return 操作结果
     */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ExperimentRequest request) {
        experimentService.create(request, getCurrentUserId(), getCurrentRole());
        return Result.success();
    }

    /**
     * 更新指定实验项目。
     *
     * @param id      实验项目 ID
     * @param request 实验项目更新信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ExperimentRequest request) {
        experimentService.update(id, request, getCurrentUserId(), getCurrentRole());
        return Result.success();
    }

    /**
     * 删除指定实验项目。
     *
     * @param id 实验项目 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        experimentService.delete(id, getCurrentUserId(), getCurrentRole());
        return Result.success();
    }

    /**
     * 从 Excel 文件批量导入实验项目。
     *
     * @param courseId 归属课程 ID
     * @param file     上传的 Excel 文件
     * @return 导入结果（成功/失败数量等）
     * @throws IOException 读取文件失败时抛出
     */
    @PostMapping("/import")
    public Result<ExperimentService.ImportResult> importExcel(
            @RequestParam Long courseId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(experimentService.importFromExcel(courseId, file, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 下载实验项目 Excel 导入模板。
     *
     * @param response HTTP 响应，用于写入模板文件流
     * @throws IOException 写入响应流失败时抛出
     */
    @GetMapping("/import/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] data = experimentService.downloadTemplate();
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode("实验项目导入模板.xlsx", StandardCharsets.UTF_8));
        response.getOutputStream().write(data);
    }

    /**
     * 从上一学期复制实验项目到当前课程。
     *
     * @param courseId 目标课程 ID
     * @return 复制成功的实验项目数量
     */
    @PostMapping("/copy-from-previous")
    public Result<Integer> copyFromPrevious(@RequestParam Long courseId) {
        return Result.success(experimentService.copyFromPrevious(courseId, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 上学期智能迁移：预览可从上学期复制的实验项目（不落库）。
     *
     * @param semesterId 目标学期 ID，可选
     * @return 迁移草稿列表
     */
    @GetMapping("/migration/preview")
    public Result<List<ExperimentService.MigrationDraft>> migrationPreview(
            @RequestParam(required = false) Long semesterId) {
        return Result.success(experimentService.previewMigration(semesterId, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 上学期智能迁移：确认后批量复制上学期实验项目到目标学期。
     *
     * @param semesterId 目标学期 ID，可选
     * @return 迁移结果（课程数 + 实验项目数）
     */
    @PostMapping("/migration/confirm")
    public Result<ExperimentService.MigrationResult> migrationConfirm(
            @RequestParam(required = false) Long semesterId) {
        return Result.success(experimentService.batchCopyFromPrevious(semesterId, getCurrentUserId(), getCurrentRole()));
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
