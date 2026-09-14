<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">学期管理</div>
        <div class="page-header__desc">学年分组视图：周历配置、开放计划、占用一览；学期状态由起止日期自动计算</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建学期</el-button>
    </div>

    <div v-loading="loading" class="semester-page__years">
      <div v-for="group in groups" :key="group.year" class="semester-page__year">
        <div class="semester-page__year-head">
          {{ group.year }} <span class="semester-page__year-unit">学年</span>
        </div>
        <div class="semester-page__cards">
          <div
            v-for="item in group.items"
            :key="item.id"
            class="semester-card"
            :class="{ 'semester-card--active': item.id === selected?.id }"
            @click="selectedId = item.id"
          >
            <div class="semester-card__top">
              <span class="semester-card__name">{{ item.semesterName }}</span>
              <el-tag :type="SEMESTER_STATUS_MAP[item.status]?.tagType ?? 'info'" size="small" effect="light">
                {{ SEMESTER_STATUS_MAP[item.status]?.label ?? item.status }}
              </el-tag>
            </div>
            <div class="semester-card__dates">{{ item.startDate }} ~ {{ item.endDate }}</div>
            <div class="semester-card__meta">
              <span>
                {{ weekText(item) }}
                <template v-if="item.status === 'ACTIVE'">（第 {{ currentWeekOf(item) }} 周）</template>
              </span>
              <span :class="{ 'semester-card__meta--warn': !item.week1StartDate }">
                {{ item.week1StartDate ? `周历：${item.week1StartDate.slice(5)} 起` : '周历未配置' }}
              </span>
            </div>
            <el-progress
              v-if="item.status === 'ACTIVE'"
              :percentage="progressOf(item)"
              :stroke-width="6"
              :show-text="false"
              class="semester-card__progress"
            />
            <div class="semester-card__ops">
              <el-button v-if="item.status === 'UPCOMING'" link type="primary" @click.stop="openEdit(item)">编辑</el-button>
              <span v-else class="semester-card__ops-hint">{{ item.status === 'ACTIVE' ? '进行中' : '已归档' }}</span>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && !groups.length" description="暂无学期数据" />
    </div>

    <div v-if="selected" class="semester-page__detail">
      <div class="semester-page__detail-head">{{ selected.semesterName }} · 详情</div>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="周历" name="calendar" lazy>
          <SemesterCalendarPanel :key="`cal-${selected.id}`" :semester="selected" @changed="handleCalendarChanged" />
        </el-tab-pane>
        <el-tab-pane label="开放计划" name="opening" lazy>
          <LabOpeningPanel :key="`open-${selected.id}-${version.opening}`" :semester-id="selected.id" @changed="version.occupancy++" />
        </el-tab-pane>
        <el-tab-pane label="占用视图" name="occupancy" lazy>
          <OccupancyPanel :key="`occ-${selected.id}-${version.occupancy}`" :semester-id="selected.id" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑学期' : '新建学期'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="学年" prop="academicYear">
          <el-input v-model="form.academicYear" :disabled="editing" placeholder="如 2026-2027" />
        </el-form-item>
        <el-form-item label="学期序号" prop="semesterNum">
          <el-radio-group v-model="form.semesterNum" :disabled="editing">
            <el-radio-button :value="1">第 1 学期</el-radio-button>
            <el-radio-button :value="2">第 2 学期</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { apiCreateSemester, apiListSemesters, apiUpdateSemester } from '@/api/semester';
import { SEMESTER_STATUS_MAP } from '@/constants';
import type { ISemester } from '@/types/api';
import LabOpeningPanel from './components/LabOpeningPanel.vue';
import OccupancyPanel from './components/OccupancyPanel.vue';
import SemesterCalendarPanel from './components/SemesterCalendarPanel.vue';

const loading = ref(false);
const records = ref<ISemester[]>([]);
const selectedId = ref<number | null>(null);
const activeTab = ref('calendar');
const version = reactive({ opening: 0, occupancy: 0 });
const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({ id: 0, academicYear: '', semesterNum: 1, startDate: '', endDate: '' });

