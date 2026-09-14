<template>
  <div v-loading="loading" class="occupancy-panel">
    <div class="occupancy-panel__summary">
      <span>
        已配置 <strong>{{ plannedCount }}</strong> / {{ overview?.labs.length ?? 0 }} 间
      </span>
      <span>占用节时合计 <strong>{{ totalOccupied }}</strong></span>
      <span :class="{ 'occupancy-panel__danger': totalConflicts > 0 }">
        课表矛盾 <strong>{{ totalConflicts }}</strong> 处
      </span>
      <span class="occupancy-panel__note">利用率 = 占用节时 / 开放节时（周加权，不含节假日/例外日扣减）</span>
    </div>

    <el-table :data="overview?.labs ?? []" border stripe max-height="520">
      <el-table-column prop="labCode" label="实验室编号" width="110" />
      <el-table-column prop="labName" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.planned ? 'success' : 'info'" size="small" effect="light">
            {{ row.planned ? '已配置' : '未开放' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开放对象" width="110">
        <template #default="{ row }">{{ row.audience || '—' }}</template>
      </el-table-column>
      <el-table-column prop="openSlotHours" label="开放节时" width="90" align="right" />
      <el-table-column prop="occupiedSlotHours" label="占用节时" width="90" align="right" />
      <el-table-column label="利用率" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.rate === null" type="info" size="small" effect="plain">未开放</el-tag>
          <el-tooltip
            v-else
            :content="`占用 ${row.occupiedSlotHours} / 开放 ${row.openSlotHours} 节时`"
            placement="top"
          >
            <el-tag :type="rateTagType(row.rate)" size="small" effect="light">{{ row.rate }}%</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="矛盾" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.conflictCount > 0" type="danger" size="small" effect="dark">{{ row.conflictCount }} 处</el-tag>
          <span v-else class="occupancy-panel__muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openGrid(row)">查看周网格</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="800px" destroy-on-close>
      <div class="occupancy-panel__grid-bar">
        <span>周次：</span>
        <el-select v-model="week" style="width: 240px" @change="loadGrid">
          <el-option v-for="item in weekOptions" :key="item.week" :label="item.label" :value="item.week" />
        </el-select>
      </div>
      <OpenWeekGrid :grid="grid" :loading="gridLoading" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { apiGetLabOpenGrid, apiGetLabOpeningOverview } from '@/api/labOpening';
import { apiGetSemesterCalendar } from '@/api/semester';
import type { ILabOpenGrid, ILabOpeningOverview, IOpenOverviewItem, ISemesterCalendar } from '@/types/api';
import OpenWeekGrid from './OpenWeekGrid.vue';

const props = defineProps<{ semesterId: number }>();

const loading = ref(false);
const overview = ref<ILabOpeningOverview | null>(null);
const calendar = ref<ISemesterCalendar | null>(null);
const drawerVisible = ref(false);
const grid = ref<ILabOpenGrid | null>(null);
const gridLoading = ref(false);
const week = ref<number | null>(null);
const currentLab = ref<IOpenOverviewItem | null>(null);

const plannedCount = computed(() => (overview.value?.labs ?? []).filter((item) => item.planned).length);
const totalOccupied = computed(() => (overview.value?.labs ?? []).reduce((sum, item) => sum + item.occupiedSlotHours, 0));
const totalConflicts = computed(() => (overview.value?.labs ?? []).reduce((sum, item) => sum + item.conflictCount, 0));
const drawerTitle = computed(() => (currentLab.value ? `${currentLab.value.labCode} · ${currentLab.value.labName} 周网格` : '周网格'));

const weekOptions = computed(() => {
  const weeks = calendar.value?.weeks ?? [];
  if (weeks.length) {
    return weeks.map((item) => ({ week: item.week, label: `第 ${item.week} 周（${item.startDate.slice(5)} ~ ${item.endDate.slice(5)}）` }));
  }
  const total = overview.value?.totalWeeks ?? 0;
  return Array.from({ length: total }, (_, index) => ({ week: index + 1, label: `第 ${index + 1} 周` }));
});

function rateTagType(rate: number | null): 'success' | 'warning' | 'danger' {
  if (rate === null) return 'success';
  if (rate >= 75) return 'danger';
  if (rate >= 40) return 'warning';
  return 'success';
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [overviewData, calendarData] = await Promise.all([
      apiGetLabOpeningOverview(props.semesterId),
      apiGetSemesterCalendar(props.semesterId),
    ]);
    overview.value = overviewData;
    calendar.value = calendarData;
  } finally {
    loading.value = false;
  }
}

async function openGrid(row: IOpenOverviewItem): Promise<void> {
  currentLab.value = row;
  week.value = null;
  drawerVisible.value = true;
  await loadGrid();
}

async function loadGrid(): Promise<void> {
  if (!currentLab.value) return;
  gridLoading.value = true;
  try {
    grid.value = await apiGetLabOpenGrid({
      semesterId: props.semesterId,
      labCode: currentLab.value.labCode,
      week: week.value ?? undefined,
    });
    week.value = grid.value.week;
  } finally {
    gridLoading.value = false;
  }
}

watch(() => props.semesterId, () => {
  void load();
});

onMounted(() => {
  void load();
});
</script>

<style scoped lang="scss">
.occupancy-panel {
  &__summary {
    display: flex;
    align-items: center;
    gap: var(--space-4);
    margin-bottom: var(--space-3);
    font-size: 13px;
  }

  &__note {
    margin-left: auto;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__danger {
    color: var(--el-color-danger);
  }

  &__muted {
    color: var(--el-text-color-placeholder);
  }

  &__grid-bar {
    display: flex;
    align-items: center;
    margin-bottom: var(--space-3);
  }
}
</style>
