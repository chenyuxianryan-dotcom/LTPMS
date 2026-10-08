import { describe, expect, it } from 'vitest';
import { courseColor, courseColorIndex } from './courseColor';

describe('courseColorIndex —— 课程取色哈希', () => {
  it('同一课程号多次取色结果稳定（不随加载顺序变化）', () => {
    expect(courseColorIndex('NET302')).toBe(courseColorIndex('NET302'));
  });

  it('锁定与后端 PDF 导出共用的哈希口径期望值', () => {
    expect(courseColorIndex('NET302')).toBe(0);
    expect(courseColorIndex('CS101')).toBe(2);
    expect(courseColorIndex('DB202')).toBe(6);
    expect(courseColorIndex('软件分析')).toBe(1);
  });

  it('空课程号回退到 unknown 桶', () => {
    expect(courseColorIndex('')).toBe(courseColorIndex('unknown'));
  });

  it('索引恒落在 8 色调色板内且足够分散', () => {
    const buckets = new Set<number>();
    for (let i = 0; i < 40; i += 1) {
      const index = courseColorIndex(`COURSE${i}`);
      expect(index).toBeGreaterThanOrEqual(0);
      expect(index).toBeLessThan(8);
      buckets.add(index);
    }
    expect(buckets.size).toBe(8);
  });
});

describe('courseColor —— CSS 变量引用', () => {
  it('返回 1..8 的三色变量（索引 +1，对齐 tokens.css 命名）', () => {
    expect(courseColor('CS101')).toEqual({
      bg: 'var(--course-3-bg)',
      fg: 'var(--course-3-fg)',
      border: 'var(--course-3-border)',
    });
  });

  it('调色板首尾索引映射到 1 与 8', () => {
    expect(courseColorIndex('NET302')).toBe(0);
    expect(courseColor('NET302').bg).toBe('var(--course-1-bg)');
    expect(courseColor('DB202').bg).toBe('var(--course-7-bg)');
  });
});
