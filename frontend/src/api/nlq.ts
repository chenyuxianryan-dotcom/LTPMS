/** 自然语言查询模块 /nlq */
import { del, get, post } from '@/utils/request';
import type { IAgentResult, INlqResult, IPageResult, IQueryHistory } from '@/types/api';

export function apiNlqQuery(question: string): Promise<INlqResult> {
  // NL2SQL 全链路（含大模型调用）耗时较长，单独放宽超时
  return post<INlqResult>('/nlq/query', { question }, { timeout: 60000 });
}

/** Agent 多轮对话（阿实），history 为可选的多轮上下文；selfDossier=true 走「我的档案」锁定入口 */
export function apiNlqAgent(
  question: string,
  history?: { role: string; content: string }[],
  options?: { selfDossier?: boolean },
): Promise<IAgentResult> {
  const payload: Record<string, unknown> = { question, history };
  if (options?.selfDossier) {
    payload.self_dossier = true;
  }
  return post<IAgentResult>('/nlq/agent', payload, { timeout: 60000 });
}

export function apiGetNlqHistory(params: { page: number; size: number }): Promise<IPageResult<IQueryHistory>> {
  return get<IPageResult<IQueryHistory>>('/nlq/history', params);
}

export function apiDeleteNlqHistory(id: number): Promise<void> {
  return del(`/nlq/history/${id}`);
}

/** 实验台数字档案结果：text=AI 档案文本；stats=真实统计数据（生成失败时降级展示） */
export interface INlqArchiveResult {
  success: boolean;
  text: string;
  cached: boolean;
  stats: Record<string, unknown> | null;
  error: string | null;
}

/** 叙事报告/教学总结结果：numbers=真实聚合数字；scope=personal|school */
export interface INlqNarrativeResult {
  success: boolean;
  text: string;
  numbers: Record<string, unknown>;
  scope: string;
  error: string | null;
}

/** 实验台数字档案：聚合真实统计 → AI 生成档案（缓存 TTL 24h） */
export function apiNlqArchive(
  entityType: 'laboratory' | 'experiment',
  entityId: number,
): Promise<INlqArchiveResult> {
  // 档案含大模型生成，单独放宽超时
  return post<INlqArchiveResult>('/nlq/archive', { entity_type: entityType, entity_id: entityId }, { timeout: 60000 });
}

/** 叙事报告/教学总结：数字由后端真实聚合，LLM 只做语言包装（教师强制个人口径） */
export function apiNlqNarrative(kind: 'annual_report' | 'teaching_summary'): Promise<INlqNarrativeResult> {
  return post<INlqNarrativeResult>('/nlq/narrative', { kind }, { timeout: 60000 });
}
