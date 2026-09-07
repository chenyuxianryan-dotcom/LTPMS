package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.PageResult;
import com.ltpms.common.Result;
import com.ltpms.dto.TeacherStatusRequest;
import com.ltpms.entity.User;
import com.ltpms.service.TeacherService;
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

/**
 * 教师管理接口，提供教师分页查询、Excel 导入、密码重置、状态变更及模板下载（仅管理员）等功能。
 */
@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    /**
     * 分页查询教师列表，支持关键字搜索（仅管理员）。
     *
     * @param page    页码，默认 1
     * @param size    每页大小，默认 20
     * @param keyword 教师姓名/工号关键字，可选
     * @return 分页后的教师列表
     */
    @GetMapping
    @RequireRole("ADMIN")
    public Result<PageResult<User>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(teacherService.list(page, size, keyword));
    }

    /**
     * 从 Excel 文件批量导入教师（仅管理员）。
     *
     * @param file 上传的 Excel 文件
     * @return 导入结果（成功/失败数量等）
     * @throws IOException 读取文件失败时抛出
     */
    @PostMapping("/import")
    @RequireRole("ADMIN")
    public Result<TeacherService.ImportResult> importExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(teacherService.importFromExcel(file));
    }

    /**
     * 重置指定教师的登录密码为默认密码（仅管理员）。
     *
     * @param id 教师（用户）ID
     * @return 操作结果
     */
    @PostMapping("/{id}/reset-password")
    @RequireRole("ADMIN")
    public Result<Void> resetPassword(@PathVariable Long id) {
        teacherService.resetPassword(id);
        return Result.success();
    }

    /**
     * 更新指定教师的状态（启用/禁用，仅管理员）。
     *
     * @param id      教师（用户）ID
     * @param request 状态变更请求，包含目标状态
     * @return 操作结果
     */
    @PatchMapping("/{id}/status")
    @RequireRole("ADMIN")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody TeacherStatusRequest request) {
        teacherService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    /**
     * 下载教师 Excel 导入模板（仅管理员）。
     *
     * @param response HTTP 响应，用于写入模板文件流
     * @throws IOException 写入响应流失败时抛出
     */
    @GetMapping("/import/template")
    @RequireRole("ADMIN")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] data = teacherService.downloadTemplate();
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode("教师导入模板.xlsx", StandardCharsets.UTF_8));
        response.getOutputStream().write(data);
    }
}
