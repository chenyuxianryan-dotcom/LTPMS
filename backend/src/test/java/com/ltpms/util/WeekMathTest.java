package com.ltpms.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class WeekMathTest {

    @Test
    void shouldParseRange() {
        Set<Integer> weeks = WeekMath.parseWeeks("1-16", null);
        assertEquals(16, weeks.size());
        assertTrue(weeks.contains(1));
        assertTrue(weeks.contains(16));
    }

    @Test
    void shouldParseDiscreteList() {
        assertEquals(Set.of(1, 3, 5), WeekMath.parseWeeks("1,3,5", null));
    }

    @Test
    void shouldParseSingleWeek() {
        assertEquals(Set.of(8), WeekMath.parseWeeks("8", null));
    }

    @Test
    void shouldFilterByOddType() {
        Set<Integer> weeks = WeekMath.parseWeeks("1-16", "ODD");
        assertEquals(8, weeks.size());
        assertEquals(Set.of(1, 3, 5, 7, 9, 11, 13, 15), weeks);
    }

    @Test
    void shouldFilterByEvenType() {
        assertEquals(Set.of(2, 4, 6, 8, 10, 12, 14, 16), WeekMath.parseWeeks("1-16", "EVEN"));
    }

    @Test
    void shouldIgnoreIllegalSegments() {
        assertEquals(Set.of(3), WeekMath.parseWeeks("abc,3,x-y", null));
        assertEquals(Set.of(), WeekMath.parseWeeks(null, "ODD"));
    }

    @Test
    void shouldCountWeeksWithTypeFilter() {
        assertEquals(8, WeekMath.weekCount("1-16", "ODD"));
        assertEquals(16, WeekMath.weekCount("1-16", "ALL"));
        assertEquals(0, WeekMath.weekCount("", null));
    }

    @Test
    void shouldFormatWeeksCompressed() {
        Set<Integer> full = new TreeSet<>();
        for (int w = 1; w <= 16; w++) {
            full.add(w);
        }
        assertEquals("1-16", WeekMath.formatWeeks(full));
        assertEquals("1,3,5", WeekMath.formatWeeks(Set.of(1, 3, 5)));
        assertEquals("1-3,7,9-10", WeekMath.formatWeeks(Set.of(1, 2, 3, 7, 9, 10)));
        assertEquals("", WeekMath.formatWeeks(Set.of()));
    }

    @Test
    void shouldComputeWeekNumber() {
        LocalDate week1 = LocalDate.parse("2026-09-07");
        assertEquals(1, WeekMath.weekNumberOf(LocalDate.parse("2026-09-07"), week1));
        assertEquals(1, WeekMath.weekNumberOf(LocalDate.parse("2026-09-13"), week1));
        assertEquals(2, WeekMath.weekNumberOf(LocalDate.parse("2026-09-14"), week1));
        assertEquals(4, WeekMath.weekNumberOf(LocalDate.parse("2026-09-28"), week1));
        assertEquals(0, WeekMath.weekNumberOf(LocalDate.parse("2026-09-06"), week1));
        assertEquals(0, WeekMath.weekNumberOf(null, week1));
        assertEquals(0, WeekMath.weekNumberOf(week1, null));
    }

    @Test
    void shouldComputeWeekDateRange() {
        LocalDate[] range = WeekMath.weekDateRange(LocalDate.parse("2026-09-07"), 4);
        assertEquals(LocalDate.parse("2026-09-28"), range[0]);
        assertEquals(LocalDate.parse("2026-10-04"), range[1]);
        assertEquals(DayOfWeek.MONDAY, range[0].getDayOfWeek());
    }

    @Test
    void shouldDetectWeeksOverlap() {
        assertTrue(WeekMath.weeksOverlap(WeekMath.parseWeeks("1-16", null), WeekMath.parseWeeks("3,5", null)));
        assertFalse(WeekMath.weeksOverlap(WeekMath.parseWeeks("1-16", null), Set.of()));
        assertFalse(WeekMath.weeksOverlap(WeekMath.parseWeeks("1-8", null), WeekMath.parseWeeks("9-16", null)));
        assertFalse(WeekMath.weeksOverlap(WeekMath.parseWeeks("1-16", "ODD"), WeekMath.parseWeeks("1-16", "EVEN")));
        assertTrue(WeekMath.weeksOverlap(WeekMath.parseWeeks("1-16", "ODD"), WeekMath.parseWeeks("3-4", null)));
    }

    @Test
    void shouldDetectSectionsOverlap() {
        assertTrue(WeekMath.sectionsOverlap(1, 4, 3, 6));
        assertTrue(WeekMath.sectionsOverlap(1, 8, 1, 8));
        assertFalse(WeekMath.sectionsOverlap(1, 4, 5, 8));
        assertFalse(WeekMath.sectionsOverlap(null, 4, 1, 2));
    }
}
