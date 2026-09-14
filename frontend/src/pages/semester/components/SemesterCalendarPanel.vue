<template>
  <div v-loading="loading" class="calendar-panel">
    <div class="calendar-panel__head">
      <div class="calendar-panel__base">
        <span class="calendar-panel__label">第 1 周起始日：</span>
        <template v-if="calendar">
          <strong>{{ baseText }}</strong>
          <el-tag v-if="!calendar.configured" type="warning" size="small" effect="light">未配置</el-tag>
          <el-tag v-else-if="!isMonday" type="warning" size="small" effect="light">非周一</el-tag>
          <span class="calendar-panel__hint">共 {{ calendar.totalWeeks }} 周</span>
        </template>
      </div>
      <el-button size="small" type="primary" plain :icon="EditPen" @click="openSet">设置</el-button>
    </div>

    <el-alert
      v-if="calendar && !calendar.configured"
      type="warning"
      :closable="false"
      show-icon
      class="calendar-panel__alert"
      title="尚未配置第 1 周起始日，当前周次暂按开学日推算，配置后开放计划/占用口径将随之调整"
    />

    <div class="calendar-panel__section">
      <div class="calendar-panel__section-title">周次 ↔ 日期</div>
      <el-table :data="weeks" border stripe size="small" max-height="320" :row-class-name="weekRowClass">
        <el-table-column prop="week" label="周次" width="90">
          <template #default="{ row }">第 {{ row.week }} 周</template>
        </el-table-column>
        <el-table-column prop="startDate" label="起始日" width="120" />
        <el-table-column prop="endDate" label="结束日" width="120" />
        <el-table-column label="备注" min-width="180">
          <template #default="{ row }">
            <template v-if="row.holidayNames.length">
              <el-tag v-for="name in row.holidayNames" :key="name" type="danger" size="small" effect="light" class="calendar-panel__tag">
                {{ name }}
              </el-tag>
            </template>
            <span v-else class="calendar-panel__muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="calendar-panel__section">
      <div class="calendar-panel__section-title">
        节假日
        <span class="calendar-panel__muted">（按日期区间批量标记，逐日一行）</span>
      </div>
      <div class="calendar-panel__form">
        <el-date-picker
          v-model="holidayRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :disabled-date="outsideSemester"
          style="width: 260px"
        />
        <el-input v-model="holidayName" placeholder="节假日名称，如 国庆节" maxlength="30" style="width: 200px" />
        <el-button type="primary" plain :disabled="!canAddHoliday" :loading="adding" @click="handleAddHoliday">标记节假日</el-button>
      </div>

      <el-table :data="holidays" border stripe size="small" max-height="240">
        <el-table-column prop="holidayDate" label="日期" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDeleteHoliday(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && holidays.length === 0" class="calendar-panel__muted calendar-panel__empty">暂无节假日记录</div>
    </div>

    <el-dialog v-model="setVisible" title="设置第 1 周起始日" width="420px">
      <el-form label-width="110px">
        <el-form-item label="第 1 周起始日">
          <el-date-picker v-model="week1StartDate" type="date" value-format="YYYY-MM-DD" :disabled-date="outsideSemester" placeholder="选择日期" />
        </el-form-item>
      </el-form>
      <div class="calendar-panel__muted">
        周次口径 = 该日起每 7 天为一周；若所选日期非周一，系统会按该日起算（仅提示，不拦截）。
      </div>
      <template #footer>
        <el-button @click="setVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSet">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { EditPen } from '@element-plus/icons-vue';
import { apiCreateHoliday, apiDeleteHoliday, apiGetSemesterCalendar, apiListHolidays, apiSetSemesterCalendar } from '@/api/semester';
import type { IHoliday, ISemester, ISemesterCalendar, ISemesterCalendarWeek } from '@/types/api';

const props = defineProps<{ semester: ISemester }>();
const emit = defineEmits<{ changed: [] }>();

const loading = ref(false);
const calendar = ref<ISemesterCalendar | null>(null);
const holidays = ref<IHoliday[]>([]);
const setVisible = ref(false);
const week1StartDate = ref('');
const saving = ref(false);
const holidayRange = ref<[string, string] | null>(null);
const holidayName = ref('');
const adding = ref(false);

const weeks = computed<ISemesterCalendarWeek[]>(() => calendar.value?.weeks ?? []);
const baseText = computed(() => {
  if (!calendar.value) return '';
  const date = calendar.value.week1StartDate;
  return `${date}${calendar.value.configured ? '' : `（按开学日 ${props.semester.startDate} 计）`}`;
});
const isMonday = computed(() => {
  if (!calendar.value) return true;
  const [y, m, d] = calendar.value.week1StartDate.split('-').map(Number);
  return new Date(y, m - 1, d).getDay() === 1;
});
const canAddHoliday = computed(() => Boolean(holidayRange.value?.[0] && holidayRange.value?.[1] && holidayName.value.trim()));

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [calendarData, holidayData] = await Promise.all([
      apiGetSemesterCalendar(props.semester.id),
      apiListHolidays(props.semester.id),
    ]);
    calendar.value = calendarData;
    holidays.value = holidayData;
  } finally {
    loading.value = false;
  }
}

