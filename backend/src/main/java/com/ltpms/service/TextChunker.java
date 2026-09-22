package com.ltpms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 知识库文本切分：标题识别 + 段落合并。
 * 目标段长 700 字（中文近义密度高，长段会稀释向量），同段内保留所属标题供出处展示。
 */
public final class TextChunker {

    /** 单段目标长度（字符） */
    public static final int MAX_CHARS = 700;

    /** 标题只认章/节/篇/部分级；「第X条」是正文段落，误当标题会把条款正文整条吞掉 */
    private static final Pattern[] HEADING_PATTERNS = {
            Pattern.compile("^#{1,6}\\s*\\S+"),
            Pattern.compile("^第[一二三四五六七八九十百零〇\\d]+[章节篇]"),
            Pattern.compile("^第[一二三四五六七八九十百零〇\\d]+部分"),
            Pattern.compile("^[一二三四五六七八九十]+[、.．]"),
            Pattern.compile("^（[一二三四五六七八九十\\d]+）"),
            Pattern.compile("^\\d+(\\.\\d+)*[、.．\\s]"),
            Pattern.compile("^[０-９]+[、.．]"),
    };
    private static final Pattern ARTICLE_PREFIX =
            Pattern.compile("^第[一二三四五六七八九十百零〇\\d]+条");
    private static final Pattern ENDS_WITH_PUNCTUATION =
            Pattern.compile(".*[。！？；：，、,.;:!?]$");

    private TextChunker() {
    }

    /** 切分结果：heading 为所属标题（可能为 null），content 为段落正文 */
    public record Chunk(String heading, String content) {
    }

    public static List<Chunk> chunk(List<String> paragraphs) {
        List<Chunk> chunks = new ArrayList<>();
        String heading = null;
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (isHeading(paragraph)) {
                flush(chunks, heading, buffer);
                heading = normalizeHeading(paragraph);
                continue;
            }
            for (String piece : splitOversize(paragraph)) {
                if (buffer.length() > 0 && buffer.length() + piece.length() + 1 > MAX_CHARS) {
                    flush(chunks, heading, buffer);
                }
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(piece);
            }
        }
        flush(chunks, heading, buffer);
        return chunks;
    }

    private static void flush(List<Chunk> chunks, String heading, StringBuilder buffer) {
        String content = buffer.toString().strip();
        buffer.setLength(0);
        if (!content.isEmpty()) {
            chunks.add(new Chunk(heading, content));
        }
    }

    /** 单段超过 MAX_CHARS 时按句号切到 ≤ MAX_CHARS（长表格行 / 无空行长文兜底） */
    private static List<String> splitOversize(String paragraph) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHARS, paragraph.length());
            if (end < paragraph.length()) {
                int cut = paragraph.lastIndexOf('。', end);
                if (cut > start + MAX_CHARS / 2) {
                    end = cut + 1;
                }
            }
            pieces.add(paragraph.substring(start, end));
            start = end;
        }
        return pieces;
    }

    private static boolean isHeading(String line) {
        if (line.length() > 60) {
            return false;
        }
        for (Pattern pattern : HEADING_PATTERNS) {
            if (pattern.matcher(line).find()) {
                return true;
            }
        }
        if (ARTICLE_PREFIX.matcher(line).find()) {
            return false;
        }
        // 无编号的短行（≤20 字）且不以句读收尾：视为标题（如「总则」「请假流程」）
        return line.length() <= 20 && !ENDS_WITH_PUNCTUATION.matcher(line).matches();
    }

    private static String normalizeHeading(String line) {
        String heading = line.replaceAll("^#{1,6}\\s*", "").strip();
        return heading.length() > 255 ? heading.substring(0, 255) : heading;
    }
}
