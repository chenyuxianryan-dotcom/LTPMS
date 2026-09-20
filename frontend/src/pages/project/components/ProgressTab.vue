<template>
  <div v-loading="loading">
    <template v-if="stats">
      <div class="progress-tab__summary">
        <div class="progress-tab__stat">
          <div class="progress-tab__stat-num">{{ Math.round(stats.projectRate) }}%</div>
          <div class="progress-tab__stat-label">完成率（验收 / 在途任务）</div>
        </div>
        <div class="progress-tab__stat">
          <div class="progress-tab__stat-num">{{ stats.total }}</div>
          <div class="progress-tab__stat-label">任务总数（不含已关闭）</div>
        </div>
        <div class="progress-tab__stat">
          <div class="progress-tab__stat-num">{{ stats.accepted }}</div>
          <div class="progress-tab__stat-label">已验收</div>
        </div>
        <div class="progress-tab__stat">
          <div class="progress-tab__stat-num" :class="{ 'progress-tab__danger': stats.overdueCount > 0 }">{{ stats.overdueCount }}</div>
          <div class="progress-tab__stat-label">逾期</div>
        </div>
        <div class="progress-tab__stat">
          <div class="progress-tab__stat-num">{{ stats.memberCount }}</div>
          <div class="progress-tab__stat-label">成员</div>
        </div>
      </div>

      <ProgressMatrix :students="stats.students" :tasks="stats.tasks" />

      <div class="progress-tab__legend">
        <span><i class="dot dot--accepted" />已验收</span>
        <span><i class="dot dot--progress" />进行中 / 待验收</span>
        <span><i class="dot dot--todo" />待领取</span>
        <span><i class="dot dot--reject" />被驳回 / 逾期</span>
        <span><i class="dot dot--closed" />已关闭 / 未指派</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { apiProjectProgress } from '@/api/project';
import type { IProjectStats } from '@/types/api';
import ProgressMatrix from './ProgressMatrix.vue';

const props = defineProps<{ projectId: number }>();

const loading = ref(false);
const stats = ref<IProjectStats | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  try {
    stats.value = await apiProjectProgress(props.projectId);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
watch(() => props.projectId, load);
</script>

<style scoped lang="scss">
.progress-tab {
  &__summary {
    display: flex;
    gap: var(--space-6);
    padding: var(--space-4);
    margin-bottom: var(--space-4);
    background: var(--bg-card, #fff);
    border: 1px solid var(--border-color, var(--el-border-color-light));
    border-radius: var(--radius-lg, 8px);
  }

  &__stat {
    min-width: 110px;
  }

  &__stat-num {
    font-size: 26px;
    font-weight: 700;
    color: var(--brand-700, var(--el-color-primary));
    line-height: 1.2;
  }

  &__stat-label {
    margin-top: 2px;
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__danger {
    color: var(--el-color-danger);
  }

  &__legend {
    display: flex;
    gap: var(--space-4);
    margin-top: var(--space-3);
    font-size: 12px;
    color: var(--text-secondary);

    .dot {
      display: inline-block;
      width: 10px;
      height: 10px;
      margin-right: 4px;
      border-radius: 2px;
      vertical-align: middle;

      &--accepted {
        background: var(--el-color-success);
      }

      &--progress {
        background: var(--el-color-warning);
      }

      &--todo {
        background: var(--el-color-info-light-7);
      }

      &--reject {
        background: var(--el-color-danger);
      }

      &--closed {
        background: var(--el-fill-color-darker);
      }
    }
  }
}
</style>
