<template>
  <div class="insight-page">
    <div class="insight-page__head">
      <h2 class="insight-page__title">数据智能</h2>
      <p class="insight-page__subtitle">就绪度体检 · 利用率热力 · 异常预警 · 冲突检测</p>
    </div>

    <div class="insight-page__grid">
      <!-- 就绪度体检 -->
      <el-card class="insight-card" shadow="never">
        <template #header>
          <div class="insight-card__head">
            <span>课程就绪度体检</span>
            <el-tag size="small" :type="readinessSummary.type" effect="light">{{ readinessSummary.text }}</el-tag>
          </div>
        </template>
        <div v-loading="loading.readiness" class="insight-card__body">
          <el-empty v-if="readiness.length === 0" description="暂无课程数据" :image-size="60" />
          <div v-else class="readiness-list">
            <div v-for="item in readiness" :key="item.courseId" class="readiness-list__item">
              <span class="readiness-list__code">{{ item.courseCode }}</span>
              <span class="readiness-list__name">{{ item.courseName }}</span>
              <span v-if="item.teacherName" class="readiness-list__teacher">{{ item.teacherName }}</span>
              <el-tag size="small" :type="item.status === 'GREEN' ? 'success' : 'danger'" effect="light">
                {{ item.status === 'GREEN' ? '已就绪' : '待补建' }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 利用率热力图 -->
      <el-card class="insight-card" shadow="never">
        <template #header>
          <div class="insight-card__head">
            <span>实验室利用率热力图</span>
            <div class="heat-legend">
              <span class="heat-legend__dot heat-legend__dot--gray"></span>闲置
              <span class="heat-legend__dot heat-legend__dot--green"></span>低
              <span class="heat-legend__dot heat-legend__dot--yellow"></span>中
              <span class="heat-legend__dot heat-legend__dot--red"></span>高
            </div>
          </div>
        </template>
        <div v-loading="loading.utilization" class="insight-card__body">
          <el-empty v-if="utilization.length === 0" description="暂无实验室数据" :image-size="60" />
          <div v-else class="heat-grid">
            <el-tooltip
              v-for="lab in utilization"
              :key="lab.labId"
              :content="`${lab.labCode} ${lab.labName}：排课 ${lab.scheduleCount} 条 / 时段 ${lab.timeSlotCount}`"
              placement="top"
            >
              <div class="heat-grid__cell" :class="heatClass(lab.scheduleCount)">
                <span class="heat-grid__code">{{ lab.labCode }}</span>
                <span class="heat-grid__count">{{ lab.scheduleCount }}</span>
              </div>
            </el-tooltip>
          </div>
        </div>
      </el-card>

      <!-- 异常预警 -->
      <el-card class="insight-card" shadow="never">
        <template #header>
          <div class="insight-card__head">
            <span>异常预警</span>
            <el-tag v-if="anomalies.length > 0" size="small" type="danger" effect="light">{{ anomalies.length }} 项</el-tag>
            <el-tag v-else size="small" type="success" effect="light">无异常</el-tag>
          </div>
        </template>
        <div v-loading="loading.anomalies" class="insight-card__body">
          <el-empty v-if="anomalies.length === 0" description="一切正常" :image-size="60" />
          <div v-else class="anomaly-list">
            <div v-for="(item, idx) in anomalies" :key="idx" class="anomaly-list__item">
              <el-tag size="small" :type="severityType(item.severity)" effect="light">{{ severityLabel(item.type) }}</el-tag>
              <div class="anomaly-list__content">
                <div class="anomaly-list__target">{{ item.target }}</div>
                <div class="anomaly-list__detail">{{ item.detail }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 冲突检测 -->
      <el-card class="insight-card" shadow="never">
        <template #header>
          <div class="insight-card__head">
            <span>排课冲突检测</span>
            <el-tag v-if="conflicts.length > 0" size="small" type="warning" effect="light">{{ conflicts.length }} 项</el-tag>
            <el-tag v-else size="small" type="success" effect="light">无冲突</el-tag>
          </div>
        </template>
        <div v-loading="loading.conflicts" class="insight-card__body">
          <el-empty v-if="conflicts.length === 0" description="未发现排课冲突" :image-size="60" />
          <div v-else class="conflict-list">
            <div v-for="(item, idx) in conflicts" :key="idx" class="conflict-list__item">
              <el-tag size="small" :type="item.resourceType === 'LAB' ? 'info' : 'primary'" effect="light">
                {{ item.resourceType === 'LAB' ? '实验室' : '教师' }}
              </el-tag>
              <span class="conflict-list__name">{{ item.resourceName }}</span>
              <span class="conflict-list__time">
                周{{ dayLabel(item.dayOfWeek) }} 第{{ item.startSection }}-{{ item.endSection }}节
                <template v-if="item.overlapWeeks">（{{ item.overlapWeeks }}周）</template>
              </span>
              <span class="conflict-list__courses">{{ item.courseCodes.join('、') }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  apiGetAnomalies,
  apiGetConflicts,
  apiGetReadiness,
  apiGetUtilization,
  type IAnomalyItem,
  type IConflictItem,
  type IReadinessItem,
  type IUtilizationItem,
} from '@/api/insight';

const readiness = ref<IReadinessItem[]>([]);
const utilization = ref<IUtilizationItem[]>([]);
const anomalies = ref<IAnomalyItem[]>([]);
const conflicts = ref<IConflictItem[]>([]);
const loading = reactive({ readiness: false, utilization: false, anomalies: false, conflicts: false });

const readinessSummary = computed(() => {
  const total = readiness.value.length;
  const ready = readiness.value.filter((r) => r.status === 'GREEN').length;
  if (total === 0) return { text: '无课程', type: 'info' as const };
  if (ready === total) return { text: `全部就绪 ${total} 门`, type: 'success' as const };
  return { text: `${ready}/${total} 门就绪`, type: 'warning' as const };
});

function heatClass(count: number): string {
  if (count === 0) return 'heat-grid__cell--gray';
  if (count <= 2) return 'heat-grid__cell--green';
  if (count <= 4) return 'heat-grid__cell--yellow';
  return 'heat-grid__cell--red';
}

function severityType(severity: string): 'danger' | 'warning' | 'info' {
  if (severity === 'HIGH') return 'danger';
  if (severity === 'MEDIUM') return 'warning';
  return 'info';
}

function severityLabel(type: string): string {
  if (type === 'OVERLOAD') return '超排';
  if (type === 'IDLE') return '闲置';
  return '课时异常';
}

function dayLabel(day: number): string {
  return ['一', '二', '三', '四', '五', '六', '日'][day - 1] ?? String(day);
}

async function loadAll(): Promise<void> {
  loading.readiness = true;
  loading.utilization = true;
  loading.anomalies = true;
  loading.conflicts = true;
  try {
    [readiness.value, utilization.value, anomalies.value, conflicts.value] = await Promise.all([
      apiGetReadiness(),
      apiGetUtilization(),
      apiGetAnomalies(),
      apiGetConflicts(),
    ]);
  } catch {
    // 各接口失败由 request 拦截器统一提示，此处静默
  } finally {
    loading.readiness = false;
    loading.utilization = false;
    loading.anomalies = false;
    loading.conflicts = false;
  }
}

onMounted(loadAll);
</script>

<style scoped lang="scss">
.insight-page {
  padding: var(--space-6);
  height: 100%;
  overflow-y: auto;

  &__head {
    margin-bottom: var(--space-5);
  }

  &__title {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
  }

  &__subtitle {
    margin: var(--space-1) 0 0;
    font-size: 13px;
    color: var(--text-tertiary);
  }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--space-5);
  }
}

