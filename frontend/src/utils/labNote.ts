/**
 * 课表行的「开放注记」计算：按实验室聚合开放计划与总览矛盾数，
 * 供全校总表与教师课表页共用（矛盾 > 未开放 > 正常开放段数）。
 */
import { WEEKDAY_LABELS, WEEK_TYPE_MAP } from '@/constants';
import type { ILabOpening, IOpenOverviewItem } from '@/types/api';

export interface ILabNote {
  type: 'none' | 'open' | 'conflict' | 'unplanned';
  text: string;
  tip: string;
  tagType: 'info' | 'danger';
  effect: 'light' | 'dark';
}

export interface ILabNoteIndex {
  openingsByLab: Map<string, ILabOpening[]>;
  overviewMap: Map<string, IOpenOverviewItem>;
}

export function buildLabNoteIndex(
  openings: ILabOpening[],
  overviewLabs: IOpenOverviewItem[],
): ILabNoteIndex {
  const openingsByLab = new Map<string, ILabOpening[]>();
  for (const slot of openings) {
    const list = openingsByLab.get(slot.labCode) ?? [];
    list.push(slot);
    openingsByLab.set(slot.labCode, list);
  }
  return { openingsByLab, overviewMap: new Map(overviewLabs.map((item) => [item.labCode, item])) };
}

export function labNoteOf(index: ILabNoteIndex, labCode: string): ILabNote {
  const conflictCount = index.overviewMap.get(labCode)?.conflictCount ?? 0;
  if (conflictCount > 0) {
    return {
      type: 'conflict',
      text: `矛盾 ${conflictCount} 处`,
      tip: '该校表与实验室开放时段存在时间矛盾（详见数据智能页）',
      tagType: 'danger',
      effect: 'dark',
    };
  }
  const enabled = (index.openingsByLab.get(labCode) ?? []).filter((slot) => slot.enabled === 1);
  if (!enabled.length) {
    return {
      type: 'unplanned',
      text: '未开放',
      tip: '本学期该实验室未配置开放计划（有课但实验室未开放）',
      tagType: 'danger',
      effect: 'light',
    };
  }
  return {
    type: 'open',
    text: `${enabled.length} 段开放`,
    tip: enabled
      .map((slot) => `周${WEEKDAY_LABELS[slot.dayOfWeek - 1]} ${slot.startSection}-${slot.endSection}节 ${slot.weekRange}周(${WEEK_TYPE_MAP[slot.weekType] ?? slot.weekType})`)
      .join('；'),
    tagType: 'info',
    effect: 'light',
  };
}
