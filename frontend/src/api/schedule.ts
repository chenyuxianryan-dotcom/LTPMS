/** 课表模块 /schedules（管理员） */
import { download, get, post, upload } from '@/utils/request';
import type { IPageResult, ISchedule } from '@/types/api';

export interface IScheduleImportPreview {
  totalCount: number;
  validCount: number;
  errorRows: { row: number; reason: string }[];
  records: ISchedule[];
}

export function apiListSchedules(params: {
  semesterId: number;
  page: number;
  size: number;
  keyword?: string;
}): Promise<IPageResult<ISchedule>> {
  return get<IPageResult<ISchedule>>('/schedules', params);
}

export function apiImportSchedules(formData: FormData): Promise<{ successCount: number; failCount: number }> {
  return post('/schedules/import', formData);
}

export function apiPreviewSchedules(formData: FormData): Promise<IScheduleImportPreview> {
  return upload('/schedules/import/preview', formData);
}

export function apiDownloadScheduleTemplate(): Promise<Blob> {
  return download('/schedules/import/template');
}

export function apiBatchDeleteSchedules(ids: number[]): Promise<void> {
  return post('/schedules/batch-delete', { ids });
}

/** 课表照片识别草稿行（与导入列对齐） */
export interface IScheduleDraftRow {
  courseCode: string;
  courseName: string;
  totalHours: number | null;
  teacherNo: string;
  labCode: string;
  classNames: string;
  studentCount: number | null;
  rawScheduleText: string;
}

/** 课表照片识别：base64 图片 → 结构化草稿行 */
export function apiRecognizeSchedule(imageBase64: string): Promise<{
  success: boolean;
  rows: IScheduleDraftRow[];
  error: string | null;
}> {
  return post('/nlq/recognize-schedule', { image_base64: imageBase64 }, { timeout: 60000 });
}

/** 课表照片识别草稿落库：结构化行 → 复用导入校验与落库 */
export function apiImportScheduleRows(
  semesterId: number,
  rows: IScheduleDraftRow[]
): Promise<{ successCount: number; failedCount: number; errors: { rowNum: number; message: string }[] }> {
  return post('/schedules/import/rows', { rows }, { params: { semesterId } });
}
