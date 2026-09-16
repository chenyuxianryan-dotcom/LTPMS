/** 实验课程模块 /courses */
import { get, put } from '@/utils/request';
import type { ICourse, IRevisionView } from '@/types/api';

export function apiListCourses(params?: { semesterId?: number }): Promise<ICourse[]> {
  return get<ICourse[]>('/courses', params);
}

export function apiGetCourse(id: number): Promise<ICourse> {
  return get<ICourse>(`/courses/${id}`);
}

/** 编辑课程聚合行（null 字段不修改） */
export function apiUpdateCourse(
  id: number,
  body: { courseName?: string; major?: string; totalHours?: number },
): Promise<void> {
  return put<void>(`/courses/${id}`, body);
}

/** 课程修订明细 */
export function apiListCourseRevisions(id: number): Promise<IRevisionView[]> {
  return get<IRevisionView[]>(`/courses/${id}/revisions`);
}

/** 批量查询课程修订条数（结果键为字符串 ID） */
export function apiCourseRevisionFlags(ids: number[]): Promise<Record<string, number>> {
  return get<Record<string, number>>('/courses/revisions/flags', { ids: ids.join(',') });
}
