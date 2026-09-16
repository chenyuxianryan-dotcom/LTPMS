package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.PageResult;
import com.ltpms.common.Result;
import com.ltpms.dto.TeacherCreateRequest;
import com.ltpms.dto.TeacherStatusRequest;
import com.ltpms.dto.TeacherUpdateRequest;
import com.ltpms.dto.TeacherVO;
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
import java.util.List;

/**
 * 教师管理接口，提供教师分页查询、新增、编辑、Excel 导入、密码重置、状态变更及模板下载（仅管理员）等功能。
 */
@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    /**
     * 分页查询教师列表，支持关键字搜索（仅管理员）。返回脱敏视图（不含密码等内部字段）。
     *
     * @param page    页码，默认 1
     * @param size    每页大小，默认 20
     * @param keyword 教师姓名/工号关键字，可选
     * @return 分页后的教师脱敏视图列表
     */
    @GetMapping
    @RequireRole("ADMIN")
    public Result<PageResult<TeacherVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(teacherService.list(page, size, keyword));
    }

    /**
     * 新增单个教师（仅管理员）。初始密码为默认密码 Ltpms@{工号}，首次登录需修改。
     *
     * @param request 教师信息
     * @return 操作结果
     */
    @PostMapping
    @RequireRole("ADMIN")
    public Result<Void> create(@Valid @RequestBody TeacherCreateRequest request) {
        teacherService.create(request);
        return Result.success();
    }

    /**
     * 编辑教师基础信息（仅管理员，工号不可修改）。
     *
     * @param id      教师（用户）ID
     * @param request 教师更新信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TeacherUpdateRequest request) {
        teacherService.update(id, request);
        return Result.success();
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
     * 批量删除教师（仅管理员）。逐条校验教师身份与关联数据，
     * 存在关联课表/课程的跳过并返回明细。
     *
     * @param ids 教师（用户）ID 列表
     * @return 批量删除结果（成功/失败数量与错误明细）
     */
    @PostMapping("/batch-delete")
    @RequireRole("ADMIN")
    public Result<TeacherService.BatchDeleteResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.success(teacherService.deleteBatch(ids));
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
