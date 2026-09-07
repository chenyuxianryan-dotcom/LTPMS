package com.ltpms.service;

import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import com.ltpms.dto.ReportDetailItem;
import com.ltpms.dto.ReportResponse;
import com.ltpms.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * 报表统计服务，提供课程项目统计与人时数统计的数据聚合，以及 Excel/PDF 导出能力。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

    /**
     * 统计课程项目数据，教师且仅汇总时仅统计本人课程。
     *
     * @param academicYear   学年，如 2024-2025
     * @param aggregateOnly  是否仅汇总
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @return 课程项目统计报表
     */
    public ReportResponse courseProjectStats(
            String academicYear,
            boolean aggregateOnly,
            Long currentUserId,
            String role) {
        boolean isTeacher = "TEACHER".equals(role);
        Long teacherId = (isTeacher && aggregateOnly) ? currentUserId : null;

        List<ReportDetailItem> items = reportMapper.selectCourseProjectStats(academicYear, teacherId);
        return buildResponse(items, isTeacher, aggregateOnly, false);
    }

    /**
     * 统计人时数数据，教师且仅汇总时仅统计本人课程。
     *
     * @param academicYear   学年，如 2024-2025
     * @param aggregateOnly  是否仅汇总
     * @param currentUserId 当前登录用户 ID
     * @param role          当前用户角色
     * @return 人时数统计报表
     */
    public ReportResponse personHours(
            String academicYear,
            boolean aggregateOnly,
            Long currentUserId,
            String role) {
        boolean isTeacher = "TEACHER".equals(role);
        Long teacherId = (isTeacher && aggregateOnly) ? currentUserId : null;

        List<ReportDetailItem> items = reportMapper.selectPersonHours(academicYear, teacherId);
        return buildResponse(items, isTeacher, aggregateOnly, true);
    }

    private ReportResponse buildResponse(
            List<ReportDetailItem> items, boolean isTeacher, boolean aggregateOnly, boolean hasPersonHours) {
        long courseCount = items.stream().map(ReportDetailItem::getCourseCode).distinct().count();
        long experimentCount = items.stream().mapToLong(ReportDetailItem::getExperimentCount).sum();
        BigDecimal totalPersonHours = items.stream()
                .map(ReportDetailItem::getPersonHours)
                .filter(ph -> ph != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ReportResponse.Summary summary = new ReportResponse.Summary(courseCount, experimentCount, totalPersonHours);

        List<ReportResponse.Detail> detail;
        String detailScope;
        if (isTeacher && aggregateOnly) {
            detail = items.stream()
                    .map(i -> new ReportResponse.Detail(
                            i.getLabCode(), i.getLabName(), i.getCourseCode(), i.getCourseName(),
                            i.getTeacherName(), i.getExperimentCount(),
                            hasPersonHours ? i.getPersonHours() : null))
                    .toList();
            detailScope = "SELF";
        } else {
            detail = items.stream()
                    .map(i -> new ReportResponse.Detail(
                            i.getLabCode(), i.getLabName(), i.getCourseCode(), i.getCourseName(),
                            i.getTeacherName(), i.getExperimentCount(),
                            hasPersonHours ? i.getPersonHours() : null))
                    .toList();
            detailScope = "ALL";
        }

        return new ReportResponse(summary, detail, detailScope);
    }

    /**
     * 将报表明细导出为 Excel 字节内容。
     *
     * @param detail        报表明细列表
     * @param hasPersonHours 是否包含人时数列
     * @return Excel 文件字节数组
     * @throws IOException 生成文件失败时抛出
     */
    public byte[] exportExcel(List<ReportResponse.Detail> detail, boolean hasPersonHours) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("报表");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("实验室编号");
            header.createCell(1).setCellValue("实验室名称");
            header.createCell(2).setCellValue("课程号");
            header.createCell(3).setCellValue("课程名");
            header.createCell(4).setCellValue("授课教师");
            header.createCell(5).setCellValue("实验项目数");
            if (hasPersonHours) {
                header.createCell(6).setCellValue("人时数");
            }

            int rowNum = 1;
            for (ReportResponse.Detail item : detail) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getLabCode());
                row.createCell(1).setCellValue(item.getLabName());
                row.createCell(2).setCellValue(item.getCourseCode());
                row.createCell(3).setCellValue(item.getCourseName());
                row.createCell(4).setCellValue(item.getTeacherName());
                row.createCell(5).setCellValue(item.getExperimentCount());
                if (hasPersonHours && item.getPersonHours() != null) {
                    row.createCell(6).setCellValue(item.getPersonHours().doubleValue());
                }
            }

            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 将报表明细导出为 PDF 字节内容。
     *
     * @param detail         报表明细列表
     * @param hasPersonHours 是否包含人时数列
     * @return PDF 文件字节数组
     * @throws BusinessException 当前 PDF 导出尚未实现时抛出
     */
    public byte[] exportPdf(List<ReportResponse.Detail> detail, boolean hasPersonHours) {
        throw new BusinessException(
                ErrorCode.NOT_IMPLEMENTED,
                "PDF 导出功能待接入 Noto Sans SC 字体后实现");
    }
}
