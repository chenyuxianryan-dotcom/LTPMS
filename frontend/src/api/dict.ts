/** 字典模块 /dicts（GET 对教师开放只读） */
import { del, get, post, put } from '@/utils/request';
import type { IDictItem } from '@/types/api';

export function apiListDictsByType(dictType: string): Promise<IDictItem[]> {
  return get<IDictItem[]>(`/dicts/type/${dictType}`);
}

export function apiCreateDict(data: Omit<IDictItem, 'id' | 'status'>): Promise<void> {
  return post('/dicts', data);
}

export function apiUpdateDict(id: number, data: Partial<IDictItem>): Promise<void> {
  return put(`/dicts/${id}`, data);
}

export function apiDeleteDict(id: number): Promise<void> {
  return del(`/dicts/${id}`);
}
