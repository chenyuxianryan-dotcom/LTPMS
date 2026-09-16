/**
 * 课程取色：同一门课程在周网格 / 卡片墙 / Popover 中保持同色。
 * 哈希算法保持可移植（31*h + c，取模 8），后端 PDF 导出（批次 4）需按同一算法复现。
 */

export interface ICourseColor {
  bg: string;
  fg: string;
  border: string;
}

const PALETTE_SIZE = 8;

/** 稳定取色索引：基于课程号哈希，不随加载顺序变化 */
export function courseColorIndex(key: string): number {
  let hash = 0;
  const text = key || 'unknown';
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  }
  return hash % PALETTE_SIZE;
}

/** 课程色（CSS 变量引用，色值定义见 styles/tokens.css） */
export function courseColor(key: string): ICourseColor {
  const index = courseColorIndex(key) + 1;
  return {
    bg: `var(--course-${index}-bg)`,
    fg: `var(--course-${index}-fg)`,
    border: `var(--course-${index}-border)`,
  };
}
