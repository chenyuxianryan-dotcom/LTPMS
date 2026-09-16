<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">课表管理</div>
        <div class="page-header__desc">全校课表总览；课表由教师本人在「课程管理」页导入，系统按「课程号 + 学期」自动归并生成实验课程</div>
      </div>
      <div class="filter-bar">
        <el-select v-model="semesterId" placeholder="选择学期" style="width: 220px" @change="handleSearch">
          <el-option v-for="item in semesters" :key="item.id" :label="item.semesterName" :value="item.id" />
        </el-select>
        <el-button type="danger" plain :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          批量删除（{{ selectedIds.length }}）
        </el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="records" border stripe @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="44" />
      <el-table-column prop="courseCode" label="课程号" width="120" />
      <el-table-column prop="courseName" label="课程名" min-width="180" />
      <el-table-column prop="teacherName" label="授课教师" width="110" />
      <el-table-column prop="labCode" label="上课地点" width="120" />
      <el-table-column label="开放注记" min-width="140">
        <template #default="{ row }">
          <el-tooltip v-if="noteOf(row.labCode).type !== 'none'" :content="noteOf(row.labCode).tip" placement="top" :show-after="200">
            <el-tag :type="noteOf(row.labCode).tagType" size="small" :effect="noteOf(row.labCode).effect">
              {{ noteOf(row.labCode).text }}
            </el-tag>
          </el-tooltip>
          <span v-else class="schedule-page__muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="classNames" label="上课班级" min-width="180" show-overflow-tooltip />
      <el-table-column prop="studentCount" label="人数" width="80" align="right" class-name="tabular-nums" />
      <el-table-column prop="totalHours" label="实验学时" width="100" align="right" class-name="tabular-nums" />
      <el-table-column prop="rawScheduleText" label="上课时间" min-width="150" show-overflow-tooltip />
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50]"
      class="page-container__pagination"
      @current-change="loadData"
      @size-change="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiGetLabOpeningOverview, apiListLabOpenings } from '@/api/labOpening';
import { apiBatchDeleteSchedules, apiListSchedules } from '@/api/schedule';
import { apiListSemesters } from '@/api/semester';
import { WEEKDAY_LABELS, WEEK_TYPE_MAP } from '@/constants';
import type { ILabOpening, ISchedule, ISemester, IOpenOverviewItem } from '@/types/api';

const semesterId = ref<number | null>(null);
const semesters = ref<ISemester[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const records = ref<ISchedule[]>([]);
const selectedIds = ref<number[]>([]);
const openingsByLab = ref<Map<string, ILabOpening[]>>(new Map());
const overviewMap = ref<Map<string, IOpenOverviewItem>>(new Map());

interface IOpenNote {
  type: 'none' | 'open' | 'conflict' | 'unplanned';
  text: string;
  tip: string;
  tagType: 'info' | 'danger';
  effect: 'light' | 'dark';
}

/** 按实验室聚合的开放注记：矛盾 > 未开放 > 正常开放段数 */
function noteOf(labCode: string): IOpenNote {
  const conflictCount = overviewMap.value.get(labCode)?.conflictCount ?? 0;
  if (conflictCount > 0) {
    return {
      type: 'conflict',
      text: `矛盾 ${conflictCount} 处`,
      tip: '该校表与实验室开放时段存在时间矛盾（详见数据智能页）',
      tagType: 'danger',
      effect: 'dark',
    };
  }
  const enabled = (openingsByLab.value.get(labCode) ?? []).filter((slot) => slot.enabled === 1);
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

async function loadSemesters(): Promise<void> {
  semesters.value = await apiListSemesters();
  // 默认选中当前学期
  const current = semesters.value.find((item) => item.status === 'ACTIVE');
  if (current) {
    semesterId.value = current.id;
  }
  await refreshAll();
}

async function loadData(): Promise<void> {
  if (!semesterId.value) return;
  loading.value = true;
  try {
    const result = await apiListSchedules({ semesterId: semesterId.value, page: page.value, size: size.value });
    records.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

/** 开放注记数据：开放时段按实验室分组 + 总览矛盾数；失败不阻塞课表主流程 */
async function loadOpenings(): Promise<void> {
  if (!semesterId.value) return;
  try {
    const [openings, overview] = await Promise.all([
      apiListLabOpenings({ semesterId: semesterId.value }),
      apiGetLabOpeningOverview(semesterId.value),
    ]);
    const grouped = new Map<string, ILabOpening[]>();
    for (const slot of openings) {
      const list = grouped.get(slot.labCode) ?? [];
      list.push(slot);
      grouped.set(slot.labCode, list);
    }
    openingsByLab.value = grouped;
    overviewMap.value = new Map(overview.labs.map((item) => [item.labCode, item]));
  } catch {
    openingsByLab.value = new Map();
    overviewMap.value = new Map();
  }
}

/** 课表与开放注记一并刷新 */
async function refreshAll(): Promise<void> {
  await Promise.all([loadData(), loadOpenings()]);
}

function handleSearch(): void {
  page.value = 1;
  void refreshAll();
}

function handleSelectionChange(rows: ISchedule[]): void {
  selectedIds.value = rows.map((row) => row.id);
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条课表记录吗？`, '批量删除确认', { type: 'warning' });
  await apiBatchDeleteSchedules(selectedIds.value);
  ElMessage.success('删除成功');
  selectedIds.value = [];
  await refreshAll();
}

onMounted(() => {
  void loadSemesters();
});
</script>

<style scoped lang="scss">
.page-container__pagination {
  margin-top: var(--space-4);
  justify-content: flex-end;
}

.schedule-page__muted {
  color: var(--el-text-color-placeholder);
}
</style>
