<template>
  <div v-if="tasks.length && students.length" class="matrix" :style="gridStyle" role="table" aria-label="学生×任务进度矩阵">
    <!-- 表头行：左上角 + 各任务列 -->
    <div class="matrix__corner" role="columnheader">学生 \ 任务</div>
    <div
      v-for="t in tasks"
      :key="`h-${t.taskId}`"
      class="matrix__col-head"
      :class="{ 'matrix__col-head--overdue': t.overdue }"
      :title="`${t.title}（${statusLabel(t.status)}${t.overdue ? '·逾期' : ''}）`"
      role="columnheader"
    >
      <span class="matrix__col-title">{{ t.title }}</span>
      <span class="matrix__col-meta">{{ fmtDateTime(t.deadline) }}</span>
    </div>

    <!-- 学生数据行 -->
    <template v-for="s in students" :key="`r-${s.studentId}`">
      <div class="matrix__row-head" role="rowheader" :title="s.name">
        {{ s.name }}
        <span class="matrix__row-rate">{{ Math.round(s.rate) }}%</span>
      </div>
      <div
        v-for="t in tasks"
        :key="`c-${s.studentId}-${t.taskId}`"
        class="matrix__cell"
        :class="cellClass(s, t.taskId)"
        role="cell"
        :title="cellTitle(s, t.taskId)"
      >
        {{ cellSymbol(s, t.taskId) }}
      </div>
    </template>
  </div>
  <el-empty v-else description="暂无任务或成员数据" :image-size="60" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { TASK_STATUS_MAP } from '@/constants';
import type { IProgressStudentRow, IProgressTaskColumn } from '@/types/api';
import { fmtDateTime } from '../projectUtils';

const props = defineProps<{ students: IProgressStudentRow[]; tasks: IProgressTaskColumn[] }>();

/** 纯 CSS grid：第一列为固定宽度姓名，其余任务列等分 */
const gridStyle = computed(() => ({
  gridTemplateColumns: `160px repeat(${props.tasks.length}, minmax(88px, 1fr))`,
}));

function statusLabel(status: string): string {
  return TASK_STATUS_MAP[status]?.label ?? status;
}

function cellFor(student: IProgressStudentRow, taskId: number) {
  return student.cells.find((c) => c.taskId === taskId);
}

function cellClass(student: IProgressStudentRow, taskId: number): string {
  const cell = cellFor(student, taskId);
  if (!cell) return 'matrix__cell--none';
  if (cell.overdue || cell.status === 'REJECTED') return 'matrix__cell--reject';
  if (cell.status === 'ACCEPTED') return 'matrix__cell--accepted';
  if (cell.status === 'SUBMITTED' || cell.status === 'IN_PROGRESS') return 'matrix__cell--progress';
  if (cell.status === 'CLOSED') return 'matrix__cell--closed';
  return 'matrix__cell--todo';
}

function cellSymbol(student: IProgressStudentRow, taskId: number): string {
  const cell = cellFor(student, taskId);
  if (!cell) return '';
  switch (cell.status) {
    case 'ACCEPTED':
      return '✓';
    case 'SUBMITTED':
      return '↑';
    case 'IN_PROGRESS':
      return '▶';
    case 'REJECTED':
      return '✕';
    case 'CLOSED':
      return '–';
    default:
      return '○';
  }
}

function cellTitle(student: IProgressStudentRow, taskId: number): string {
  const cell = cellFor(student, taskId);
  if (!cell) return `${student.name} · 未指派`;
  return `${student.name} · ${cell.taskTitle} · ${statusLabel(cell.status)}${cell.overdue ? '·逾期' : ''}`;
}
</script>

<style scoped lang="scss">
.matrix {
  display: grid;
  gap: 3px;
  overflow-x: auto;
  font-size: 12px;
}

.matrix__corner,
.matrix__col-head,
.matrix__row-head {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.matrix__col-head {
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.matrix__col-title {
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.matrix__col-meta {
  font-weight: 400;
  color: var(--text-tertiary);
}

.matrix__col-head--overdue .matrix__col-meta {
  font-weight: 600;
  color: var(--el-color-danger);
}

.matrix__row-head {
  justify-content: space-between;
}

.matrix__row-rate {
  font-weight: 700;
  color: var(--brand-700, var(--el-color-primary));
}

.matrix__cell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  font-size: 14px;
  color: #fff;
  border-radius: 4px;

  &--accepted {
    background: var(--el-color-success);
  }

  &--progress {
    background: var(--el-color-warning);
  }

  &--todo {
    color: var(--text-secondary);
    background: var(--el-color-info-light-8);
  }

  &--reject {
    background: var(--el-color-danger);
  }

  &--closed {
    color: var(--text-tertiary);
    background: var(--el-fill-color);
  }

  &--none {
    color: var(--text-disabled, #c0c4cc);
    background: var(--el-fill-color-lighter);
  }
}
</style>
