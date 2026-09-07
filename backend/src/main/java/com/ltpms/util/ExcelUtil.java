package com.ltpms.util;

import org.apache.poi.ss.usermodel.*;

/**
 * Excel 工具类，提供从单元格安全读取字符串、整数、浮点数等通用方法。
 */
public class ExcelUtil {

    private ExcelUtil() {
    }

    /**
     * 从单元格读取字符串值，按类型做格式化（日期转字符串、数值去小数等）。
     *
     * @param cell 单元格，可为 null
     * @return 字符串值；单元格为空或类型不支持时返回 null
     */
    public static String getStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value)) {
                    yield String.valueOf((long) value);
                }
                yield String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    /**
     * 从单元格读取整数值。
     *
     * @param cell 单元格，可为 null
     * @return 整数值；为空或无法解析时返回 null
     */
    public static Integer getIntValue(Cell cell) {
        String value = getStringValue(cell);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从单元格读取浮点数值。
     *
     * @param cell 单元格，可为 null
     * @return 浮点数值；为空或无法解析时返回 null
     */
    public static Double getDoubleValue(Cell cell) {
        String value = getStringValue(cell);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
