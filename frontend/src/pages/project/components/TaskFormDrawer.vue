<template>
  <el-drawer :model-value="modelValue" :title="editTask ? '编辑任务' : '派发任务'" size="480px" @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="任务标题" prop="title">
        <el-input v-model="form.title" maxlength="128" placeholder="如：完成鼓点数据集清洗并撰写说明" />
      </el-form-item>
      <el-form-item label="任务说明">
        <el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" />
      </el-form-item>
      <el-form-item v-if="!editTask" label="执行人" prop="assigneeStudentIds">
        <el-select v-model="form.assigneeStudentIds" multiple filterable placeholder="可多选；多人将复制为多条独立任务" style="width: 100%">
          <el-option v-for="m in members" :key="m.studentId" :value="m.studentId" :label="`${m.realName}（${m.studentNo}）`" />
        </el-select>
      </el-form-item>
      <el-form-item v-else label="执行人">
        <el-input :model-value="editTask.assigneeName" disabled />
      </el-form-item>
      <el-form-item label="截止时间">
        <el-date-picker
          v-model="form.deadline"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="可选"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="关联数据">
        <el-select v-model="form.datasetId" clearable placeholder="可选，关联本课题数据集" style="width: 100%">
          <el-option v-for="d in datasets" :key="d.id" :value="d.id" :label="`${d.datasetName}（v${d.latestVersion}）`" />
        </el-select>
      </el-form-item>
      <!-- T04：关联实验（本容器目录行）；独立课题无目录语义，不显示此项 -->
      <el-form-item v-if="experiments && experiments.length" label="关联实验">
        <el-select v-model="form.experimentId" clearable placeholder="可选，挂靠实验目录行" style="width: 100%">
          <el-option v-for="e in experiments" :key="e.id" :value="e.id" :label="`${e.experimentCode} · ${e.experimentName}`" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ editTask ? '保存' : '派发' }}</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { apiCreateTasks, apiUpdateTask } from '@/api/project';
import type { IDataset, IExperiment, IProjectMember, IProjectTask } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  projectId: number;
  members: IProjectMember[];
  datasets: IDataset[];
  editTask?: IProjectTask | null;
  /** T04：本容器实验目录（空=独立课题，隐藏「关联实验」项） */
  experiments?: IExperiment[];
  /** T04：目录行「布置任务」入口预填（标题=实验名、默认选中该实验） */
  prefill?: { title?: string; experimentId?: number } | null;
}>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'saved'): void }>();

const formRef = ref<FormInstance>();
const saving = ref(false);
const form = reactive<{
  title: string;
  description: string;
  assigneeStudentIds: number[];
  deadline: string | null;
  datasetId: number | null;
  experimentId: number | null;
}>({ title: '', description: '', assigneeStudentIds: [], deadline: null, datasetId: null, experimentId: null });

const rules: FormRules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
  assigneeStudentIds: [{ required: true, message: '请至少选择一名执行人', trigger: 'change' }],
};

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    form.title = props.editTask?.title ?? props.prefill?.title ?? '';
    form.description = props.editTask?.description ?? '';
    form.assigneeStudentIds = [];
    form.deadline = props.editTask?.deadline ?? null;
    form.datasetId = props.editTask?.datasetId ?? null;
    form.experimentId = props.editTask
      ? props.editTask.experimentId ?? null
      : props.prefill?.experimentId ?? null;
  },
);

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  const hasCatalog = !!(props.experiments && props.experiments.length);
  try {
    if (props.editTask) {
      await apiUpdateTask(props.editTask.id, {
        title: form.title,
        description: form.description,
        deadline: form.deadline,
        datasetId: form.datasetId,
        // 有目录才传：null=不动 / 0=清空 / >0=改挂；独立课题不传（后端一律 400 兜底）
        experimentId: hasCatalog ? form.experimentId ?? 0 : undefined,
      });
      ElMessage.success('任务已更新（变更已留痕）');
    } else {
      const created = await apiCreateTasks(props.projectId, {
        title: form.title,
        description: form.description || undefined,
        assigneeStudentIds: form.assigneeStudentIds,
        deadline: form.deadline,
        datasetId: form.datasetId,
        experimentId: form.experimentId ?? undefined,
      });
      ElMessage.success(`已派发 ${created.length} 条任务并通知学生`);
    }
    emit('update:modelValue', false);
    emit('saved');
  } finally {
    saving.value = false;
  }
}
</script>
