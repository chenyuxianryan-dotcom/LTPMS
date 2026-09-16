package com.ltpms.util;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课表文本解析器：从 raw_schedule_text（如 "周一第1-4节(1-16周)"）解析出结构化时间槽。
 * 解析规则与 scripts/parse_schedule_time.py 对齐；周次提取复用 {@link WeekMath}。
 */
public final class ScheduleTimeParser {

    private ScheduleTimeParser() {
    }

    /** 解析出的时间槽：星期 / 起止节次 / 周次范围 / 单双周类型。 */
    public record Slot(int dayOfWeek, int startSection, int endSection, String weekRange, String weekType) {
    }

    private static final Map<String, Integer> DAY_MAP = Map.of(
            "一", 1, "二", 2, "三", 3, "四", 4, "五", 5, "六", 6, "日", 7);
    private static final Pattern DAY_PATTERN = Pattern.compile("周([一二三四五六日])");
    private static final Pattern SECTION_PATTERN = Pattern.compile("第(\\d+)-(\\d+)节");

    /**
     * 解析课表文本；星期或节次缺失时返回 null，不伪造数据。
     * 周次缺失时回退 "1-16"（与 Python 回填脚本口径一致）。
     */
    public static Slot parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        Matcher dayMatcher = DAY_PATTERN.matcher(rawText);
        if (!dayMatcher.find()) {
            return null;
        }
        Matcher sectionMatcher = SECTION_PATTERN.matcher(rawText);
        if (!sectionMatcher.find()) {
            return null;
        }
        int start = Integer.parseInt(sectionMatcher.group(1));
        int end = Integer.parseInt(sectionMatcher.group(2));
        if (start < 1 || end < start) {
            return null;
        }
        String weekType = rawText.contains("单周") ? "ODD" : rawText.contains("双周") ? "EVEN" : "ALL";
        Set<Integer> weeks = WeekMath.weeksFromBrackets(rawText);
        String weekRange = weeks.isEmpty() ? "1-16" : WeekMath.formatWeeks(weeks);
        return new Slot(DAY_MAP.get(dayMatcher.group(1)), start, end, weekRange, weekType);
    }
}
