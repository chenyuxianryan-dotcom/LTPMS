/** 学期模块 /semesters（管理员） */
import { get, post, put } from '@/utils/request';
import type { ISemester } from '@/types/api';

export function apiListSemesters(params?: { status?: string }): Promise<ISemester[]> {
  return get<ISemester[]>('/semesters', params);
}

export function apiGetCurrentSemester(): Promise<ISemester> {
  return get<ISemester>('/semesters/current');
}

export function apiCreateSemester(data: Pick<ISemester, 'academicYear' | 'semesterNum' | 'startDate' | 'endDate'>): Promise<void> {
  return post('/semesters', data);
}

export function apiUpdateSemester(id: number, data: Pick<ISemester, 'startDate' | 'endDate'>): Promise<void> {
  return put(`/semesters/${id}`, data);
}