.insight-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-subtle);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__body {
    min-height: 180px;
  }
}

.readiness-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);

  &__item {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-2) var(--space-3);
    border-radius: var(--radius-md);
    background: var(--bg-subtle);
    font-size: 13px;
  }

  &__code {
    font-family: monospace;
    font-weight: 600;
    color: var(--brand-700);
  }

  &__name {
    flex: 1;
    color: var(--text-primary);
  }

  &__teacher {
    color: var(--text-tertiary);
    font-size: 12px;
  }
}

.heat-legend {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  color: var(--text-tertiary);

  &__dot {
    width: 10px;
    height: 10px;
    border-radius: 2px;
    margin-left: var(--space-2);

    &--gray { background: #e5e7eb; }
    &--green { background: #86efac; }
    &--yellow { background: #fde68a; }
    &--red { background: #fca5a5; }
  }
}

.heat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
  gap: var(--space-3);

  &__cell {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 64px;
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: transform 0.15s ease, box-shadow 0.15s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgb(0 0 0 / 12%);
    }

    &--gray { background: #e5e7eb; color: #6b7280; }
    &--green { background: #86efac; color: #14532d; }
    &--yellow { background: #fde68a; color: #713f12; }
    &--red { background: #fca5a5; color: #7f1d1d; }
  }

  &__code {
    font-size: 11px;
    font-weight: 600;
  }

  &__count {
    font-size: 12px;
    margin-top: 2px;
  }
}

.anomaly-list,
.conflict-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-height: 260px;
  overflow-y: auto;
}

.anomaly-list {
  &__item {
    display: flex;
    align-items: flex-start;
    gap: var(--space-3);
    padding: var(--space-2) var(--space-3);
    border-radius: var(--radius-md);
    background: var(--bg-subtle);
  }

  &__content {
    flex: 1;
  }

  &__target {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__detail {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 2px;
  }
}

.conflict-list {
  &__item {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-2) var(--space-3);
    border-radius: var(--radius-md);
    background: var(--bg-subtle);
    font-size: 13px;
  }

  &__name {
    font-weight: 600;
    color: var(--text-primary);
  }

  &__time {
    color: var(--text-secondary);
  }

  &__courses {
    margin-left: auto;
    color: var(--danger-600, #dc2626);
    font-family: monospace;
  }
}
</style>
