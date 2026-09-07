/** 报表模块 /reports（分层可见：教师=汇总+本人明细） */
import { download, get } from '@/utils/request';
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
