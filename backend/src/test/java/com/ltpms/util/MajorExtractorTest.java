package com.ltpms.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MajorExtractorTest {

    @Test
    void shouldExtractSingleMajor() {
        String result = MajorExtractor.extract("计算机科学与技术2301班");
        assertEquals("计算机科学与技术", result);
    }

    @Test
    void shouldExtractMultipleMajors() {
        String result = MajorExtractor.extract("计算机科学与技术2301班,软件工程2302班");
        assertEquals("计算机科学与技术,软件工程", result);
    }

    @Test
    void shouldReturnNullWhenNoMatch() {
        String result = MajorExtractor.extract("未知专业2301班");
        assertNull(result);
    }
}
