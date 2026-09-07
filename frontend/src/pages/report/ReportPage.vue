<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">报表中心</div>
        <div class="page-header__desc">
          {{ isAdmin ? '查看全校统计报表，支持导出' : '可见全校汇总与本人任教明细，其他教师明细不可见' }}
        </div>
      </div>
      <div class="filter-bar">
        <el-select v-model="academicYear" placeholder="选择学年" style="width: 180px" @change="loadData">
          <el-option v-for="year in academicYears" :key="year" :label="year" :value="year" />
        </el-select>
        <el-button :icon="Download" :disabled="!loaded" @click="handleExport('excel')">导出 Excel</el-button>
        <el-button :icon="Download" :disabled="!loaded" @click="handleExport('pdf')">导出 PDF</el-button>
      </div>
    </div>

    <!-- 教师可见：全校汇总行 -->
    <div v-if="!isAdmin && reportData?.summary" class="report-page__summary">
      <div class="stat-card">
        <div class="stat-card__label">全校课程总数</div>
        <div class="stat-card__value">{{ reportData.summary.courseCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card__label">全校实验项目总数</div>
        <div class="stat-card__value">{{ reportData.summary.experimentCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card__label">全校总人时数</div>
        <div class="stat-card__value">{{ reportData.summary.totalPersonHours }}</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="loadData">
      <el-tab-pane label="实验室课程项目统计" name="course-project-stats" />
      <el-tab-pane label="人时数统计" name="person-hours" />
    </el-tabs>

    <el-table v-loading="loading" :data="detailRows" border stripe>
      <el-table-column
        v-for="col in columns"
        :key="col.key"
        :prop="col.key"
        :label="col.label"
        :min-width="col.minWidth"
        :align="col.right ? 'right' : undefined"
        :class-name="col.right ? 'tabular-nums' : undefined"
      />
    </el-table>

    <el-alert
      v-if="!isAdmin && loaded"
      type="info"
      :closable="false"
      show-icon
      title="明细仅显示本人任教数据；如需全校明细请联系管理员"
      class="report-page__notice"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { Download } from '@element-plus/icons-vue';
import * as echarts from 'echarts/core';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { apiExportCourseProjectStats, apiExportPersonHours, apiGetCourseProjectStats, apiGetPersonHours } from '@/api/report';
import { apiListSemesters } from '@/api/semester';
import { useUserStore } from '@/store/user';
import { saveBlob } from '@/utils/request';
import type { IReportData, IReportRow } from '@/types/api';

/** 明细列定义（两份报表列近似，缺省列自动隐藏） */
interface IReportColumn {
  key: string;
  label: string;
  minWidth: number;
  right?: boolean;
}

const COLUMN_DEFS: IReportColumn[] = [
  { key: 'labCode', label: '实验室编号', minWidth: 120 },
  { key: 'labName', label: '实验室名称', minWidth: 160 },
  { key: 'courseCode', label: '课程号', minWidth: 110 },
  { key: 'courseName', label: '课程名', minWidth: 180 },
  { key: 'teacherName', label: '授课教师', minWidth: 100 },
  { key: 'experimentCount', label: '实验项目数', minWidth: 110, right: true },
  { key: 'personHours', label: '人时数', minWidth: 100, right: true },
];

const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

const activeTab = ref<'course-project-stats' | 'person-hours'>('course-project-stats');
const academicYear = ref('');
const academicYears = ref<string[]>([]);
const reportData = ref<IReportData | null>(null);
const loading = ref(false);
const loaded = ref(false);

const columns = computed(() => {
  const rows = reportData.value?.detail ?? [];
  if (rows.length === 0) return [];
  const sample = rows[0];
  return COLUMN_DEFS.filter((col) => sample[col.key] !== undefined);
});

const detailRows = computed<IReportRow[]>(() => reportData.value?.detail ?? []);

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

const chartRef = ref<HTMLDivElement>();
let chartInstance: ReturnType<typeof echarts.init> | null = null;

/** 人时数柱状图（仅 person-hours 页签显示） */
function renderChart(): void {
  if (activeTab.value !== 'person-hours' || detailRows.value.length === 0) return;
  void nextTick(() => {
    if (!chartRef.value) return;
    chartInstance ??= echarts.init(chartRef.value);
    const labels = detailRows.value.map((row) => String(row.courseName ?? row.labName ?? row.labCode ?? ''));
    const values = detailRows.value.map((row) => Number(row.personHours ?? 0));
    chartInstance?.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 48, right: 24, bottom: 60, top: 24 },
      xAxis: { type: 'category', data: labels, axisLabel: { rotate: 30, fontSize: 12 } },
      yAxis: { type: 'value', name: '人时数' },
      series: [{ type: 'bar', data: values, barMaxWidth: 32, itemStyle: { color: '#2563eb', borderRadius: [3, 3, 0, 0] } }],
    });
  });
}

function handleChartResize(): void {
  chartInstance?.resize();
}

function extractAcademicYears(semesters: { academicYear: string }[]): string[] {
  return [...new Set(semesters.map((item) => item.academicYear))].sort().reverse();
}

async function loadData(): Promise<void> {
  if (!academicYear.value) return;
  loading.value = true;
  try {
    const params = { academicYear: academicYear.value };
    reportData.value =
      activeTab.value === 'course-project-stats'
        ? await apiGetCourseProjectStats(params)
        : await apiGetPersonHours(params);
    loaded.value = true;
  } finally {
    loading.value = false;
  }
}

async function handleExport(format: 'excel' | 'pdf'): Promise<void> {
  const params = { academicYear: academicYear.value, format };
  const blob =
    activeTab.value === 'course-project-stats'
      ? await apiExportCourseProjectStats(params)
      : await apiExportPersonHours(params);
  const label = activeTab.value === 'course-project-stats' ? '课程项目统计' : '人时数统计';
  const ext = format === 'excel' ? 'xlsx' : 'pdf';
  saveBlob(blob, `${label}_${academicYear.value}.${ext}`);
}

onMounted(async () => {
  const semesters = await apiListSemesters();
  academicYears.value = extractAcademicYears(semesters);
  if (academicYears.value.length > 0) {
    academicYear.value = academicYears.value[0];
    await loadData();
  }
});
</script>

<style scoped lang="scss">
.report-page__summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.report-page__notice {
  margin-top: var(--space-4);
}

.report-page__chart {
  width: 100%;
  height: 360px;
  margin-bottom: var(--space-4);
}
</style>
