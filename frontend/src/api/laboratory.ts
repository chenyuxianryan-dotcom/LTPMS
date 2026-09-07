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

export function apiImportLaboratories(formData: FormData): Promise<{ successCount: number; failCount: number }> {
  return post('/laboratories/import', formData);
}

export function apiDownloadLabTemplate(): Promise<Blob> {
  return download('/laboratories/import/template');
}
