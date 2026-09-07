/** 实验课程模块 /courses */
import { get } from '@/utils/request';
import type { ICourse } from '@/types/api';

export function apiListCourses(params?: { semesterId?: number }): Promise<ICourse[]> {
  return get<ICourse[]>('/courses', params);
}

export function apiGetCourse(id: number): Promise<ICourse> {
  return get<ICourse>(`/courses/${id}`);
}
