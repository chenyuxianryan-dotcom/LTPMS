<template>
  <el-dialog :model-value="modelValue" title="从上学期复制开放计划" width="560px" @update:model-value="emit('update:modelValue', $event)">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="复制为覆盖式：目标学期对应实验室的既有计划会被源学期计划覆盖；重复复制结果一致（幂等）"
      class="copy-dialog__alert"
    />

    <el-form label-width="110px">
      <el-form-item label="源学期">
        <el-select v-model="fromSemesterId" placeholder="选择要复制的学期" style="width: 240px">
          <el-option v-for="item in sourceOptions" :key="item.id" :label="item.semesterName" :value="item.id" />
        </el-select>
        <span v-if="!sourceOptions.length" class="copy-dialog__muted">暂无可复制的其他学期</span>
      </el-form-item>
      <el-form-item label="实验室范围">
        <el-radio-group v-model="mode">
          <el-radio-button value="auto">源学期已配置的全部实验室</el-radio-button>
          <el-radio-button value="pick">指定实验室</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="mode === 'pick'" label="实验室">
        <el-select v-model="labCodes" multiple collapse-tags collapse-tags-tooltip filterable placeholder="选择实验室" style="width: 320px">
          <el-option v-for="lab in labs" :key="lab.labCode" :label="`${lab.labCode} ${lab.labName}`" :value="lab.labCode" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!fromSemesterId" @click="handleSave">开始复制</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { apiCopyLabOpenings } from '@/api/labOpening';
import type { ILaboratory, ISemester } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  semesterId: number;
  semesters: ISemester[];
  labs: ILaboratory[];
}>();
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>();

const fromSemesterId = ref<number | null>(null);
const mode = ref<'auto' | 'pick'>('auto');
const labCodes = ref<string[]>([]);
const saving = ref(false);

const sourceOptions = computed(() => props.semesters.filter((item) => item.id !== props.semesterId));

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    const previous = sourceOptions.value.find((item) => item.status === 'ENDED');
    fromSemesterId.value = previous?.id ?? sourceOptions.value[0]?.id ?? null;
    mode.value = 'auto';
    labCodes.value = [];
  },
);

async function handleSave(): Promise<void> {
  if (!fromSemesterId.value) return;
  if (mode.value === 'pick' && !labCodes.value.length) {
    ElMessage.warning('请选择要复制的实验室');
    return;
  }
  saving.value = true;
  try {
    const count = await apiCopyLabOpenings({
      fromSemesterId: fromSemesterId.value,
      toSemesterId: props.semesterId,
      labCodes: mode.value === 'pick' ? labCodes.value : undefined,
    });
    ElMessage.success(`已复制 ${count} 间实验室的开放计划`);
    emit('update:modelValue', false);
    emit('saved');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.copy-dialog {
  &__alert {
    margin-bottom: var(--space-3);
  }

  &__muted {
    margin-left: var(--space-2);
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
</style>