const rules: FormRules = {
  academicYear: [{ required: true, message: '请输入学年', trigger: 'blur' }],
  semesterNum: [{ required: true, message: '请选择学期序号', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
};

const groups = computed(() => {
  const map = new Map<string, ISemester[]>();
  for (const item of records.value) {
    const list = map.get(item.academicYear) ?? [];
    list.push(item);
    map.set(item.academicYear, list);
  }
  return [...map.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([year, items]) => ({ year, items: [...items].sort((a, b) => a.semesterNum - b.semesterNum) }));
});

const selected = computed(() => records.value.find((item) => item.id === selectedId.value) ?? null);

function parseLocal(date: string): Date {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function totalWeeksOf(item: ISemester): number {
  const base = item.week1StartDate || item.startDate;
  const days = Math.floor((parseLocal(item.endDate).getTime() - parseLocal(base).getTime()) / 86400000);
  return days < 0 ? 0 : Math.floor(days / 7) + 1;
}

function currentWeekOf(item: ISemester): number {
  const base = item.week1StartDate || item.startDate;
  const today = new Date();
  const now = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const days = Math.floor((now.getTime() - parseLocal(base).getTime()) / 86400000);
  if (days < 0) return 0;
  return Math.min(Math.floor(days / 7) + 1, totalWeeksOf(item));
}

function progressOf(item: ISemester): number {
  const total = totalWeeksOf(item);
  if (!total) return 0;
  return Math.min(100, Math.round(((currentWeekOf(item) - 1) / total) * 100));
}

function weekText(item: ISemester): string {
  const total = totalWeeksOf(item);
  if (item.status === 'UPCOMING') return `共 ${total} 周 · 未开始`;
  if (item.status === 'ENDED') return `共 ${total} 周 · 已结束`;
  return `共 ${total} 周`;
}

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    records.value = await apiListSemesters();
    if (!records.value.some((item) => item.id === selectedId.value)) {
      selectedId.value = (records.value.find((item) => item.status === 'ACTIVE') ?? records.value[0])?.id ?? null;
    }
  } finally {
    loading.value = false;
  }
}

function handleCalendarChanged(): void {
  void loadData();
  version.occupancy++;
}

function openCreate(): void {
  editing.value = false;
  Object.assign(form, { id: 0, academicYear: '', semesterNum: 1, startDate: '', endDate: '' });
  dialogVisible.value = true;
}

function openEdit(row: ISemester): void {
  editing.value = true;
  Object.assign(form, { id: row.id, academicYear: row.academicYear, semesterNum: row.semesterNum, startDate: row.startDate, endDate: row.endDate });
  dialogVisible.value = true;
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (form.endDate <= form.startDate) {
    ElMessage.warning('结束日期必须晚于开始日期');
    return;
  }
  saving.value = true;
  try {
    if (editing.value) {
      await apiUpdateSemester(form.id, { startDate: form.startDate, endDate: form.endDate });
    } else {
      await apiCreateSemester({ ...form });
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  void loadData();
});
</script>

<style scoped lang="scss">
.semester-page {
  &__years {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  &__year-head {
    font-size: 15px;
    font-weight: 600;
    margin-bottom: var(--space-2);
  }

  &__year-unit {
    color: var(--el-text-color-secondary);
    font-weight: 400;
    font-size: 12px;
  }

  &__cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: var(--space-3);
  }

  &__detail {
    margin-top: var(--space-5);
  }

  &__detail-head {
    font-size: 15px;
    font-weight: 600;
    margin-bottom: var(--space-1);
  }
}

.semester-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md, 8px);
  padding: var(--space-3);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }

  &--active {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
  }

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-1);
  }

  &__name {
    font-weight: 600;
  }

  &__dates {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    margin-bottom: var(--space-1);
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12px;
    color: var(--el-text-color-secondary);

    &--warn {
      color: var(--el-color-warning);
    }
  }

  &__progress {
    margin-top: var(--space-2);
  }

  &__ops {
    margin-top: var(--space-1);
    text-align: right;
    min-height: 24px;
  }

  &__ops-hint {
    color: var(--el-text-color-placeholder);
    font-size: 12px;
  }
}
</style>
