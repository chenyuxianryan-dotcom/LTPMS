/** 实验室模块 /laboratories（管理员） */
import { del, download, get, post, put } from '@/utils/request';
import type { ILaboratory, IPageResult } from '@/types/api';

export function apiListLaboratories(params: { page: number; size: number; keyword?: string }): Promise<IPageResult<ILaboratory>> {
  return get<IPageResult<ILaboratory>>('/laboratories', params);
}

export function apiCreateLaboratory(data: Omit<ILaboratory, 'id' | 'status'>): Promise<void> {
  return post('/laboratories', data);
}

export function apiUpdateLaboratory(id: number, data: Partial<ILaboratory>): Promise<void> {
  return put(`/laboratories/${id}`, data);
}

export function apiDeleteLaboratory(id: number): Promise<void> {
  return del(`/laboratories/${id}`);
}

export interface ILaboratoryBatchDeleteError {
  id: number;
  message: string;
}

export interface ILaboratoryBatchDeleteResult {
  successCount: number;
  failedCount: number;
  errors: ILaboratoryBatchDeleteError[];
}

/** 批量删除实验室，逐条校验关联课表，关联存在的会被跳过并记录明细 */
export function apiDeleteLaboratories(ids: number[]): Promise<ILaboratoryBatchDeleteResult> {
  return post<ILaboratoryBatchDeleteResult>('/laboratories/batch-delete', ids);
}

export function apiImportLaboratories(formData: FormData): Promise<{ successCount: number; failCount: number }> {
  return post('/laboratories/import', formData);
}

export function apiDownloadLabTemplate(): Promise<Blob> {
  return download('/laboratories/import/template');
}
