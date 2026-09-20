/** 课题项目模块内共享的小工具（仅本模块使用，不进全局 utils） */

/** 后端 LocalDateTime 序列化为 ISO 串（如 2026-09-18T10:30:00），展示转「09-18 10:30」 */
export function fmtDateTime(iso?: string | null): string {
  if (!iso) return '-';
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
  if (!m) return iso;
  const now = new Date();
  const sameYear = Number(m[1]) === now.getFullYear();
  const ymd = `${m[2]}-${m[3]}`;
  return `${sameYear ? '' : `${m[1]}-`}${ymd} ${m[4]}:${m[5]}`;
}

export function fmtFullDateTime(iso?: string | null): string {
  return iso ? iso.replace('T', ' ').replace(/\.\d+$/, '') : '-';
}

/** el-date-picker 的 value-format 与后端 ISO LocalDateTime 对齐 */
export const DATE_TIME_VALUE_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

/** 文件大小展示 */
export function fmtSize(bytes?: number | null): string {
  if (bytes == null) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

/** 文件名去扩展名（上传默认数据集名） */
export function baseName(filename: string): string {
  const i = filename.lastIndexOf('.');
  return i > 0 ? filename.slice(0, i) : filename;
}
