package com.ltpms.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 周次解析与运算工具：统一 "1-16"、"1,3,5"、"8" 语义及单双周过滤，
 * 供课表解析、开放管理、占用视图与利用率口径共用，避免多份实现产生偏差。
 */
public final class WeekMath {

    private WeekMath() {
    }

    /**
     * 解析周次范围字符串，逗号分隔，支持 "1-16" 区间与单值，并按单双周类型过滤。
     *
     * @param weekRange 周次范围文本，如 "1-16"、"1,3,5"、"8"
     * @param weekType  周类型：ODD/单周、EVEN/双周，其余视为全周
     * @return 升序周次集合；入参为空时返回空集合
     */
    public static Set<Integer> parseWeeks(String weekRange, String weekType) {
        Set<Integer> weeks = new TreeSet<>();
        if (weekRange == null || weekRange.isBlank()) {
            return weeks;
        }
        for (String part : weekRange.split(",")) {
            String segment = part.trim();
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.contains("-")) {
                String[] bounds = segment.split("-", 2);
                try {
                    int from = Integer.parseInt(bounds[0].trim());
                    int to = Integer.parseInt(bounds[1].trim());
                    for (int w = from; w <= to; w++) {
                        weeks.add(w);
                    }
                } catch (NumberFormatException ignored) {
                    // 非法周次段跳过，不影响其余解析
                }
            } else {
                try {
                    weeks.add(Integer.parseInt(segment));
                } catch (NumberFormatException ignored) {
                    // 同上
                }
            }
        }
        applyWeekType(weeks, weekType);
        return weeks;
    }

    /**
     * 解析周次范围字符串（不区分单双周）。
     *
     * @param weekRange 周次范围文本
     * @return 升序周次集合
     */
    public static Set<Integer> parseWeeks(String weekRange) {
        return parseWeeks(weekRange, null);
    }

    /**
     * 统计生效周数（已含单双周过滤）。
     *
     * @param weekRange 周次范围文本
     * @param weekType  周类型
     * @return 生效周数
     */
    public static int weekCount(String weekRange, String weekType) {
        return parseWeeks(weekRange, weekType).size();
    }

    /**
     * 将周次集合压缩为文本：连续段用 "a-b" 表示，其余用逗号连接的单个周次。
     *
     * @param weeks 周次集合
     * @return 如 "1-16"、"1,3,5"；空集合返回空串
     */
    public static String formatWeeks(Set<Integer> weeks) {
        if (weeks == null || weeks.isEmpty()) {
            return "";
        }
        List<Integer> sorted = new ArrayList<>(new TreeSet<>(weeks));
        StringBuilder sb = new StringBuilder();
        int start = sorted.get(0);
        int prev = start;
        for (int i = 1; i < sorted.size(); i++) {
            int current = sorted.get(i);
            if (current != prev + 1) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(start == prev ? String.valueOf(start) : start + "-" + prev);
                start = current;
            }
            prev = current;
        }
        if (sb.length() > 0) {
            sb.append(",");
        }
        sb.append(start == prev ? String.valueOf(start) : start + "-" + prev);
        return sb.toString();
    }

    /**
     * 计算日期落在第几周（以周一起点计）。
     *
     * @param date          目标日期
     * @param week1StartDate 第 1 周起始日
     * @return 1 起的周序号；week1StartDate 为空时返回 0，日期早于起始日时返回 0 或负数
     */
    public static int weekNumberOf(LocalDate date, LocalDate week1StartDate) {
        if (date == null || week1StartDate == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(week1StartDate, date);
        return (int) (Math.floorDiv(days, 7) + 1);
    }

    /**
     * 由第 1 周起始日推算某周的日期区间（起始日 + 6 天）。
     *
     * @param week1StartDate 第 1 周起始日
     * @param weekNo         周序号（1 起）
     * @return 长度为 2 的数组：[起始日, 结束日]
     */
    public static LocalDate[] weekDateRange(LocalDate week1StartDate, int weekNo) {
        LocalDate start = week1StartDate.plusWeeks(weekNo - 1L);
        return new LocalDate[]{start, start.plusDays(6)};
    }

    /**
     * 判断两组周次是否存在交集。
     *
     * @param a 周次集合 A
     * @param b 周次集合 B
     * @return 存在公共周次时为 true
     */
    public static boolean weeksOverlap(Set<Integer> a, Set<Integer> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        Set<Integer> smaller = a.size() <= b.size() ? a : b;
        Set<Integer> larger = smaller == a ? b : a;
        for (Integer week : smaller) {
            if (larger.contains(week)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断两个节次闭区间是否重叠。
     *
     * @param aStart 区间 A 起节
     * @param aEnd   区间 A 止节
     * @param bStart 区间 B 起节
     * @param bEnd   区间 B 止节
     * @return 重叠时为 true；任一参数为空时返回 false
     */
    public static boolean sectionsOverlap(Integer aStart, Integer aEnd, Integer bStart, Integer bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        return aStart <= bEnd && bStart <= aEnd;
    }

    /**
     * 计算学期总周数（含第 1 周起始日所在周）。
     *
     * @param week1StartDate 第 1 周起始日
     * @param endDate        学期结束日期
     * @return 总周数；起始日晚于结束日或日期缺失时返回 0
     */
    public static int totalWeeks(LocalDate week1StartDate, LocalDate endDate) {
        if (week1StartDate == null || endDate == null || endDate.isBefore(week1StartDate)) {
            return 0;
        }
        return (int) (ChronoUnit.DAYS.between(week1StartDate, endDate) / 7) + 1;
    }

    /**
     * 从含括号的原始课表文本中提取周次（如 "第(1-16)周"、"第(2-8,10)周"），不做单双周过滤。
     *
     * @param raw 原始课表时间文本
     * @return 升序周次集合；无括号或无数字时返回空集合
     */
    public static Set<Integer> weeksFromBrackets(String raw) {
        Set<Integer> weeks = new TreeSet<>();
        if (raw == null || raw.isBlank()) {
            return weeks;
        }
        Matcher bracketMatcher = Pattern.compile("\\(([^()]+)\\)").matcher(raw);
        while (bracketMatcher.find()) {
            String segment = bracketMatcher.group(1);
            Matcher weekMatcher = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)|(\\d+)").matcher(segment);
            while (weekMatcher.find()) {
                if (weekMatcher.group(1) != null) {
                    int from = Integer.parseInt(weekMatcher.group(1));
                    int to = Integer.parseInt(weekMatcher.group(2));
                    for (int w = from; w <= to; w++) {
                        weeks.add(w);
                    }
                } else {
                    weeks.add(Integer.parseInt(weekMatcher.group(3)));
                }
            }
        }
        return weeks;
    }

    /**
     * 从原始课表时间文本解析周次集合：括号内提取 + 单双周嗅探过滤。
     *
     * @param raw 原始课表时间文本
     * @return 升序周次集合；无周次信息时返回空集合（调用方自行决定缺省）
     */
    public static Set<Integer> parseWeeksFromRawText(String raw) {
        Set<Integer> weeks = weeksFromBrackets(raw);
        if (weeks.isEmpty() || raw == null) {
            return weeks;
        }
        if (raw.contains("单周")) {
            weeks.removeIf(w -> w % 2 == 0);
        } else if (raw.contains("双周")) {
            weeks.removeIf(w -> w % 2 == 1);
        }
        return weeks;
    }

    private static void applyWeekType(Set<Integer> weeks, String weekType) {
        if ("ODD".equalsIgnoreCase(weekType) || "单周".equals(weekType)) {
            weeks.removeIf(w -> w % 2 == 0);
        } else if ("EVEN".equalsIgnoreCase(weekType) || "双周".equals(weekType)) {
            weeks.removeIf(w -> w % 2 == 1);
        }
    }
}
