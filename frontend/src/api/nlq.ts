/** 自然语言查询模块 /nlq */
import { del, get, post } from '@/utils/request';
import type { IAgentResult, INlqResult, IPageResult, IQueryHistory } from '@/types/api';

export function apiNlqQuery(question: string): Promise<INlqResult> {
  // NL2SQL 全链路（含大模型调用）耗时较长，单独放宽超时
  return post<INlqResult>('/nlq/query', { question }, { timeout: 60000 });
}

/** Agent 多轮对话（阿实），history 为可选的多轮上下文 */
export function apiNlqAgent(
  question: string,
  history?: { role: string; content: string }[],
): Promise<IAgentResult> {
  return post<IAgentResult>('/nlq/agent', { question, history }, { timeout: 60000 });
}

export function apiGetNlqHistory(params: { page: number; size: number }): Promise<IPageResult<IQueryHistory>> {
  return get<IPageResult<IQueryHistory>>('/nlq/history', params);
}

export function apiDeleteNlqHistory(id: number): Promise<void> {
  return del(`/nlq/history/${id}`);
}
