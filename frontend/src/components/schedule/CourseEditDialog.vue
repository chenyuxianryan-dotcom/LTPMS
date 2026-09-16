<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑课程信息"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <div v-if="course" class="course-edit__summary">
      <div class="course-edit__course">
        <span class="course-edit__code">{{ course.courseCode }}</span>
        <span class="course-edit__meta">{{ course.teacherName }} · {{ course.semesterName }}</span>
      </div>
      <el-link
        v-if="(revisionCount ?? 0) > 0"
        type="warning"
        :underline="false"
        @click="emit('revisions')"
      >
        查看修订记录（{{ revisionCount }}）
      </el-link>
    </div>

    <el-form :model="form" label-width="88px">
      <el-form-item label="课程名称">
        <el-input v-model="form.courseName" placeholder="课程名称" />
      </el-form-item>
      <el-form-item label="面向专业">
        <el-input v-model="form.major" placeholder="如：软件工程" />
      </el-form-item>
      <el-form-item label="实验学时">
        <el-input-number v-model="form.totalHours" :min="0.5" :step="0.5" :max="500" style="width: 100%" />
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
import { apiUpdateCourse } from '@/api/course';
import type { ICourse } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  course: ICourse | null;
  revisionCount?: number;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'revisions'): void;
  (e: 'success'): void;
}>();

const saving = ref(false);
const form = reactive({
  courseName: '',
  major: '',
  totalHours: 0,
});

function handleOpen(): void {
  if (!props.course) return;
  form.courseName = props.course.courseName ?? '';
  form.major = props.course.major ?? '';
  form.totalHours = props.course.totalHours ?? 0;
}

async function handleSave(): Promise<void> {
  if (!props.course) return;
  saving.value = true;
  try {
    await apiUpdateCourse(props.course.id, {
      courseName: form.courseName,
      major: form.major,
      totalHours: form.totalHours,
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
.course-edit {
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

  &__meta {
    font-size: 13px;
    color: var(--text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
