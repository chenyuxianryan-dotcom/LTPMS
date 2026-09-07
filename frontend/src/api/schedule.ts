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
