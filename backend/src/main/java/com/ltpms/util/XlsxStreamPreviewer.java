package com.ltpms.util;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * xlsx 流式预览（r2-B5）：SAX 事件读第一个工作表，绝不整表进内存；
 * 行数超上限立即中止解析（下载后查看），仅取回预览所需的前 N 行。
 */
public final class XlsxStreamPreviewer {

    /** 解析中止哨兵异常 */
    private static final class StopParsing extends RuntimeException {
        StopParsing() {
            super(null, null, false, false);
        }
    }

    private XlsxStreamPreviewer() {
    }

    /** 预览结果：rows 为截断后的行；rowLimitExceeded=文件行数超硬上限；truncated=还有后续行 */
    public record Preview(List<List<String>> rows, boolean rowLimitExceeded, boolean truncated) {
    }

    /**
     * 流式读取首个工作表。
     *
     * @param stream       xlsx 输入流（调用方负责关闭外层来源，这里不关）
     * @param previewRows  返回的最大行数
     * @param hardMaxRows  行数硬上限，超过即中止并标记 rowLimitExceeded
     */
    public static Preview preview(InputStream stream, int previewRows, int hardMaxRows) {
        try (OPCPackage pkg = OPCPackage.open(stream)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                return new Preview(List.of(), false, false);
            }
            List<List<String>> rows = new ArrayList<>();
            SheetHandler handler = new SheetHandler(sst, rows, previewRows, hardMaxRows);
            try (InputStream sheet = sheets.next()) {
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(true);
                SAXParser parser = factory.newSAXParser();
                XMLReader xmlReader = parser.getXMLReader();
                xmlReader.setContentHandler(handler);
                try {
                    xmlReader.parse(new InputSource(sheet));
                } catch (Exception stopOrError) {
                    // SAX 实现可能把哨兵异常包装为 SAXException，需沿 cause 链识别
                    if (!causedByStop(stopOrError)) {
                        throw stopOrError;
                    }
                }
            }
            boolean exceeded = handler.rowCount > hardMaxRows;
            boolean truncated = !exceeded && handler.rowCount > rows.size();
            return new Preview(rows, exceeded, truncated);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("xlsx 流式预览失败: " + e.getMessage(), e);
        }
    }

    private static boolean causedByStop(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof StopParsing) {
                return true;
            }
        }
        return false;
    }

    /** 只解析 &lt;row&gt;/&lt;c&gt;/&lt;v&gt; 节点，跳过样式与公式文本 */
    private static final class SheetHandler extends DefaultHandler {
        private final SharedStrings sst;
        private final List<List<String>> rows;
        private final int previewRows;
        private final int hardMaxRows;

        private List<String> currentRow;
        private final StringBuilder cellText = new StringBuilder();
        private boolean inValue;
        private boolean cellIsString;
        private int rowCount;

        SheetHandler(SharedStrings sst, List<List<String>> rows, int previewRows, int hardMaxRows) {
            this.sst = sst;
            this.rows = rows;
            this.previewRows = previewRows;
            this.hardMaxRows = hardMaxRows;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (localName) {
                case "row" -> {
                    currentRow = new ArrayList<>();
                    rowCount++;
                    if (rowCount > hardMaxRows) {
                        throw new StopParsing();
                    }
                }
                case "c" -> {
                    cellText.setLength(0);
                    cellIsString = "s".equals(attributes.getValue("t"));
                }
                case "v", "t" -> inValue = true;
                default -> {
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue) {
                cellText.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (localName) {
                case "v", "t" -> inValue = false;
                case "c" -> {
                    if (currentRow != null) {
                        currentRow.add(resolveCellText());
                    }
                }
                case "row" -> {
                    if (currentRow != null && rows.size() < previewRows) {
                        rows.add(currentRow);
                    }
                    currentRow = null;
                }
                default -> {
                }
            }
        }

        private String resolveCellText() {
            String raw = cellText.toString();
            if (cellIsString) {
                try {
                    return sst.getItemAt(Integer.parseInt(raw.trim())).getString();
                } catch (Exception e) {
                    return raw;
                }
            }
            return raw;
        }
    }
}
