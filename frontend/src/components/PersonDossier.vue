<template>
  <div class="dossier">
    <!-- 唯一定位：单行横幅（避免整张候选表占屏） -->
    <div v-if="banner" class="dossier__banner">
      <span class="dossier__lamp" aria-hidden="true" />
      <span class="dossier__banner-text">{{ banner }}</span>
    </div>

    <div class="dossier__grid">
      <section
        v-for="card in cards"
        :key="card.key"
        class="dossier__card"
        :class="`dossier__card--${card.variant}`"
      >
        <header class="dossier__card-head">
          <span class="dossier__card-title">{{ card.title }}</span>
          <span v-if="card.badge" class="dossier__card-badge">{{ card.badge }}</span>
        </header>

        <p v-if="card.variant === 'error'" class="dossier__note dossier__note--error">{{ card.error }}</p>
        <p v-else-if="card.variant === 'missing'" class="dossier__note">{{ card.note }}</p>
        <div v-else class="dossier__blocks">
          <div v-for="block in card.blocks" :key="block.title" class="dossier__block">
            <div class="dossier__block-title">{{ block.title }}</div>
            <p v-if="block.empty" class="dossier__note">{{ block.note ?? '暂无记录' }}</p>
            <template v-else>
              <div class="dossier__table-wrap">
                <table class="dossier__table">
                  <thead>
                    <tr>
                      <th v-for="col in block.columns" :key="col">{{ col }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                      <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell ?? '—' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p v-if="block.note" class="dossier__note dossier__note--foot">{{ block.note }}</p>
            </template>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { IAgentStep, IDossierBlock, IDossierToolResult } from '@/types/api';

/**
 * 人员档案分区卡：把档案工具的 display.blocks 原始行直接上屏（决策 D7）。
 * 数据零转写：列名与单元格都来自后端 SQL 结果，前端只负责分区与空态。
 * 区块缺失不删除：exact 档案缺维标「未获取」，挂名档案标「不适用（无系统账号）」。
 */
const props = defineProps<{ steps: IAgentStep[] }>();

/** 八维顺序与标题（与后端 PERSON_DOSSIER_DIMENSIONS 一致；标题仅在区块缺失时兜底） */
const DIMENSION_ORDER = [
  'person_identity',
  'person_courses',
  'person_schedules',
  'person_class_times',
  'person_experiments',
  'person_managed_labs',
  'person_footprints',
  'person_workload',
] as const;

const TOOL_TITLES: Record<string, string> = {
  resolve_person: '人员解析',
  person_identity: '身份与账号',
  person_courses: '任课课程',
  person_schedules: '排课记录',
  person_class_times: '课节时间明细',
  person_experiments: '实验项目',
  person_managed_labs: '管理的实验室',
  person_footprints: '行为足迹',
  person_workload: '派生洞察',
};

const STATUS_LABELS: Record<string, string> = {
  ambiguous: '多人同名 · 待指认',
  manager_only: '仅挂名 · 无系统账号',
  not_found: '未找到',
  denied: '仅限本人',
};

interface IDossierCard {
  key: string;
  title: string;
  variant: 'normal' | 'notice' | 'error' | 'missing';
  badge?: string;
  note?: string;
  error?: string;
  blocks: IDossierBlock[];
}

const results = computed<IDossierToolResult[]>(() =>
  props.steps
    .map((step) => step.result as IDossierToolResult | undefined)
    .filter((r): r is IDossierToolResult => Boolean(r) && Array.isArray(r?.display?.blocks)),
);

const byDimension = computed(() => {
  const map = new Map<string, IDossierToolResult>();
  for (const r of results.value) {
    if (r.dimension) map.set(r.dimension, r);
  }
  return map;
});

const resolveResult = computed(() => byDimension.value.get('resolve_person'));
/** 无解析步骤（「我的档案」锁定入口）等同于 exact */
const resolveState = computed(() => resolveResult.value?.status ?? 'exact');

const banner = computed(() => {
  const r = resolveResult.value;
  if (!r || r.status !== 'exact') return '';
  const block = r.display?.blocks?.[0];
  const row = block?.rows?.[0];
  if (!block || !row) return '';
  const name = cell(row, block.columns, '姓名');
  const username = cell(row, block.columns, '工号');
  const role = cell(row, block.columns, '角色');
  const department = cell(row, block.columns, '院系');
  const phone = cell(row, block.columns, '手机号');
  const head = username ? `${name}（${username}）` : name;
  return ['已唯一定位', head, role, department, phone && `手机号 ${phone}`].filter(Boolean).join(' · ');
});

const cards = computed<IDossierCard[]>(() => {
  const list: IDossierCard[] = [];

  const r = resolveResult.value;
  if (r && r.status !== 'exact') {
    list.push({
      key: 'resolve',
      title: r.title ?? TOOL_TITLES.resolve_person,
      variant: 'notice',
      badge: STATUS_LABELS[r.status ?? ''] ?? r.status,
      blocks: r.display?.blocks ?? [],
    });
  }

  for (const dimension of DIMENSION_ORDER) {
    const dimResult = byDimension.value.get(dimension);
    if (!dimResult) continue;
    list.push({
      key: dimension,
      title: dimResult.title ?? TOOL_TITLES[dimension],
      variant: 'normal',
      badge: typeof dimResult.count === 'number' ? String(dimResult.count) : undefined,
      blocks: dimResult.display?.blocks ?? [],
    });
  }

  // 缺维不删区：exact 标「未获取」（完成度断言二次仍缺），挂名档案标「不适用」
  if (resolveState.value === 'exact' || resolveState.value === 'manager_only') {
    for (const dimension of DIMENSION_ORDER) {
      if (byDimension.value.has(dimension)) continue;
      list.push({
        key: `missing-${dimension}`,
        title: TOOL_TITLES[dimension],
        variant: 'missing',
        badge: '缺',
        note: resolveState.value === 'manager_only'
          ? '不适用（该人员无系统账号）'
          : '未获取（本轮检索未返回该维度）',
        blocks: [],
      });
    }
  }

  for (const step of props.steps) {
    const failed = step.result as IDossierToolResult | undefined;
    if (!failed || failed.ok !== false || !failed.error) continue;
    list.push({
      key: `error-${step.tool}`,
      title: TOOL_TITLES[step.tool] ?? step.tool,
      variant: 'error',
      error: failed.error,
      blocks: [],
    });
  }

  return list;
});

function cell(row: unknown[], columns: string[], name: string): string {
  const index = columns.indexOf(name);
  const value = index >= 0 ? row[index] : null;
  return value == null ? '' : String(value);
}
</script>

<style scoped lang="scss">
/**
 * 磷光绿档案终端：与 NlqPage（ASY-01 检索终端）、NlqWidget 同源的终端语言，
 * 色板沿用页面局部先例（tokens.css 未收录），排版尺寸引用 design tokens。
 */
$phosphor-bg: #0c1f16;
$phosphor-border: #1e3a2b;
$phosphor-line: #2c4a38;
$phosphor-text: #6ee7a0;
$phosphor-dim: #3f8f66;
$phosphor-data: #d1fae5;

.dossier {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  background: $phosphor-bg;
  border: 1px solid $phosphor-border;
  border-radius: var(--radius-xl);
}

.dossier__banner {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  font-family: var(--font-mono);
  font-size: 12px;
  color: $phosphor-text;
  border-bottom: 1px dashed $phosphor-border;
}

.dossier__lamp {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  background: $phosphor-text;
  border-radius: var(--radius-full);
  box-shadow: 0 0 8px rgba(110, 231, 160, 0.8);
}

.dossier__banner-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dossier__grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.dossier__card {
  padding: var(--space-3) var(--space-4);
  background: rgba(110, 231, 160, 0.04);
  border: 1px solid $phosphor-border;
  border-radius: var(--radius-lg);

  &--notice {
    background: rgba(245, 158, 11, 0.08);
    border-color: rgba(245, 158, 11, 0.35);
  }

  &--error {
    background: rgba(239, 68, 68, 0.08);
    border-color: rgba(239, 68, 68, 0.35);
  }

  /* 缺维区块：标灰而非删除 */
  &--missing {
    background: transparent;
    border-style: dashed;
    opacity: 0.55;
  }
}

.dossier__card-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.dossier__card-title {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: $phosphor-text;
}

.dossier__card-badge {
  padding: 1px 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: $phosphor-dim;
  background: rgba(110, 231, 160, 0.1);
  border: 1px solid $phosphor-line;
  border-radius: var(--radius-full);
}

.dossier__blocks {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.dossier__block-title {
  margin-bottom: var(--space-2);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  color: $phosphor-dim;
}

.dossier__table-wrap {
  max-height: 280px;
  overflow: auto;
  border: 1px solid $phosphor-border;
  border-radius: var(--radius-lg);

  &::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: $phosphor-line;
    border-radius: 3px;
  }
}

.dossier__table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: nowrap;

  th,
  td {
    padding: 6px 10px;
    text-align: left;
    border-bottom: 1px solid rgba(44, 74, 56, 0.55);
    border-right: 1px solid rgba(44, 74, 56, 0.35);
  }

  th {
    position: sticky;
    top: 0;
    color: $phosphor-dim;
    font-weight: 500;
    letter-spacing: 0.04em;
    background: #0f2a1d;
  }

  td {
    color: $phosphor-data;
  }

  tbody tr:last-child td {
    border-bottom: none;
  }

  tbody tr:hover td {
    background: rgba(110, 231, 160, 0.06);
  }
}

.dossier__note {
  margin: 0;
  font-size: 12px;
  color: $phosphor-dim;
  white-space: pre-wrap;

  &--error {
    color: #fca5a5;
  }

  &--foot {
    margin-top: var(--space-2);
    font-size: 11px;
    opacity: 0.85;
  }
}
</style>
