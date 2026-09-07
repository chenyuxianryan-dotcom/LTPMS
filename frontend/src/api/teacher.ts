/** 教师账号模块 /teachers（管理员） */
import { download, get, patch, post, upload } from '@/utils/request';
import type { IPageResult, ITeacher } from '@/types/api';

export function apiListTeachers(params: { page: number; size: number; keyword?: string }): Promise<IPageResult<ITeacher>> {
  return get<IPageResult<ITeacher>>('/teachers', params);
}

export function apiImportTeachers(formData: FormData): Promise<{ successCount: number; failCount: number }> {
  return post('/teachers/import', formData);
}

export function apiResetTeacherPassword(id: number): Promise<{ newPassword: string }> {
  return post(`/teachers/${id}/reset-password`);
}

export function apiUpdateTeacherStatus(id: number, status: 0 | 1): Promise<void> {
  return patch(`/teachers/${id}/status`, { status });
}

export function apiDownloadTeacherTemplate(): Promise<Blob> {
  return download('/teachers/import/template');
}
