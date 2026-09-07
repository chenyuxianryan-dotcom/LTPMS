/** 实验项目模块 /experiments（教师） */
import { del, download, get, post, put, upload } from '@/utils/request';
import type { IExperiment } from '@/types/api';

export type TExperimentPayload = Omit<IExperiment, 'id' | 'experimentCode' | 'courseId' | 'courseName'>;

export function apiListExperiments(params: { courseId?: number }): Promise<IExperiment[]> {
  return get<IExperiment[]>('/experiments', params);
}

export function apiCreateExperiment(courseId: number, data: TExperimentPayload): Promise<void> {
  return post('/experiments', { ...data, courseId });
}

export function apiUpdateExperiment(id: number, data: Partial<TExperimentPayload>): Promise<void> {
  return put(`/experiments/${id}`, data);
}

export function apiDeleteExperiment(id: number): Promise<void> {
  return del(`/experiments/${id}`);
}

export function apiImportExperiments(formData: FormData): Promise<{ successCount: number; failCount: number }> {
  return post('/experiments/import', formData);
}

export function apiDownloadExperimentTemplate(): Promise<Blob> {
  return download('/experiments/import/template');
}

export function apiCopyFromPrevious(courseId: number): Promise<{ copiedCount: number }> {
  return post('/experiments/copy-from-previous', { courseId });
}
