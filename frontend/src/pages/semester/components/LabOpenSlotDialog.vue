<template>
  <el-dialog :model-value="modelValue" :title="editing ? '编辑开放时段' : '添加开放时段'" width="520px" @update:model-value="emit('update:modelValue', $event)">
    <div class="slot-dialog__lab">{{ labName ? `${labCode} · ${labName}` : labCode }}</div>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="星期" prop="dayOfWeek">
        <el-select v-model="form.dayOfWeek" style="width: 160px">
          <el-option v-for="(label, index) in WEEKDAY_LABELS" :key="label" :label="`周${label}`" :value="index + 1" />
        </el-select>
      </el-form-item>
      <el-form-item label="节次" prop="startSection">
        <div class="slot-dialog__sections">
          <el-select v-model="form.startSection" style="width: 110px">
            <el-option v-for="section in SECTION_OPTIONS" :key="section" :label="`第 ${section} 节`" :value="section" />
          </el-select>
          <span>至</span>
          <el-select v-model="form.endSection" style="width: 110px">
            <el-option v-for="section in SECTION_OPTIONS" :key="section" :label="`第 ${section} 节`" :value="section" />
          </el-select>
        </div>
      </el-form-item>
      <el-form-item label="周次范围" prop="weekRange">
        <el-input v-model="form.weekRange" placeholder="如 1-16 / 1,3,5 / 8" style="width: 220px" />
      </el-form-item>
      <el-form-item label="周类型" prop="weekType">
        <el-radio-group v-model="form.weekType">
          <el-radio-button value="ALL">全周</el-radio-button>
          <el-radio-button value="ODD">单周</el-radio-button>
          <el-radio-button value="EVEN">双周</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="开放对象" prop="audience">
        <el-select v-model="form.audience" style="width: 160px">
          <el-option v-for="item in AUDIENCE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="开放" inactive-text="停用" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="可选，如 晚间开放" maxlength="60" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { apiCreateLabOpening, apiUpdateLabOpening } from '@/api/labOpening';
import { AUDIENCE_OPTIONS, SECTION_OPTIONS, WEEKDAY_LABELS } from '@/constants';
import type { ILabOpening, TWeekType } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  semesterId: number;
  labCode: string;
  labName?: string;
  editing: ILabOpening | null;
}>();
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>();

const formRef = ref<FormInstance>();
const saving = ref(false);
const form = reactive({
  dayOfWeek: 1,
  startSection: 1,
  endSection: 2,
  weekRange: '1-16',
  weekType: 'ALL' as TWeekType,
  audience: '全校',
  enabled: 1,
  remark: '',
});

const rules: FormRules = {
  dayOfWeek: [{ required: true, message: '请选择星期', trigger: 'change' }],
  startSection: [{ required: true, message: '请选择起始节次', trigger: 'change' }],
  endSection: [{ required: true, message: '请选择结束节次', trigger: 'change' }],
  weekRange: [{ required: true, message: '请输入周次范围', trigger: 'blur' }],
  weekType: [{ required: true, message: '请选择周类型', trigger: 'change' }],
  audience: [{ required: true, message: '请选择开放对象', trigger: 'change' }],
};

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    if (props.editing) {
      Object.assign(form, {
        dayOfWeek: props.editing.dayOfWeek,
        startSection: props.editing.startSection,
        endSection: props.editing.endSection,
        weekRange: props.editing.weekRange,
        weekType: props.editing.weekType,
        audience: props.editing.audience,
        enabled: props.editing.enabled,
        remark: props.editing.remark ?? '',
      });
    } else {
      Object.assign(form, { dayOfWeek: 1, startSection: 1, endSection: 2, weekRange: '1-16', weekType: 'ALL', audience: '全校', enabled: 1, remark: '' });
    }
  },
);

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (form.endSection < form.startSection) {
    ElMessage.warning('结束节次不能小于起始节次');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      labCode: props.labCode,
      semesterId: props.semesterId,
      dayOfWeek: form.dayOfWeek,
      startSection: form.startSection,
      endSection: form.endSection,
      weekRange: form.weekRange.trim(),
      weekType: form.weekType,
      audience: form.audience,
      enabled: form.enabled,
      remark: form.remark.trim() || undefined,
    };
    if (props.editing) {
      await apiUpdateLabOpening(props.editing.id, payload);
    } else {
      await apiCreateLabOpening(payload);
    }
    ElMessage.success('保存成功');
    emit('update:modelValue', false);
    emit('saved');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.slot-dialog {
  &__lab {
    margin-bottom: var(--space-3);
    font-weight: 600;
  }

  &__sections {
    display: flex;
    align-items: center;
    gap: var(--space-2);
  }
}
</style>
