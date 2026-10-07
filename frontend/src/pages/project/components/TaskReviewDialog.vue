<template>
  <el-dialog :model-value="modelValue" title="任务验收" width="560px" @update:model-value="emit('update:modelValue', $event)">
    <template v-if="task">
      <div class="review-dialog__meta">
        <div><b>{{ task.title }}</b> · {{ task.assigneeName }}</div>
        <div class="review-dialog__desc">{{ task.description || '（无任务说明）」' }}</div>
      </div>
      <el-descriptions v-if="submission" :column="1" border size="small" class="review-dialog__sub">
        <el-descriptions-item label="提交时间">{{ fmtFullDateTime(submission.submittedAt) }}</el-descriptions-item>
        <el-descriptions-item label="提交内容">{{ submission.content }}</el-descriptions-item>
        <el-descriptions-item v-if="submission.datasetVersionId" label="附带数据版本">
          数据集版本 #{{ submission.datasetVersionId }}（可在「数据」页对照查看）
        </el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px" class="review-dialog__form">
        <el-form-item label="验收结论">
          <el-radio-group v-model="decision">
            <el-radio value="ACCEPT">通过验收</el-radio>
            <el-radio value="REJECT">驳回重做</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见">
          <el-input v-model="comment" type="textarea" :rows="3" maxlength="500" :placeholder="decision === 'REJECT' ? '驳回必填：说明需要改进的点' : '可选'" />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleReview">提交结论</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { apiReviewTask } from '@/api/project';
import type { IProjectTask, ITaskSubmission } from '@/types/api';
import { fmtFullDateTime } from '../projectUtils';

const props = defineProps<{
  modelValue: boolean;
  task: IProjectTask | null;
  submission: ITaskSubmission | null;
  /** 收件箱「通过/驳回」入口预选结论（T02）；缺省 ACCEPT，约束不变 */
  initialDecision?: 'ACCEPT' | 'REJECT';
}>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'done'): void }>();

const decision = ref<'ACCEPT' | 'REJECT'>('ACCEPT');
const comment = ref('');
const saving = ref(false);

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      decision.value = props.initialDecision ?? 'ACCEPT';
      comment.value = '';
    }
  },
);

async function handleReview(): Promise<void> {
  if (!props.task) return;
  if (decision.value === 'REJECT' && !comment.value.trim()) {
    ElMessage.warning('驳回时必须填写意见');
    return;
  }
  saving.value = true;
  try {
    await apiReviewTask(props.task.id, { decision: decision.value, comment: comment.value || undefined });
    ElMessage.success(decision.value === 'ACCEPT' ? '已验收通过' : '已驳回并通知学生');
    emit('update:modelValue', false);
    emit('done');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.review-dialog {
  &__meta {
    margin-bottom: var(--space-3);
  }

  &__desc {
    margin-top: var(--space-1);
    font-size: 13px;
    color: var(--text-secondary);
  }

  &__sub {
    margin-bottom: var(--space-4);
  }
}
</style>
