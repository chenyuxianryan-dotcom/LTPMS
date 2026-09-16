/** 教师账号模块 /teachers（管理员） */
import { download, get, patch, post, put, upload } from '@/utils/request';
import type { IPageResult, ITeacher } from '@/types/api';

export function apiListTeachers(params: { page: number; size: number; keyword?: string }): Promise<IPageResult<ITeacher>> {
  return get<IPageResult<ITeacher>>('/teachers', params);
}

/** 新增单个教师，初始密码为默认密码 Ltpms@{工号} */
export function apiCreateTeacher(data: { username: string; realName: string; department?: string; phone?: string }): Promise<void> {
  return post('/teachers', data);
}

/** 编辑教师基础信息（工号不可修改） */
export function apiUpdateTeacher(id: number, data: { realName: string; department?: string; phone?: string }): Promise<void> {
  return put(`/teachers/${id}`, data);
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

export interface ITeacherBatchDeleteError {
  id: number;
  message: string;
}

export interface ITeacherBatchDeleteResult {
  successCount: number;
  failedCount: number;
  errors: ITeacherBatchDeleteError[];
}

/** 批量删除教师，逐条校验教师身份与关联课表/课程，关联存在的会被跳过并记录明细 */
export function apiDeleteTeachers(ids: number[]): Promise<ITeacherBatchDeleteResult> {
  return post<ITeacherBatchDeleteResult>('/teachers/batch-delete', ids);
}

export function apiDownloadTeacherTemplate(): Promise<Blob> {
  return download('/teachers/import/template');
}
