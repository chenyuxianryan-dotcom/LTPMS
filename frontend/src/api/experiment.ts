/** 实验项目模块 /experiments（教师） */
import { del, download, get, post, put, upload } from '@/utils/request';
import type { IExperiment } from '@/types/api';

export type TExperimentPayload = Omit<IExperiment, 'id' | 'experimentCode' | 'courseId' | 'courseName'>;

/** 编辑提交必须回带 courseId：后端 ExperimentRequest 该字段 @NotNull，且取该字段值做归属落库 */
export type TExperimentUpdate = Partial<TExperimentPayload> & { courseId: number };

export function apiListExperiments(params: { courseId?: number }): Promise<IExperiment[]> {
  return get<IExperiment[]>('/experiments', params);
}

export function apiCreateExperiment(courseId: number, data: TExperimentPayload): Promise<void> {
  return post('/experiments', { ...data, courseId });
}

export function apiUpdateExperiment(id: number, data: TExperimentUpdate): Promise<void> {
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
  return post('/experiments/copy-from-previous', null, { params: { courseId } });
}

/** 「从上学期复制」可行性探针（R3：按钮置灰依据，不落库） */
export interface ICopyAvailability {
  available: boolean;
  previousSemesterName: string | null;
  previousExperimentCount: number;
}

export function apiCopyAvailability(courseId: number): Promise<ICopyAvailability> {
  return get<ICopyAvailability>('/experiments/copy-availability', { courseId });
}

/** 上学期迁移草稿（预览） */
export interface IMigrationDraft {
  courseId: number;
  courseCode: string;
  courseName: string;
  previousCourseId: number;
  previousSemesterName: string;
  previousExperimentCount: number;
  experimentNames: string[];
}

/** 上学期迁移结果 */
export interface IMigrationResult {
  courseCount: number;
  experimentCount: number;
}

/** 上学期智能迁移预览（不落库） */
export function apiMigrationPreview(semesterId?: number): Promise<IMigrationDraft[]> {
  return get<IMigrationDraft[]>('/experiments/migration/preview', semesterId ? { semesterId } : undefined);
}

/** 上学期智能迁移确认（批量复制上学期实验项目） */
export function apiMigrationConfirm(semesterId?: number): Promise<IMigrationResult> {
  return post('/experiments/migration/confirm', null, { params: semesterId ? { semesterId } : undefined });
}
