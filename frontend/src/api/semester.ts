/** 学期模块 /semesters（管理员） */
import { del, get, post, put } from '@/utils/request';
import type { IHoliday, ISemester, ISemesterCalendar } from '@/types/api';

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

export function apiGetSemesterCalendar(id: number): Promise<ISemesterCalendar> {
  return get<ISemesterCalendar>(`/semesters/${id}/calendar`);
}

/** 设定第 1 周起始日（周历基准），影响周次口径 */
export function apiSetSemesterCalendar(id: number, week1StartDate: string): Promise<void> {
  return put(`/semesters/${id}/calendar`, { week1StartDate });
}

export function apiListHolidays(semesterId: number): Promise<IHoliday[]> {
  return get<IHoliday[]>('/holidays', { semesterId });
}

/** 按日期区间批量标记节假日，返回新增天数 */
export function apiCreateHoliday(data: { startDate: string; endDate: string; name: string }): Promise<number> {
  return post<number>('/holidays', data);
}

export function apiDeleteHoliday(id: number): Promise<void> {
  return del(`/holidays/${id}`);
}
