/** 课表模块 /schedules */
import { download, get, post, put, upload } from '@/utils/request';
import type {
  IImportPreview,
  IMyScheduleItem,
  IPageResult,
  IRevisionView,
  ISchedule,
  IScheduleImportResult,
} from '@/types/api';

export function apiListSchedules(params: {
  semesterId: number;
  page: number;
  size: number;
  keyword?: string;
}): Promise<IPageResult<ISchedule>> {
  return get<IPageResult<ISchedule>>('/schedules', params);
}

/** 教师我的课表（周课表/卡片墙数据源）；管理员需指定 teacherId */
export function apiMySchedules(params: {
  semesterId: number;
  week?: number;
  teacherId?: number;
}): Promise<IMyScheduleItem[]> {
  return get<IMyScheduleItem[]>('/schedules/mine', params);
}

/** 编辑课表行（null 字段不修改） */
export function apiUpdateSchedule(
  id: number,
  body: { labCode?: string; classNames?: string; studentCount?: number; rawScheduleText?: string },
): Promise<void> {
  return put<void>(`/schedules/${id}`, body);
}

/** 课表行修订明细 */
export function apiListScheduleRevisions(id: number): Promise<IRevisionView[]> {
  return get<IRevisionView[]>(`/schedules/${id}/revisions`);
}

/** 批量查询课表行修订条数（结果键为字符串 ID） */
export function apiScheduleRevisionFlags(ids: number[]): Promise<Record<string, number>> {
  return get<Record<string, number>>('/schedules/revisions/flags', { ids: ids.join(',') });
}

export function apiImportSchedules(formData: FormData): Promise<IScheduleImportResult> {
  return upload<IScheduleImportResult>('/schedules/import', formData);
}

export function apiPreviewSchedules(formData: FormData): Promise<IImportPreview> {
  return upload<IImportPreview>('/schedules/import/preview', formData);
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

/** 课表照片识别草稿解析预览（不落库） */
export function apiPreviewScheduleRows(semesterId: number, rows: IScheduleDraftRow[]): Promise<IImportPreview> {
  return post<IImportPreview>('/schedules/import/rows/preview', { rows }, { params: { semesterId } });
}

/** 课表照片识别草稿落库：结构化行 → 复用导入校验与落库 */
export function apiImportScheduleRows(
  semesterId: number,
  rows: IScheduleDraftRow[],
  confirmOverwrite = false,
): Promise<IScheduleImportResult> {
  return post<IScheduleImportResult>('/schedules/import/rows', { rows }, {
    params: { semesterId, confirmOverwrite },
  });
}
