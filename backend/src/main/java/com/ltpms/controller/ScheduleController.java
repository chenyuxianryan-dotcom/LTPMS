package com.ltpms.controller;

import com.ltpms.annotation.RequireRole;
import com.ltpms.common.PageResult;
import com.ltpms.common.Result;
import com.ltpms.dto.ScheduleEditRequest;
import com.ltpms.dto.ScheduleImportRequest;
import com.ltpms.dto.ScheduleImportRow;
import com.ltpms.dto.UploadRemindRequest;
import com.ltpms.entity.Schedule;
import com.ltpms.service.ScheduleService;
import com.ltpms.service.RevisionService;
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
 * 课表管理接口，提供课表分页查询、教师课表导入与预览、周视图查询、
 * 行编辑与修订留痕、上传进度与催办、模板下载及批量删除（仅管理员）等功能。
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
     * 从 Excel 文件批量导入课表（仅教师端，强制归属本人，只覆盖本人旧课表）。
     *
     * @param semesterId       归属学期 ID
     * @param file             上传的 Excel 文件
     * @param confirmOverwrite 是否确认覆盖含修订留痕的旧记录
     * @return 导入结果（成功/失败数量等）
     * @throws IOException 读取文件失败时抛出
     */
    @PostMapping("/import")
    @RequireRole("TEACHER")
    public Result<ScheduleService.ImportResult> importExcel(
            @RequestParam Long semesterId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean confirmOverwrite) throws IOException {
        return Result.success(scheduleService.importSchedule(semesterId, file, getCurrentUserId(), confirmOverwrite));
    }

    /**
     * 课表照片识别草稿落库（仅教师端）：接收结构化行，复用 Excel 导入的校验与落库逻辑。
     *
     * @param semesterId       归属学期 ID
     * @param request          结构化课表行
     * @param confirmOverwrite 是否确认覆盖含修订留痕的旧记录
     * @return 导入结果
     */
    @PostMapping("/import/rows")
    @RequireRole("TEACHER")
    public Result<ScheduleService.ImportResult> importRows(
            @RequestParam Long semesterId,
            @RequestBody ScheduleImportRequest request,
            @RequestParam(defaultValue = "false") boolean confirmOverwrite) {
        return Result.success(scheduleService.importFromRows(
                semesterId, request.getRows(), getCurrentUserId(), confirmOverwrite));
    }

    /**
     * Excel 导入解析预览（仅教师端，不落库）：返回有效行数、逐行错误与修订冲突统计。
     *
     * @param semesterId 归属学期 ID
     * @param file       上传的 Excel 文件
     * @return 解析预览
     * @throws IOException 读取文件失败时抛出
     */
    @PostMapping("/import/preview")
    @RequireRole("TEACHER")
    public Result<ScheduleService.ImportPreview> previewImport(
            @RequestParam Long semesterId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(scheduleService.previewImport(semesterId, file, getCurrentUserId()));
    }

    /**
     * 课表照片识别草稿的解析预览（仅教师端，不落库）。
     *
     * @param semesterId 归属学期 ID
     * @param request    结构化课表行
     * @return 解析预览
     */
    @PostMapping("/import/rows/preview")
    @RequireRole("TEACHER")
    public Result<ScheduleService.ImportPreview> previewRows(
            @RequestParam Long semesterId,
            @RequestBody ScheduleImportRequest request) {
        return Result.success(scheduleService.previewRows(semesterId, request.getRows(), getCurrentUserId()));
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
     * 查询教师课表（周课表/卡片墙数据源）。
     * <p>教师固定查本人；管理员需指定 teacherId 查看任一教师。
     *
     * @param semesterId 学期 ID
     * @param week       周次过滤，可选
     * @param teacherId  管理员指定查看的教师 ID，可选
     * @return 教师课表项列表
     */
    @GetMapping("/mine")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<ScheduleService.MyScheduleItem>> mine(
            @RequestParam Long semesterId,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) Long teacherId) {
        return Result.success(scheduleService.mine(semesterId, week, getCurrentUserId(), getCurrentRole(), teacherId));
    }

    /**
     * 单条新增课表记录（教师限本人课表；管理员可指定教师）。
     *
     * @param semesterId 归属学期 ID
     * @param row        课表单行（课程号/课程名/总学时/实验室编号/上课班级/选课人数/上课时间）
     * @return 新增的课表记录
     */
    @PostMapping
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Schedule> create(@RequestParam Long semesterId, @RequestBody ScheduleImportRow row) {
        return Result.success(scheduleService.createOne(semesterId, row, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 单条删除课表记录（教师限本人、管理员不限）。
     *
     * @param id 课表记录 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Void> deleteOne(@PathVariable Long id) {
        scheduleService.deleteOne(id, getCurrentUserId(), getCurrentRole());
        return Result.success();
    }

    /**
     * 编辑课表行（教师限本人、管理员不限）。
     *
     * @param id      课表记录 ID
     * @param request 编辑请求，字段为 null 表示不修改
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Void> update(@PathVariable Long id, @RequestBody ScheduleEditRequest request) {
        scheduleService.updateSchedule(id, request, getCurrentUserId(), getCurrentRole());
        return Result.success();
    }

    /**
     * 查询某课表行的修订明细（教师限本人、管理员不限）。
     *
     * @param id 课表记录 ID
     * @return 修订明细列表
     */
    @GetMapping("/{id}/revisions")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<List<RevisionService.RevisionView>> revisions(@PathVariable Long id) {
        return Result.success(scheduleService.listRevisions(id, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 批量查询课表行修订条数（行级笔形标记数据源）。
     *
     * @param ids 课表记录 ID 列表，逗号分隔
     * @return 课表记录 ID → 修订条数
     */
    @GetMapping("/revisions/flags")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Map<Long, Long>> revisionFlags(@RequestParam List<Long> ids) {
        return Result.success(scheduleService.revisionFlags(ids, getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 统计各教师本学期课表上传进度（仅管理员）。
     *
     * @param semesterId 学期 ID
     * @return 教师上传状态列表
     */
    @GetMapping("/upload-progress")
    @RequireRole("ADMIN")
    public Result<List<ScheduleService.TeacherUploadStatus>> uploadProgress(@RequestParam Long semesterId) {
        return Result.success(scheduleService.uploadProgress(semesterId));
    }

    /**
     * 向指定教师发送课表上传催办通知（仅管理员）。
     *
     * @param request 催办请求，含学期 ID 与教师 ID 列表
     * @return 实际发送的催办数量
     */
    @PostMapping("/upload-remind")
    @RequireRole("ADMIN")
    public Result<Integer> uploadRemind(@RequestBody UploadRemindRequest request) {
        return Result.success(scheduleService.uploadRemind(request.getSemesterId(), request.getTeacherIds()));
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
