/** 报表模块 /reports（分层可见：教师=汇总+本人明细） */
import { download, downloadPost, get } from '@/utils/request';
import type { IReportData } from '@/types/api';

export interface IReportParams {
  academicYear: string;
  /** 教师侧服务端强制 true；管理员传 true 表示只看汇总 */
  aggregate_only?: boolean;
}

export function apiGetCourseProjectStats(params: IReportParams): Promise<IReportData> {
  return get<IReportData>('/reports/course-project-stats', params);
}

export function apiGetPersonHours(params: IReportParams): Promise<IReportData> {
  return get<IReportData>('/reports/person-hours', params);
}

export function apiExportCourseProjectStats(params: IReportParams & { format: 'excel' | 'pdf' }): Promise<Blob> {
  return download('/reports/course-project-stats/export', params);
}

export function apiExportPersonHours(params: IReportParams & { format: 'excel' | 'pdf' }): Promise<Blob> {
  return download('/reports/person-hours/export', params);
}

/** 叙事报告导出载荷：页面所见内容原样提交，后端只排版不重新生成 */
export interface INarrativeExportPayload {
  title: string;
  semester?: string;
  text: string;
  numbers: { label: string; value: string }[];
}

export function apiExportNarrative(
  params: { format: 'excel' | 'pdf' },
  payload: INarrativeExportPayload,
): Promise<Blob> {
  return downloadPost('/reports/narrative/export', params, payload);
}
