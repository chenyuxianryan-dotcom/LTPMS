package com.ltpms.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 从班级名称中提取面向专业
 */
public class MajorExtractor {

    private static final Set<String> KNOWN_MAJORS = new HashSet<>(Arrays.asList(
            "计算机科学与技术", "软件工程", "网络工程", "信息安全", "物联网工程",
            "数据科学与大数据技术", "人工智能", "电子信息工程", "通信工程",
            "自动化", "电气工程及其自动化", "机械设计制造及其自动化", "数学与应用数学"
    ));

    private MajorExtractor() {
    }

    /**
     * 从班级名字符串中提取专业，多个专业去重后用逗号拼接
     */
    public static String extract(String classNames) {
        if (classNames == null || classNames.trim().isEmpty()) {
            return null;
        }

        String[] parts = classNames.split("[,，、\\s]+");
        Set<String> majors = new LinkedHashSet<>();

        for (String part : parts) {
            String cleaned = cleanClassName(part.trim());
            if (cleaned.isEmpty()) continue;

            String major = matchMajor(cleaned);
            if (major != null) {
                majors.add(major);
            }
        }

        return majors.isEmpty() ? null : String.join(",", majors);
    }

    private static String cleanClassName(String className) {
        // 移除常见前缀/后缀，如 "2023级", "2301班", "(本)"
        return className.replaceAll("^\\d{4}级", "")
                .replaceAll("\\d{2,4}班$", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .trim();
    }

    private static String matchMajor(String text) {
        // 完全匹配
        if (KNOWN_MAJORS.contains(text)) {
            return text;
        }
        // 包含匹配：取最长匹配
        String bestMatch = null;
        for (String major : KNOWN_MAJORS) {
            if (text.contains(major)) {
                if (bestMatch == null || major.length() > bestMatch.length()) {
                    bestMatch = major;
                }
            }
        }
        return bestMatch;
    }
}
