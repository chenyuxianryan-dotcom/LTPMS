package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.PageResult;
import com.ltpms.common.Result;
import com.ltpms.dto.ScheduleImportRequest;
import com.ltpms.entity.Schedule;
import com.ltpms.service.ScheduleService;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.Map;

/**
 * 课表管理接口，提供课表分页查询、Excel 导入、模板下载及批量删除（仅管理员）等功能。
 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 分页查询课表列表，可按学期筛选（仅管理员）。
     *
     * @param semesterId 学期 ID，可选
     * @param page       页码，默认 1
     * @param size       每页大小，默认 20
     * @return 分页后的课表列表
     */
    @GetMapping
    @RequireRole("ADMIN")
    public Result<PageResult<Schedule>> list(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(scheduleService.list(semesterId, page, size));
    }

    /**
     * 从 Excel 文件批量导入课表。
     * <p>管理员：导入整学期课表（覆盖该学期全部旧课表）；教师：仅导入本人课表（强制归属本人，只覆盖本人旧课表）。
     *
     * @param semesterId 归属学期 ID
     * @param file       上传的 Excel 文件
     * @return 导入结果（成功/失败数量等）
     * @throws IOException 读取文件失败时抛出
     */
    @PostMapping("/import")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<ScheduleService.ImportResult> importExcel(
            @RequestParam Long semesterId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(scheduleService.importSchedule(semesterId, file, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 课表照片识别草稿落库：接收结构化行，复用 Excel 导入的校验与落库逻辑。
     * 前端完成识别草稿预览与人工修正后调用。
     *
     * @param semesterId 归属学期 ID
     * @param request    结构化课表行
     * @return 导入结果
     */
    @PostMapping("/import/rows")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<ScheduleService.ImportResult> importRows(
            @RequestParam Long semesterId,
            @RequestBody ScheduleImportRequest request) {
        return Result.success(scheduleService.importFromRows(
                semesterId, request.getRows(), getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 下载课表 Excel 导入模板（管理员与教师均可）。
     *
     * @param response HTTP 响应，用于写入模板文件流
     * @throws IOException 写入响应流失败时抛出
     */
    @GetMapping("/import/template")
    @RequireRole({"ADMIN", "TEACHER"})
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] data = scheduleService.downloadTemplate();
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode("课表导入模板.xlsx", StandardCharsets.UTF_8));
        response.getOutputStream().write(data);
    }

    /**
     * 批量删除课表记录（仅管理员）。
     *
     * @param body 请求体，包含待删除记录的 ID 列表（键为 "ids"）
     * @return 操作结果
     */
    @PostMapping("/batch-delete")
    @RequireRole("ADMIN")
    public Result<Void> batchDelete(@RequestBody Map<String, List<Long>> body) {
        scheduleService.batchDelete(body.get("ids"));
        return Result.success();
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
