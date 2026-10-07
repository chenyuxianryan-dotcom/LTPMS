package com.ltpms.service;

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.ltpms.dto.NarrativeExportRequest;
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
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
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
     * 将报表明细导出为 PDF 字节内容（横向 A4，标题含学年，表头跨页重复）。
     *
     * @param title          报表名称，如「课程项目统计」
     * @param academicYear   学年，如 2024-2025
     * @param detail         报表明细列表
     * @param hasPersonHours 是否包含人时数列
     * @return PDF 文件字节数组
     * @throws IOException 生成文件失败时抛出
     */
    public byte[] exportPdf(String title, String academicYear, List<ReportResponse.Detail> detail,
                            boolean hasPersonHours) throws IOException {
        PdfFont font = cjkFont();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(bos));
            Document doc = new Document(pdfDoc, PageSize.A4.rotate());
            String heading = (academicYear == null || academicYear.isBlank())
                    ? title : title + "（" + academicYear + " 学年）";
            doc.add(new Paragraph(heading).setFont(font).setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(12));

            Table table = new Table(hasPersonHours
                    ? new float[]{2, 3, 2, 3, 2, 2, 2}
                    : new float[]{2, 3, 2, 3, 2, 2});
            table.setWidth(UnitValue.createPercentValue(100));
            String[] headers = hasPersonHours
                    ? new String[]{"实验室编号", "实验室名称", "课程号", "课程名", "授课教师", "实验项目数", "人时数"}
                    : new String[]{"实验室编号", "实验室名称", "课程号", "课程名", "授课教师", "实验项目数"};
            for (String header : headers) {
                table.addHeaderCell(pdfCell(font, header, 10));
            }
            for (ReportResponse.Detail item : detail) {
                table.addCell(pdfCell(font, item.getLabCode(), 9));
                table.addCell(pdfCell(font, item.getLabName(), 9));
                table.addCell(pdfCell(font, item.getCourseCode(), 9));
                table.addCell(pdfCell(font, item.getCourseName(), 9));
                table.addCell(pdfCell(font, item.getTeacherName(), 9));
                table.addCell(pdfCell(font, String.valueOf(item.getExperimentCount()), 9));
                if (hasPersonHours) {
                    table.addCell(pdfCell(font,
                            item.getPersonHours() == null ? "" : item.getPersonHours().toPlainString(), 9));
                }
            }
            doc.add(table);
            // 关闭 Document 才会落盘全部内容（页树/xref 在 close 时写出）
            doc.close();
            return bos.toByteArray();
        }
    }

    /**
     * 将叙事报告渲染为 PDF 字节内容（纵向 A4：标题 + 学期 + 正文 + 统计数字附表）。
     *
     * @param request 前端提交的叙事内容
     * @return PDF 文件字节数组
     * @throws IOException 生成文件失败时抛出
     */
    public byte[] exportNarrativePdf(NarrativeExportRequest request) throws IOException {
        PdfFont font = cjkFont();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(bos));
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.add(new Paragraph(blankTo(request.getTitle(), "AI 叙事报告")).setFont(font).setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER));
            if (notBlank(request.getSemester())) {
                doc.add(new Paragraph(request.getSemester()).setFont(font).setFontSize(11)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER).setMarginBottom(16));
            }
            for (String line : textLines(request.getText())) {
                doc.add(new Paragraph(line.isBlank() ? " " : line.trim()).setFont(font).setFontSize(12)
                        .setMultipliedLeading(1.6f));
            }
            if (request.getNumbers() != null && !request.getNumbers().isEmpty()) {
                doc.add(new Paragraph("统计数据").setFont(font).setFontSize(13).setMarginTop(18).setMarginBottom(6));
                Table table = new Table(new float[]{3, 2});
                table.setWidth(UnitValue.createPercentValue(60));
                table.addHeaderCell(pdfCell(font, "指标", 10));
                table.addHeaderCell(pdfCell(font, "数值", 10));
                for (NarrativeExportRequest.NumberItem item : request.getNumbers()) {
                    table.addCell(pdfCell(font, blankTo(item.getLabel(), ""), 10));
                    table.addCell(pdfCell(font, blankTo(item.getValue(), ""), 10));
                }
                doc.add(table);
            }
            doc.close();
            return bos.toByteArray();
        }
    }

    /**
     * 将叙事报告渲染为 Excel 字节内容（标题/学期/正文逐行写入，统计数字为「指标-数值」两列）。
     *
     * @param request 前端提交的叙事内容
     * @return Excel 文件字节数组
     * @throws IOException 生成文件失败时抛出
     */
    public byte[] exportNarrativeExcel(NarrativeExportRequest request) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("叙事报告");
            int rowNum = 0;
            sheet.createRow(rowNum++).createCell(0).setCellValue(blankTo(request.getTitle(), "AI 叙事报告"));
            if (notBlank(request.getSemester())) {
                sheet.createRow(rowNum++).createCell(0).setCellValue(request.getSemester());
            }
            rowNum++;
            for (String line : textLines(request.getText())) {
                sheet.createRow(rowNum++).createCell(0).setCellValue(line);
            }
            if (request.getNumbers() != null && !request.getNumbers().isEmpty()) {
                rowNum++;
                sheet.createRow(rowNum++).createCell(0).setCellValue("统计数据");
                Row header = sheet.createRow(rowNum++);
                header.createCell(0).setCellValue("指标");
                header.createCell(1).setCellValue("数值");
                for (NarrativeExportRequest.NumberItem item : request.getNumbers()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(blankTo(item.getLabel(), ""));
                    row.createCell(1).setCellValue(blankTo(item.getValue(), ""));
                }
            }
            sheet.setColumnWidth(0, 90 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 将实验项目全项目报告渲染为 PDF（纵向 A4，教学闭环收口 T09）。
     *
     * <p>所见即所得：六节叙述正文由前端原样提交，后端只做排版（小标题「一、二、…」放大区分）；
     * 附录表（成员完成情况）由调用方从库直出后传入，不经模型。沿用静态字体 + 每文档新建 PdfFont
     * + close 后取字节的既有教训。</p>
     *
     * @param title          文档标题
     * @param subtitle       副标题（项目编号 ｜ 数据截至 …），可空
     * @param narrativeText  六节叙述正文
     * @param appendixTitle  附录标题
     * @param appendixHeaders 附录表头
     * @param appendixRows   附录数据行（每行与表头等长）
     * @return PDF 文件字节数组
     * @throws IOException 生成文件失败时抛出
     */
    public byte[] exportProjectReportPdf(String title, String subtitle, String narrativeText,
                                         String appendixTitle, String[] appendixHeaders,
                                         List<String[]> appendixRows) throws IOException {
        PdfFont font = cjkFont();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(bos));
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.add(new Paragraph(blankTo(title, "实验项目报告")).setFont(font).setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER));
            if (notBlank(subtitle)) {
                doc.add(new Paragraph(subtitle).setFont(font).setFontSize(10)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER).setMarginBottom(16));
            }
            for (String line : textLines(narrativeText)) {
                // 模型可能输出 markdown 小标题（## 一、…）：剥掉井号，按中文序号识别标题放大
                String cleaned = line.trim().replaceAll("^#{1,6}\\s*", "");
                boolean heading = cleaned.matches("^[一二三四五六七八九十]+、.*");
                Paragraph paragraph = new Paragraph(cleaned.isEmpty() ? " " : cleaned).setFont(font)
                        .setMultipliedLeading(1.6f);
                if (heading) {
                    paragraph.setFontSize(13).setMarginTop(12).setMarginBottom(4);
                } else {
                    paragraph.setFontSize(11);
                }
                doc.add(paragraph);
            }
            if (appendixHeaders != null && appendixRows != null && !appendixRows.isEmpty()) {
                doc.add(new Paragraph(blankTo(appendixTitle, "附录")).setFont(font).setFontSize(14)
                        .setMarginTop(20).setMarginBottom(6));
                float[] widths = new float[appendixHeaders.length];
                Arrays.fill(widths, 1f);
                Table table = new Table(widths);
                table.setWidth(UnitValue.createPercentValue(100));
                for (String header : appendixHeaders) {
                    table.addHeaderCell(pdfCell(font, header, 10));
                }
                for (String[] row : appendixRows) {
                    for (int i = 0; i < appendixHeaders.length; i++) {
                        table.addCell(pdfCell(font, i < row.length ? blankTo(row[i], "-") : "-", 9));
                    }
                }
                doc.add(table);
            }
            doc.close();
            return bos.toByteArray();
        }
    }

    private static Cell pdfCell(PdfFont font, String value, float fontSize) {
        return new Cell().add(new Paragraph(value == null ? "" : value).setFont(font).setFontSize(fontSize));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankTo(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }

    /** 正文按行拆分，去掉首尾空行，保留段间空行 */
    private static List<String> textLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] lines = text.replace("\r", "").split("\n", -1);
        int start = 0;
        int end = lines.length;
        while (start < end && lines[start].isBlank()) {
            start++;
        }
        while (end > start && lines[end - 1].isBlank()) {
            end--;
        }
        return Arrays.asList(Arrays.copyOfRange(lines, start, end));
    }

    private static final String CJK_FONT_RESOURCE = "/fonts/NotoSansSC-Regular.ttf";
    private static volatile FontProgram cjkFontProgram;

    /** 懒加载并缓存解析后的中文字体（首次约 300ms，之后复用） */
    private static FontProgram cjkFontProgram() throws IOException {
        FontProgram program = cjkFontProgram;
        if (program == null) {
            synchronized (ReportService.class) {
                program = cjkFontProgram;
                if (program == null) {
                    try (InputStream in = ReportService.class.getResourceAsStream(CJK_FONT_RESOURCE)) {
                        if (in == null) {
                            throw new IOException("中文字体资源缺失: " + CJK_FONT_RESOURCE);
                        }
                        program = FontProgramFactory.createFont(in.readAllBytes());
                        cjkFontProgram = program;
                    }
                }
            }
        }
        return program;
    }

    /** 每次导出创建新的 PdfFont（PdfFont 绑定文档，跨文档复用会抛异常） */
    private static PdfFont cjkFont() throws IOException {
        return PdfFontFactory.createFont(cjkFontProgram(), PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
    }
}
