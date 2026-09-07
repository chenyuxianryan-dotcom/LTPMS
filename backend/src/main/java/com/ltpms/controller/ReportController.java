package com.ltpms.controller;

import com.ltpms.common.Result;
import com.ltpms.dto.ReportResponse;
import com.ltpms.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 报表统计接口，提供课程项目统计、人时数统计及其 Excel/PDF 导出功能。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 查询课程项目统计数据。
     *
     * @param academicYear   学年，如 2024-2025
     * @param aggregateOnly  是否仅汇总（教师将被强制汇总）
     * @return 课程项目统计报表数据
     */
    @GetMapping("/course-project-stats")
    public Result<ReportResponse> courseProjectStats(
            @RequestParam String academicYear,
            @RequestParam(required = false, defaultValue = "false") boolean aggregateOnly) {
        return Result.success(reportService.courseProjectStats(academicYear, resolveAggregateOnly(aggregateOnly),
                getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 查询人时数统计数据。
     *
     * @param academicYear   学年，如 2024-2025
     * @param aggregateOnly  是否仅汇总（教师将被强制汇总）
     * @return 人时数统计报表数据
     */
    @GetMapping("/person-hours")
    public Result<ReportResponse> personHours(
            @RequestParam String academicYear,
            @RequestParam(required = false, defaultValue = "false") boolean aggregateOnly) {
        return Result.success(reportService.personHours(academicYear, resolveAggregateOnly(aggregateOnly),
                getCurrentUserId(), getCurrentRole()));
    }

    /**
     * 导出课程项目统计报表（支持 Excel 或 PDF）。
     *
     * @param academicYear   学年
     * @param aggregateOnly  是否仅汇总
     * @param format         导出格式，excel 或 pdf
     * @param response       HTTP 响应，用于写入文件流
     * @throws IOException   写入响应流失败时抛出
     */
    @GetMapping("/course-project-stats/export")
    public void exportCourseProjectStats(
            @RequestParam String academicYear,
            @RequestParam(required = false, defaultValue = "false") boolean aggregateOnly,
            @RequestParam String format,
            HttpServletResponse response) throws IOException {
        ReportResponse report = reportService.courseProjectStats(academicYear, resolveAggregateOnly(aggregateOnly),
                getCurrentUserId(), getCurrentRole());
        doExport(report, format, false, "课程项目统计", response);
    }

    /**
     * 导出人时数统计报表（支持 Excel 或 PDF）。
     *
     * @param academicYear   学年
     * @param aggregateOnly  是否仅汇总
     * @param format         导出格式，excel 或 pdf
     * @param response       HTTP 响应，用于写入文件流
     * @throws IOException   写入响应流失败时抛出
     */
    @GetMapping("/person-hours/export")
    public void exportPersonHours(
            @RequestParam String academicYear,
            @RequestParam(required = false, defaultValue = "false") boolean aggregateOnly,
            @RequestParam String format,
            HttpServletResponse response) throws IOException {
        ReportResponse report = reportService.personHours(academicYear, resolveAggregateOnly(aggregateOnly),
                getCurrentUserId(), getCurrentRole());
        doExport(report, format, true, "人时数统计", response);
    }

    private void doExport(ReportResponse report, String format, boolean hasPersonHours,
                          String filename, HttpServletResponse response) throws IOException {
        byte[] data;
        String extension;
        if ("pdf".equalsIgnoreCase(format)) {
            data = reportService.exportPdf(report.getDetail(), hasPersonHours);
            extension = ".pdf";
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        } else {
            data = reportService.exportExcel(report.getDetail(), hasPersonHours);
            extension = ".xlsx";
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode(filename + extension, StandardCharsets.UTF_8));
        response.getOutputStream().write(data);
    }

    private boolean resolveAggregateOnly(boolean aggregateOnly) {
        // 教师强制 aggregateOnly=true
        return "TEACHER".equals(getCurrentRole()) || aggregateOnly;
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
