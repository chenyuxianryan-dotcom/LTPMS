/** 实验室开放管理模块 /lab-openings、/lab-open-exceptions */
import { del, get, post, put } from '@/utils/request';
import type {
  ILabOpenException,
  ILabOpenGrid,
  ILabOpening,
  ILabOpeningOverview,
  ILabOpenToday,
  TWeekType,
} from '@/types/api';

export function apiListLabOpenings(params: { semesterId: number; labCode?: string }): Promise<ILabOpening[]> {
  return get<ILabOpening[]>('/lab-openings', params);
}

export function apiCreateLabOpening(data: Omit<ILabOpening, 'id' | 'createdAt' | 'updatedAt'>): Promise<void> {
  return post('/lab-openings', data);
}

export function apiUpdateLabOpening(id: number, data: Partial<ILabOpening>): Promise<void> {
  return put(`/lab-openings/${id}`, data);
}

export function apiDeleteLabOpening(id: number): Promise<void> {
  return del(`/lab-openings/${id}`);
}

export interface ILabOpenSlotPayload {
  dayOfWeek: number;
  startSection: number;
  endSection: number;
  weekRange: string;
  weekType: TWeekType;
}

/** 批量设置：覆盖选中实验室本学期全部开放时段 */
export function apiBatchLabOpenings(data: {
  semesterId: number;
  labCodes: string[];
  slots: ILabOpenSlotPayload[];
  audience?: string;
  remark?: string;
}): Promise<number> {
  return post<number>('/lab-openings/batch', data);
}

/** 从其他学期复制开放计划（覆盖目标学期对应实验室） */
export function apiCopyLabOpenings(data: { fromSemesterId: number; toSemesterId: number; labCodes?: string[] }): Promise<number> {
  return post<number>('/lab-openings/copy', data);
}

export function apiGetLabOpeningOverview(semesterId: number): Promise<ILabOpeningOverview> {
  return get<ILabOpeningOverview>('/lab-openings/overview', { semesterId });
}

export function apiGetLabOpenGrid(params: { semesterId: number; labCode: string; week?: number }): Promise<ILabOpenGrid> {
  return get<ILabOpenGrid>('/lab-openings/grid', params);
}

export function apiGetLabOpenToday(semesterId?: number): Promise<ILabOpenToday> {
  return get<ILabOpenToday>('/lab-openings/today', semesterId ? { semesterId } : undefined);
}

export function apiListLabOpenExceptions(params: { semesterId: number; labCode?: string }): Promise<ILabOpenException[]> {
  return get<ILabOpenException[]>('/lab-open-exceptions', params);
}

export function apiCreateLabOpenException(data: Omit<ILabOpenException, 'id' | 'createdAt'>): Promise<void> {
  return post('/lab-open-exceptions', data);
}

export function apiDeleteLabOpenException(id: number): Promise<void> {
  return del(`/lab-open-exceptions/${id}`);
}
