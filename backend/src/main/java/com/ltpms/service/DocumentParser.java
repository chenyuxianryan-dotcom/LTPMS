package com.ltpms.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.ltpms.common.BusinessException;
import com.ltpms.common.ErrorCode;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库文档解析：docx（POI，段落 + 表格按文档顺序）/ pdf（iText 文本层）/ txt / md。
 * 扫描版 PDF 无文本层 → 提取为空，按「未能解析出文本」拒收（OCR 属④ 多模态，不在本期）。
 */
public final class DocumentParser {

    private DocumentParser() {
    }

    /** 解析为「段落列表」，每项一个自然段（表格行合并为 tab 分隔的一行） */
    public static List<String> parse(Path path, String fileType) {
        List<String> paragraphs;
        try (InputStream in = Files.newInputStream(path)) {
            paragraphs = switch (fileType) {
                case "docx" -> parseDocx(in);
                case "pdf" -> parsePdf(in);
                case "txt", "md" -> parseText(in);
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的文件类型：" + fileType);
            };
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件解析失败，请确认文档未损坏且未加密");
        }
        List<String> cleaned = new ArrayList<>();
        for (String p : paragraphs) {
            String text = p == null ? "" : p.strip();
            if (!text.isEmpty()) {
                cleaned.add(text);
            }
        }
        if (cleaned.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "未能从文件中解析出文本；若是扫描件/图片型文档，请改用可复制文字的版本");
        }
        return cleaned;
    }

    private static List<String> parseDocx(InputStream in) throws IOException {
        List<String> out = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(in)) {
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    out.add(paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(cell.getText().replace("\n", " ").strip());
                        }
                        String line = String.join("\t", cells).strip();
                        if (!line.isEmpty()) {
                            out.add(line);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static List<String> parsePdf(InputStream in) throws IOException {
        List<String> out = new ArrayList<>();
        try (PdfDocument pdf = new PdfDocument(new PdfReader(in))) {
            int pages = pdf.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                String text = PdfTextExtractor.getTextFromPage(pdf.getPage(i));
                for (String line : text.split("\r?\n")) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    private static List<String> parseText(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return new ArrayList<>(List.of(text.split("\r?\n")));
    }
}