function parseLocal(date: string): Date {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function outsideSemester(date: Date): boolean {
  return date < parseLocal(props.semester.startDate) || date > parseLocal(props.semester.endDate);
}

function weekRowClass({ row }: { row: ISemesterCalendarWeek }): string {
  if (props.semester.status !== 'ACTIVE') return '';
  const today = new Date();
  const now = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  return now >= row.startDate && now <= row.endDate ? 'calendar-panel__row--current' : '';
}

function openSet(): void {
  week1StartDate.value = calendar.value?.week1StartDate ?? props.semester.startDate;
  setVisible.value = true;
}

async function handleSet(): Promise<void> {
  if (!week1StartDate.value) {
    ElMessage.warning('请选择第 1 周起始日');
    return;
  }
  const [y, m, d] = week1StartDate.value.split('-').map(Number);
  if (new Date(y, m - 1, d).getDay() !== 1) {
    ElMessage.warning('所选日期非周一，周次映射将按该日起算');
  }
  saving.value = true;
  try {
    await apiSetSemesterCalendar(props.semester.id, week1StartDate.value);
    ElMessage.success('周历已更新');
    setVisible.value = false;
    await load();
    emit('changed');
  } finally {
    saving.value = false;
  }
}

async function handleAddHoliday(): Promise<void> {
  if (!holidayRange.value) return;
  adding.value = true;
  try {
    const count = await apiCreateHoliday({
      startDate: holidayRange.value[0],
      endDate: holidayRange.value[1],
      name: holidayName.value.trim(),
    });
    ElMessage.success(`已标记 ${count} 天节假日`);
    holidayRange.value = null;
    holidayName.value = '';
    await load();
    emit('changed');
  } finally {
    adding.value = false;
  }
}

async function handleDeleteHoliday(row: IHoliday): Promise<void> {
  await ElMessageBox.confirm(`确定删除 ${row.holidayDate}「${row.name}」吗？`, '删除确认', { type: 'warning' });
  await apiDeleteHoliday(row.id);
  ElMessage.success('删除成功');
  await load();
  emit('changed');
}

watch(() => props.semester.id, () => {
  void load();
});

onMounted(() => {
  void load();
});
</script>

<style scoped lang="scss">
.calendar-panel {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-3);
  }

  &__base {
    display: flex;
    align-items: center;
    gap: var(--space-2);
  }

  &__label {
    color: var(--el-text-color-secondary);
  }

  &__hint {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__alert {
    margin-bottom: var(--space-3);
  }

  &__section {
    margin-bottom: var(--space-4);
  }

  &__section-title {
    font-weight: 600;
    margin-bottom: var(--space-2);
  }

  &__form {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-3);
  }

  &__tag {
    margin-right: var(--space-1);
  }

  &__muted {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__empty {
    padding: var(--space-3) 0;
    text-align: center;
  }

  :deep(.calendar-panel__row--current) {
    background: var(--el-color-primary-light-9);
  }
}
</style>
