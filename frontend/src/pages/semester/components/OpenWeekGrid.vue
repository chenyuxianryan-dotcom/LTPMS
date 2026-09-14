<template>
  <div v-loading="loading" class="open-week-grid">
    <template v-if="grid">
      <div class="open-week-grid__bar">
        <span class="open-week-grid__week">
          第 {{ grid.week }} 周
          <span class="open-week-grid__range">（{{ short(grid.weekStartDate) }} ~ {{ short(grid.weekEndDate) }}）</span>
        </span>
        <span class="open-week-grid__legend">
          <i class="open-week-grid__dot open-week-grid__dot--occupied" />有课
          <i class="open-week-grid__dot open-week-grid__dot--open" />开放
          <i class="open-week-grid__dot open-week-grid__dot--not-open" />未开放
          <i class="open-week-grid__dot open-week-grid__dot--closed" />闭馆
        </span>
      </div>

      <table class="open-week-grid__table">
        <thead>
          <tr>
            <th class="open-week-grid__corner">节次</th>
            <th v-for="(label, index) in WEEKDAY_LABELS" :key="label">
              周{{ label }}
              <span class="open-week-grid__date">{{ short(dateAt(grid.weekStartDate, index)) }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="section in SECTION_OPTIONS" :key="section">
            <th class="open-week-grid__corner">{{ section }}</th>
            <td
              v-for="(label, index) in WEEKDAY_LABELS"
              :key="label"
              class="open-week-grid__cell"
              :class="cellClass(index + 1, section)"
              :title="cellTitle(index + 1, section)"
            >
              {{ cellText(index + 1, section) }}
            </td>
          </tr>
        </tbody>
      </table>

      <div class="open-week-grid__closed">
        <span class="open-week-grid__closed-label">闭馆日（全学期）：</span>
        <template v-if="grid.closedDates.length">
          <el-tag
            v-for="item in grid.closedDates"
            :key="item.date"
            :type="item.type === 'HOLIDAY' ? 'danger' : 'warning'"
            size="small"
            effect="light"
          >
            {{ short(item.date) }} {{ item.name }}（{{ item.type === 'HOLIDAY' ? '节假日' : '例外' }}）
          </el-tag>
        </template>
        <span v-else class="open-week-grid__closed-empty">无</span>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="暂无网格数据" :image-size="72" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { SECTION_OPTIONS, WEEKDAY_LABELS } from '@/constants';
import type { ILabOpenGrid, TOpenCellState } from '@/types/api';

const props = defineProps<{
  grid: ILabOpenGrid | null;
  loading?: boolean;
}>();

const CELL_TEXT: Record<TOpenCellState, string> = {
  OCCUPIED: '课',
  OPEN: '开',
  NOT_OPEN: '',
  CLOSED: '闭',
};

const STATE_LABEL: Record<TOpenCellState, string> = {
  OCCUPIED: '有课（占用）',
  OPEN: '开放',
  NOT_OPEN: '未开放',
  CLOSED: '闭馆',
};

const cellMap = computed<Map<string, TOpenCellState>>(() => {
  const map = new Map<string, TOpenCellState>();
  for (const cell of props.grid?.cells ?? []) {
    map.set(`${cell.dayOfWeek}-${cell.section}`, cell.state);
  }
  return map;
});

function stateOf(day: number, section: number): TOpenCellState {
  return cellMap.value.get(`${day}-${section}`) ?? 'NOT_OPEN';
}

function cellClass(day: number, section: number): string {
  return `open-week-grid__cell--${stateOf(day, section).toLowerCase()}`;
}

function cellText(day: number, section: number): string {
  return CELL_TEXT[stateOf(day, section)];
}

function cellTitle(day: number, section: number): string {
  return `周${WEEKDAY_LABELS[day - 1]} 第 ${section} 节：${STATE_LABEL[stateOf(day, section)]}`;
}

function short(date: string): string {
  return date ? date.slice(5) : '';
}

function dateAt(base: string, offset: number): string {
  const [y, m, d] = base.split('-').map(Number);
  const dt = new Date(y, m - 1, d + offset);
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
}
</script>

<style scoped lang="scss">
.open-week-grid {
  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-3);
  }

  &__week {
    font-weight: 600;
  }

  &__range {
    color: var(--el-text-color-secondary);
    font-weight: 400;
  }

  &__legend {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__dot {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 2px;
    margin-left: var(--space-2);
    vertical-align: middle;

    &--occupied {
      background: var(--el-color-danger-light-5);
      border: 1px solid var(--el-color-danger);
    }

    &--open {
      background: var(--el-color-success-light-7);
      border: 1px solid var(--el-color-success);
    }

    &--not-open {
      background: var(--el-fill-color-light);
      border: 1px solid var(--el-border-color);
    }

    &--closed {
      background: var(--el-fill-color-darker);
      border: 1px solid var(--el-border-color-darker);
    }
  }

  &__table {
    width: 100%;
    border-collapse: collapse;
    table-layout: fixed;

    th,
    td {
      border: 1px solid var(--el-border-color-lighter);
      text-align: center;
      font-size: 12px;
      height: 30px;
    }

    thead th {
      background: var(--el-fill-color-light);
      font-weight: 600;
    }
  }

  &__corner {
    width: 46px;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);
    font-weight: 400;
  }

  &__date {
    display: block;
    color: var(--el-text-color-secondary);
    font-weight: 400;
    transform: scale(0.92);
  }

  &__cell {
    color: var(--el-text-color-primary);

    &--occupied {
      background: var(--el-color-danger-light-8);
      color: var(--el-color-danger);
      font-weight: 600;
    }

    &--open {
      background: var(--el-color-success-light-9);
      color: var(--el-color-success);
    }

    &--not-open {
      background: var(--el-fill-color-lighter);
    }

    &--closed {
      background: var(--el-fill-color-dark);
      color: var(--el-text-color-secondary);
      text-decoration: line-through;
    }
  }

  &__closed {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    flex-wrap: wrap;
    margin-top: var(--space-3);
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__closed-label {
    font-weight: 600;
  }

  &__closed-empty {
    color: var(--el-text-color-placeholder);
  }
}
</style>
