<template>
  <el-dialog :model-value="modelValue" title="提交任务成果" width="560px" @update:model-value="emit('update:modelValue', $event)">
    <template v-if="task">
      <div class="submit-dialog__meta">
        <b>{{ task.title }}</b>
        <span v-if="task.deadline" :class="{ 'submit-dialog__overdue': task.overdue }">截止 {{ fmtDateTime(task.deadline) }}</span>
      </div>
      <el-form label-width="90px">
        <el-form-item label="提交内容" required>
          <el-input v-model="content" type="textarea" :rows="5" maxlength="4000" show-word-limit placeholder="成果说明 / 结论 / 遇到的问题…" />
        </el-form-item>
        <el-form-item label="附带数据">
          <el-select v-model="datasetId" clearable placeholder="可选" style="width: 46%" @change="onDatasetChange">
            <el-option v-for="d in datasets" :key="d.id" :value="d.id" :label="d.datasetName" />
          </el-select>
          <el-select v-model="versionId" :disabled="!datasetId" placeholder="版本" style="width: 46%; margin-left: 8px">
            <el-option v-for="v in versions" :key="v.id" :value="v.id" :label="`v${v.versionNo} · ${v.originalFilename}`" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="history.length" class="submit-dialog__history">
        <div v-for="h in history" :key="h.id" class="submit-dialog__history-item">
          {{ fmtFullDateTime(h.submittedAt) }} 提交
          <template v-if="h.reviewedAt"> → {{ h.reviewComment || '已验收' }}</template>
          <template v-else> → 待验收</template>
        </div>
      </div>
    </template>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="!content.trim()" :loading="saving" @click="handleSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { apiListDatasets, apiListSubmissions, apiListVersions, apiSubmitTask } from '@/api/project';
import type { IDataset, IDatasetVersion, IProjectTask, ITaskSubmission } from '@/types/api';
import { fmtDateTime, fmtFullDateTime } from '../projectUtils';

const props = defineProps<{ modelValue: boolean; projectId: number | null; task: IProjectTask | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'done'): void }>();

const content = ref('');
const datasetId = ref<number | null>(null);
const versionId = ref<number | null>(null);
const saving = ref(false);
const datasets = ref<IDataset[]>([]);
const versions = ref<IDatasetVersion[]>([]);
const history = reactive<ITaskSubmission[]>([]);

watch(
  () => props.modelValue,
  async (open) => {
    if (!open || !props.task || props.projectId == null) return;
    content.value = '';
    datasetId.value = props.task.datasetId ?? null;
    versionId.value = null;
    history.splice(0, history.length, ...(await apiListSubmissions(props.task.id)));
    datasets.value = await apiListDatasets(props.projectId);
    if (datasetId.value) await onDatasetChange(datasetId.value);
  },
);

async function onDatasetChange(id: number | null): Promise<void> {
  versionId.value = null;
  versions.value = id ? await apiListVersions(id) : [];
}

async function handleSubmit(): Promise<void> {
  if (!props.task) return;
  saving.value = true;
  try {
    await apiSubmitTask(props.task.id, { content: content.value.trim(), datasetVersionId: versionId.value });
    ElMessage.success('已提交，等待教师验收');
    emit('update:modelValue', false);
    emit('done');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.submit-dialog {
  &__meta {
    display: flex;
    gap: var(--space-3);
    align-items: baseline;
    margin-bottom: var(--space-4);
  }

  &__overdue {
    font-size: 12px;
    color: var(--el-color-danger);
  }

  &__history {
    padding: var(--space-2) var(--space-3);
    margin-top: var(--space-2);
    font-size: 12px;
    color: var(--text-tertiary);
    background: var(--el-fill-color-lighter);
    border-radius: 4px;
  }
}
</style>
