<template>
  <el-dialog :model-value="modelValue" title="批量设置开放时段" width="720px" @update:model-value="emit('update:modelValue', $event)">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="覆盖式设置：将先清空选中实验室本学期的全部开放时段，再写入以下时段"
      class="batch-dialog__alert"
    />

    <el-form label-width="90px">
      <el-form-item label="实验室范围">
        <el-radio-group v-model="mode">
          <el-radio-button value="all">全部实验室（{{ labs.length }} 间）</el-radio-button>
          <el-radio-button value="pick">指定实验室</el-radio-button>
        </el-radio-group>
        <el-select
          v-if="mode === 'pick'"
          v-model="labCodes"
          multiple
          collapse-tags
          collapse-tags-tooltip
          filterable
          placeholder="选择实验室"
          class="batch-dialog__labs"
        >
          <el-option v-for="lab in labs" :key="lab.labCode" :label="`${lab.labCode} ${lab.labName}`" :value="lab.labCode" />
        </el-select>
        <span v-if="mode === 'pick'" class="batch-dialog__count">已选 {{ labCodes.length }} 间</span>
      </el-form-item>

      <el-form-item label="开放对象">
        <el-select v-model="audience" style="width: 160px">
          <el-option v-for="item in AUDIENCE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
        <el-input v-model="remark" placeholder="备注（可选）" maxlength="60" class="batch-dialog__remark" />
      </el-form-item>
    </el-form>

    <div class="batch-dialog__slots-head">
      <span class="batch-dialog__title">时段列表</span>
      <div>
        <el-button size="small" plain @click="fillWorkdays">按第 1 行生成周一~周五</el-button>
        <el-button size="small" plain @click="addSlot">添加时段</el-button>
      </div>
    </div>

    <el-table :data="slots" border size="small">
      <el-table-column label="星期" width="110">
        <template #default="{ row }">
          <el-select v-model="row.dayOfWeek" size="small">
            <el-option v-for="(label, index) in WEEKDAY_LABELS" :key="label" :label="`周${label}`" :value="index + 1" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="节次" width="220">
        <template #default="{ row }">
          <div class="batch-dialog__sections">
            <el-select v-model="row.startSection" size="small" style="width: 95px">
              <el-option v-for="section in SECTION_OPTIONS" :key="section" :label="`第 ${section} 节`" :value="section" />
            </el-select>
            <span>至</span>
            <el-select v-model="row.endSection" size="small" style="width: 95px">
              <el-option v-for="section in SECTION_OPTIONS" :key="section" :label="`第 ${section} 节`" :value="section" />
            </el-select>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="周次范围" width="150">
        <template #default="{ row }">
          <el-input v-model="row.weekRange" size="small" placeholder="如 1-16" />
        </template>
      </el-table-column>
      <el-table-column label="周类型" width="180">
        <template #default="{ row }">
          <el-select v-model="row.weekType" size="small">
            <el-option label="全周" value="ALL" />
            <el-option label="单周" value="ODD" />
            <el-option label="双周" value="EVEN" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ $index }">
          <el-button link type="danger" :disabled="slots.length <= 1" @click="slots.splice($index, 1)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">确认覆盖设置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { apiBatchLabOpenings, type ILabOpenSlotPayload } from '@/api/labOpening';
import { AUDIENCE_OPTIONS, SECTION_OPTIONS, WEEKDAY_LABELS } from '@/constants';
import type { ILaboratory, TWeekType } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  semesterId: number;
  labs: ILaboratory[];
  selectedCodes: string[];
}>();
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>();

const mode = ref<'all' | 'pick'>('all');
const labCodes = ref<string[]>([]);
const audience = ref('全校');
const remark = ref('');
const slots = ref<ILabOpenSlotPayload[]>([]);
const saving = ref(false);

function defaultSlot(): ILabOpenSlotPayload {
  return { dayOfWeek: 1, startSection: 1, endSection: 8, weekRange: '1-16', weekType: 'ALL' };
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    mode.value = props.selectedCodes.length ? 'pick' : 'all';
    labCodes.value = [...props.selectedCodes];
    audience.value = '全校';
    remark.value = '';
    slots.value = Array.from({ length: 5 }, (_, index) => ({ ...defaultSlot(), dayOfWeek: index + 1 }));
  },
);

function addSlot(): void {
  slots.value.push(defaultSlot());
}

function fillWorkdays(): void {
  const base = slots.value[0] ?? defaultSlot();
  slots.value = Array.from({ length: 5 }, (_, index) => ({ ...base, dayOfWeek: index + 1 }));
}

async function handleSave(): Promise<void> {
  const targets = mode.value === 'all' ? props.labs.map((lab) => lab.labCode) : labCodes.value;
  if (!targets.length) {
    ElMessage.warning('请选择要设置的实验室');
    return;
  }
  if (!slots.value.length) {
    ElMessage.warning('请至少保留一条时段');
    return;
  }
  for (const slot of slots.value) {
    if (!slot.weekRange.trim()) {
      ElMessage.warning('周次范围不能为空');
      return;
    }
    if (slot.endSection < slot.startSection) {
      ElMessage.warning('结束节次不能小于起始节次');
      return;
    }
  }
  saving.value = true;
  try {
    const count = await apiBatchLabOpenings({
      semesterId: props.semesterId,
      labCodes: targets,
      slots: slots.value.map((slot) => ({ ...slot, weekRange: slot.weekRange.trim(), weekType: slot.weekType as TWeekType })),
      audience: audience.value,
      remark: remark.value.trim() || undefined,
    });
    ElMessage.success(`已为 ${targets.length} 间实验室写入 ${count} 条开放时段`);
    emit('update:modelValue', false);
    emit('saved');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.batch-dialog {
  &__alert {
    margin-bottom: var(--space-3);
  }

  &__labs {
    margin-left: var(--space-2);
    width: 260px;
  }

  &__count {
    margin-left: var(--space-2);
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__remark {
    margin-left: var(--space-3);
    width: 200px;
  }

  &__slots-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: var(--space-2) 0;
  }

  &__title {
    font-weight: 600;
  }

  &__sections {
    display: flex;
    align-items: center;
    gap: var(--space-1);
  }
}
</style>
