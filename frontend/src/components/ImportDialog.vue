<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="emitVisible"
    @closed="handleClosed"
  >
    <el-alert type="info" :closable="false" show-icon class="import-dialog__tip">
      <template #title>
        请按模板格式填写后上传（.xlsx）。
        <el-link v-if="templateUrl" type="primary" @click="handleDownloadTemplate">下载导入模板</el-link>
      </template>
    </el-alert>

    <el-upload
      ref="uploadRef"
      drag
      accept=".xlsx,.xls"
      :auto-upload="false"
      :limit="1"
      :on-change="handleFileChange"
      :on-exceed="handleExceed"
      class="import-dialog__upload"
    >
      <el-icon size="36"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或<em>点击选择文件</em></div>
    </el-upload>

    <!-- 导入预览（课表等支持先解析不入库的场景） -->
    <div v-if="previewUrl && previewResult" class="import-dialog__preview">
      <el-alert
        :type="previewResult.errorRows.length > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
        :title="`共 ${previewResult.totalCount} 行，可导入 ${previewResult.validCount} 行，异常 ${previewResult.errorRows.length} 行`"
      />
      <el-table v-if="previewResult.errorRows.length > 0" :data="previewResult.errorRows" size="small" max-height="200">
        <el-table-column prop="row" label="行号" width="80" />
        <el-table-column prop="reason" label="异常原因" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="emitVisible(false)">取消</el-button>
      <el-button v-if="previewUrl" :disabled="!file" :loading="previewing" @click="handlePreview">解析预览</el-button>
      <el-button type="primary" :disabled="!file" :loading="submitting" @click="handleSubmit">确认导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import type { UploadFile, UploadInstance, UploadRawFile } from 'element-plus';
import { download, saveBlob, upload } from '@/utils/request';

interface IPreviewResult {
  totalCount: number;
  validCount: number;
  errorRows: { row: number; reason: string }[];
  records: unknown[];
}

const props = defineProps<{
  modelValue: boolean;
  title: string;
  /** 导入接口路径（POST multipart） */
  action: string;
  /** 模板下载路径（GET blob），可选 */
  templateUrl?: string;
  /** 预览接口路径（POST multipart，先解析不入库），可选 */
  previewUrl?: string;
  /** 附加表单字段（如 semesterId、courseId） */
  extraData?: Record<string, unknown>;
}>();

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'success', result: unknown): void }>();

const uploadRef = ref<UploadInstance>();
const file = ref<UploadRawFile | null>(null);
const submitting = ref(false);
const previewing = ref(false);
const previewResult = ref<IPreviewResult | null>(null);

function emitVisible(value: boolean): void {
  emit('update:modelValue', value);
}

function handleClosed(): void {
  file.value = null;
  previewResult.value = null;
  uploadRef.value?.clearFiles();
}

function handleFileChange(uploadFile: UploadFile): void {
  file.value = (uploadFile.raw as UploadRawFile) ?? null;
  previewResult.value = null;
}

function handleExceed(files: File[]): void {
  const raw = files[0] as UploadRawFile;
  uploadRef.value?.clearFiles();
  uploadRef.value?.handleStart(raw);
  file.value = raw;
}

function buildFormData(): FormData {
  const formData = new FormData();
  if (file.value) {
    formData.append('file', file.value);
  }
  Object.entries(props.extraData ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      formData.append(key, String(value));
    }
  });
  return formData;
}

async function handleDownloadTemplate(): Promise<void> {
  if (!props.templateUrl) return;
  const blob = await download(props.templateUrl);
  saveBlob(blob, `${props.title}模板.xlsx`);
}

async function handlePreview(): Promise<void> {
  if (!props.previewUrl || !file.value) return;
  previewing.value = true;
  try {
    previewResult.value = await upload<IPreviewResult>(props.previewUrl, buildFormData());
  } finally {
    previewing.value = false;
  }
}

async function handleSubmit(): Promise<void> {
  if (!file.value) return;
  submitting.value = true;
  try {
    const result = await upload<{ successCount?: number; failedCount?: number; errors?: { message: string }[] }>(
      props.action,
      buildFormData(),
    );
    const failed = result?.failedCount ?? 0;
    if (failed > 0) {
      const first = result?.errors?.length ? `，如：${result.errors[0].message}` : '';
      ElMessage.warning(`导入完成：成功 ${result?.successCount ?? 0} 条，失败 ${failed} 条${first}`);
    } else {
      ElMessage.success('导入完成');
    }
    emit('success', result);
    emitVisible(false);
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped lang="scss">
.import-dialog {
  &__tip {
    margin-bottom: var(--space-4);
  }

  &__upload {
    width: 100%;
  }

  &__preview {
    margin-top: var(--space-4);
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
}
</style>
