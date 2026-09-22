/** 自然语言查询模块 /nlq */
import { del, get, post } from '@/utils/request';
import type { IScheduleDraftRow } from './schedule';
import type { IAgentResult, IAgentStep, INlqResult, IPageResult, IQueryHistory } from '@/types/api';

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

/** 附图识别的通知转述（标题/时间/地点/事项） */
export interface INlqImageNotice {
  title?: string;
  time?: string;
  place?: string;
  items?: string[];
}

/** 附图识图结果：route=schedule 课表 / document 文档页 / notice 通知 / unknown 未判定 */
export interface INlqImageResult {
  success: boolean;
  route: string;
  /** 图片理解摘要（系统看到的图片内容概述，路由目标在此明示） */
  digest: string;
  /** 低置信：走「没看清」兜底，无回答、无结构化结果 */
  low_confidence: boolean;
  answer: string;
  /** 课表链路识别出的结构化行（字段同课表导入草稿） */
  rows: IScheduleDraftRow[];
  notice: INlqImageNotice;
  /** 文档链路识别出的图内文字 */
  document_text: string;
  /** 文档链路的顾问作答轨迹（knowledge_search 出处） */
  steps: IAgentStep[];
  suggestions: string[];
  error: string | null;
}

/**
 * 智能查询附图：图片（+可选文字）→ 服务端意图路由后分路作答。
 * imageBase64 不含 data: 前缀；forceRoute 用于「一键改道」重跑；
 * 图片真实类型由服务端按魔数判定，前端不传 mime。
 */
export function apiNlqImageQuery(
  imageBase64: string,
  question = '',
  forceRoute?: string,
): Promise<INlqImageResult> {
  // 识图=一次视觉调用（文档链路再叠知识库答问），单独放宽超时
  return post<INlqImageResult>(
    '/nlq/image-query',
    { image_base64: imageBase64, question, force_route: forceRoute },
    { timeout: 90000 },
  );
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
