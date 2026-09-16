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
        <el-dropdown
          trigger="click"
          :disabled="!loaded || activeTab === 'narrative'"
          @command="handleExportCommand"
        >
          <el-button :icon="Download" :disabled="!loaded || activeTab === 'narrative'">
            导出<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="excel">导出 Excel</el-dropdown-item>
              <el-dropdown-item command="pdf">导出 PDF</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="实验室课程项目统计" name="course-project-stats" />
      <el-tab-pane label="人时数统计" name="person-hours" />
      <el-tab-pane label="AI 叙事报告" name="narrative" />
    </el-tabs>

    <!-- AI 叙事报告 / 教学总结（T3.3）：数字由后端真实聚合，LLM 只做语言包装 -->
    <div v-if="activeTab === 'narrative'" class="report-page__narrative">
      <div class="report-page__narrative-bar">
        <el-select v-model="narrativeKind" style="width: 220px">
          <el-option v-if="isAdmin" label="全校年度叙事报告" value="annual_report" />
          <el-option v-if="isAdmin" label="全校教学总结" value="teaching_summary" />
          <el-option v-if="!isAdmin" label="我的教学总结" value="teaching_summary" />
        </el-select>
        <el-button type="primary" :loading="narrativeLoading" @click="handleGenerateNarrative">
          {{ narrativeLoading ? '生成中…' : '生成报告' }}
        </el-button>
        <el-dropdown
          trigger="click"
          :disabled="!narrativeResult?.success"
          @command="handleNarrativeExportCommand"
        >
          <el-button :icon="Download" :disabled="!narrativeResult?.success">
            导出报告<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="pdf">导出 PDF</el-dropdown-item>
              <el-dropdown-item command="excel">导出 Excel</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div v-loading="narrativeLoading">
        <el-alert
          v-if="narrativeResult"
          type="info"
          :closable="false"
          show-icon
          title="以下数字全部来自真实报表聚合，AI 仅做语言包装"
          class="report-page__narrative-notice"
        />
        <el-descriptions v-if="narrativeResult" :column="3" border size="small" class="report-page__narrative-numbers">
          <el-descriptions-item
            v-for="(value, key) in narrativeResult.numbers"
            :key="key"
            :label="NARRATIVE_NUMBER_LABELS[key as string] ?? String(key)"
          >
            {{ value ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <pre v-if="narrativeResult?.success" class="report-page__narrative-text">{{ narrativeResult.text }}</pre>
        <el-alert
          v-if="narrativeResult && !narrativeResult.success"
          :title="narrativeResult.error ?? '生成失败，请稍后重试'"
          type="warning"
          show-icon
          :closable="false"
        />
      </div>
    </div>

    <template v-else>
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { Download, ArrowDown } from '@element-plus/icons-vue';
import * as echarts from 'echarts/core';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import {
  apiExportCourseProjectStats,
  apiExportNarrative,
  apiExportPersonHours,
  apiGetCourseProjectStats,
  apiGetPersonHours,
} from '@/api/report';
import { apiNlqNarrative, type INlqNarrativeResult } from '@/api/nlq';
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

const activeTab = ref<'course-project-stats' | 'person-hours' | 'narrative'>('course-project-stats');
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
  if (activeTab.value === 'narrative') return;
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

function handleTabChange(): void {
  if (activeTab.value !== 'narrative') void loadData();
}

/** 叙事报告数字的中文标签 */
const NARRATIVE_NUMBER_LABELS: Record<string, string> = {
  semester_name: '当前学期',
  academic_year: '学年',
  laboratory_total: '实验室总数',
  device_total: '设备总数',
  teacher_total: '任课教师数',
  schedule_total: '排课总数',
  course_total: '课程数',
  person_hours: '人时数',
  experiment_total: '实验项目数',
};

const narrativeKind = ref<'annual_report' | 'teaching_summary'>('teaching_summary');
const narrativeLoading = ref(false);
const narrativeResult = ref<INlqNarrativeResult | null>(null);

async function handleGenerateNarrative(): Promise<void> {
  narrativeLoading.value = true;
  narrativeResult.value = null;
  try {
    narrativeResult.value = await apiNlqNarrative(narrativeKind.value);
  } catch {
    // 错误由 request 拦截器统一提示
  } finally {
    narrativeLoading.value = false;
  }
}

/** 叙事导出文档标题（与页签内类型选择器口径一致） */
const NARRATIVE_TITLES = {
  annual_report: '全校年度叙事报告',
  teaching_summary: '全校教学总结',
} as const;

const narrativeTitle = computed(() =>
  isAdmin.value ? NARRATIVE_TITLES[narrativeKind.value] : '我的教学总结',
);

/** 学期标签：semester_name 形如 2026-2027-1 → 2026-2027学年第1学期 */
const narrativeSemesterLabel = computed(() => {
  const raw = String(narrativeResult.value?.numbers?.semester_name ?? '');
  const matched = /^(\d{4}-\d{4})-([12])$/.exec(raw);
  return matched ? `${matched[1]}学年第${matched[2]}学期` : raw;
});

function handleNarrativeExportCommand(command: string): void {
  void handleNarrativeExport(command as 'excel' | 'pdf');
}

/** 导出叙事报告：提交页面所见内容，后端仅排版（所见即所得） */
async function handleNarrativeExport(format: 'excel' | 'pdf'): Promise<void> {
  const result = narrativeResult.value;
  if (!result?.success) return;
  const numbers = Object.entries(result.numbers ?? {})
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({ label: NARRATIVE_NUMBER_LABELS[key] ?? key, value: String(value) }));
  const payload = {
    title: narrativeTitle.value,
    semester: narrativeSemesterLabel.value,
    text: result.text,
    numbers,
  };
  try {
    const blob = await apiExportNarrative({ format }, payload);
    const ext = format === 'excel' ? 'xlsx' : 'pdf';
    const suffix = payload.semester ? `_${payload.semester}` : '';
    saveBlob(blob, `${payload.title}${suffix}.${ext}`);
  } catch {
    // 错误由 request 拦截器统一提示
  }
}

function handleExportCommand(command: string): void {
  void handleExport(command as 'excel' | 'pdf');
}

async function handleExport(format: 'excel' | 'pdf'): Promise<void> {
  const params = { academicYear: academicYear.value, format };
  try {
    const blob =
      activeTab.value === 'course-project-stats'
        ? await apiExportCourseProjectStats(params)
        : await apiExportPersonHours(params);
    const label = activeTab.value === 'course-project-stats' ? '课程项目统计' : '人时数统计';
    const ext = format === 'excel' ? 'xlsx' : 'pdf';
    saveBlob(blob, `${label}_${academicYear.value}.${ext}`);
  } catch {
    // 错误由 request 拦截器统一提示
  }
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

.report-page__narrative-bar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.report-page__narrative-notice {
  margin-bottom: var(--space-3);
}

.report-page__narrative-numbers {
  margin-bottom: var(--space-4);
}

.report-page__narrative-text {
  margin: 0;
  padding: var(--space-4);
  background: var(--bg-subtle);
  border-radius: var(--radius-md);
  font-size: 14px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-all;
}

.report-page__chart {
  width: 100%;
  height: 360px;
  margin-bottom: var(--space-4);
}
</style>
