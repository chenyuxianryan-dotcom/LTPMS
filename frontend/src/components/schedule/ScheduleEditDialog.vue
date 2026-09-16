<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑课表行"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <div v-if="item" class="schedule-edit__summary">
      <div class="schedule-edit__course">
        <span class="schedule-edit__code">{{ item.courseCode }}</span>
        <span class="schedule-edit__name">{{ item.courseName }}</span>
      </div>
      <el-link
        v-if="item.revisionCount > 0"
        type="warning"
        :underline="false"
        @click="emit('revisions')"
      >
        查看修订记录（{{ item.revisionCount }}）
      </el-link>
    </div>

    <el-form :model="form" label-width="88px">
      <el-form-item label="上课地点">
        <el-select v-model="form.labCode" filterable placeholder="选择实验室" style="width: 100%">
          <el-option
            v-for="lab in labs"
            :key="lab.labCode"
            :label="`${lab.labCode} ${lab.labName}`"
            :value="lab.labCode"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="上课班级">
        <el-input v-model="form.classNames" placeholder="如：2023级软件工程1班" />
      </el-form-item>
      <el-form-item label="选课人数">
        <el-input-number v-model="form.studentCount" :min="1" :max="500" style="width: 100%" />
      </el-form-item>
      <el-form-item label="上课时间">
        <el-input v-model="form.rawScheduleText" placeholder="如：(1-16周) 周四第1-4节" />
        <div class="schedule-edit__hint">保存后系统将按此文本重新解析时间槽</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { apiGetLabOpeningOverview } from '@/api/labOpening';
import { apiUpdateSchedule } from '@/api/schedule';
import type { IMyScheduleItem } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  item: IMyScheduleItem | null;
  semesterId: number | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'revisions'): void;
  (e: 'success'): void;
}>();

const saving = ref(false);
const labs = ref<{ labCode: string; labName: string }[]>([]);
const form = reactive({
  labCode: '',
  classNames: '',
  studentCount: 1,
  rawScheduleText: '',
});

async function handleOpen(): Promise<void> {
  if (!props.item) return;
  form.labCode = props.item.labCode;
  form.classNames = props.item.classNames ?? '';
  form.studentCount = props.item.studentCount ?? 1;
  form.rawScheduleText = props.item.rawScheduleText ?? '';
  if (labs.value.length === 0 && props.semesterId) {
    // 实验室清单经「开放总览」获取：该接口对教师开放（/laboratories 仅管理员）
    const overview = await apiGetLabOpeningOverview(props.semesterId);
    labs.value = overview.labs.map((lab) => ({ labCode: lab.labCode, labName: lab.labName }));
  }
}

async function handleSave(): Promise<void> {
  if (!props.item) return;
  saving.value = true;
  try {
    await apiUpdateSchedule(props.item.id, {
      labCode: form.labCode,
      classNames: form.classNames,
      studentCount: form.studentCount,
      rawScheduleText: form.rawScheduleText,
    });
    ElMessage.success('已保存');
    emit('update:modelValue', false);
    emit('success');
  } catch {
    // 错误由 request 拦截器统一提示
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.schedule-edit {
  &__summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
    padding: var(--space-2) var(--space-3);
    margin-bottom: var(--space-4);
    background: var(--bg-subtle);
    border-radius: var(--radius-md);
  }

  &__course {
    display: flex;
    align-items: baseline;
    gap: var(--space-2);
    min-width: 0;
  }

  &__code {
    font-family: var(--font-mono);
    font-size: 12px;
    color: var(--text-tertiary);
    flex-shrink: 0;
  }

  &__name {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__hint {
    font-size: 12px;
    color: var(--text-quaternary);
    line-height: 18px;
  }
}
</style>
