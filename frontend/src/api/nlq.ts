/** 自然语言查询模块 /nlq */
import { del, get, post } from '@/utils/request';
import type { INlqResult, IPageResult, IQueryHistory } from '@/types/api';

export function apiNlqQuery(question: string): Promise<INlqResult> {
  // NL2SQL 全链路（含大模型调用）耗时较长，单独放宽超时
  return post<INlqResult>('/nlq/query', { question }, { timeout: 60000 });
}

export function apiGetNlqHistory(params: { page: number; size: number }): Promise<IPageResult<IQueryHistory>> {
  return get<IPageResult<IQueryHistory>>('/nlq/history', params);
}

export function apiDeleteNlqHistory(id: number): Promise<void> {
  return del(`/nlq/history/${id}`);
}
