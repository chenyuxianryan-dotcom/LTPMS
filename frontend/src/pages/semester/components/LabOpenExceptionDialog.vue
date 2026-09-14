<template>
  <el-dialog :model-value="modelValue" title="开放例外日（临时闭馆）" width="680px" @update:model-value="emit('update:modelValue', $event)">
    <div class="exception-dialog__form">
      <el-select v-model="labCode" filterable placeholder="选择实验室" style="width: 180px">
        <el-option v-for="lab in labs" :key="lab.labCode" :label="`${lab.labCode} ${lab.labName}`" :value="lab.labCode" />
      </el-select>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 250px"
      />
      <el-input v-model="reason" placeholder="事由，如 设备检修" maxlength="60" style="width: 170px" />
      <el-button type="primary" plain :disabled="!canAdd" :loading="adding" @click="handleAdd">添加</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe size="small" max-height="320">
      <el-table-column prop="labCode" label="实验室" width="110" />
      <el-table-column label="闭馆区间" width="200">
        <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
      </el-table-column>
      <el-table-column label="天数" width="70" align="right">
        <template #default="{ row }">{{ daysOf(row.startDate, row.endDate) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="事由" min-width="140">
        <template #default="{ row }">{{ row.reason || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="!loading && records.length === 0" class="exception-dialog__empty">本学期暂无例外日</div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiCreateLabOpenException, apiDeleteLabOpenException, apiListLabOpenExceptions } from '@/api/labOpening';
import type { ILaboratory, ILabOpenException } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  semesterId: number;
  labs: ILaboratory[];
}>();
const emit = defineEmits<{ 'update:modelValue': [boolean]; changed: [] }>();

const loading = ref(false);
const records = ref<ILabOpenException[]>([]);
const labCode = ref('');
const range = ref<[string, string] | null>(null);
const reason = ref('');
const adding = ref(false);

const canAdd = computed(() => Boolean(labCode.value && range.value?.[0] && range.value?.[1]));

async function load(): Promise<void> {
  loading.value = true;
  try {
    records.value = await apiListLabOpenExceptions({ semesterId: props.semesterId });
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    labCode.value = '';
    range.value = null;
    reason.value = '';
    void load();
  },
);

function daysOf(start: string, end: string): number {
  const [y1, m1, d1] = start.split('-').map(Number);
  const [y2, m2, d2] = end.split('-').map(Number);
  return Math.round((new Date(y2, m2 - 1, d2).getTime() - new Date(y1, m1 - 1, d1).getTime()) / 86400000) + 1;
}

async function handleAdd(): Promise<void> {
  if (!range.value) return;
  adding.value = true;
  try {
    await apiCreateLabOpenException({
      labCode: labCode.value,
      semesterId: props.semesterId,
      startDate: range.value[0],
      endDate: range.value[1],
      reason: reason.value.trim() || undefined,
    });
    ElMessage.success('已添加例外日');
    range.value = null;
    reason.value = '';
    await load();
    emit('changed');
  } finally {
    adding.value = false;
  }
}

async function handleDelete(row: ILabOpenException): Promise<void> {
  await ElMessageBox.confirm(`确定删除 ${row.labCode} 的例外日（${row.startDate} ~ ${row.endDate}）吗？`, '删除确认', { type: 'warning' });
  await apiDeleteLabOpenException(row.id);
  ElMessage.success('删除成功');
  await load();
  emit('changed');
}
</script>

<style scoped lang="scss">
.exception-dialog {
  &__form {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-3);
  }

  &__empty {
    padding: var(--space-3) 0;
    text-align: center;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
</style>
