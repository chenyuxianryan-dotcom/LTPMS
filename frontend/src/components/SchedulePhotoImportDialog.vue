<template>
  <el-dialog
    :model-value="modelValue"
    :title="'课表拍照导入'"
    width="860px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="photo-import">
      <div v-if="draftRows.length === 0" class="photo-import__upload">
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept="image/*"
          :on-change="handleFileChange"
        >
          <el-icon class="photo-import__upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽课表照片到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">上传清晰的课表截图，系统用视觉模型识别成结构化草稿，人工确认后落库</div>
          </template>
        </el-upload>
        <div v-if="recognizing" class="photo-import__recognizing">
          <el-icon class="is-loading"><Loading /></el-icon> 正在识别课表…
        </div>
        <el-alert v-if="recognizeError" :title="recognizeError" type="warning" show-icon :closable="false" class="photo-import__error" />
      </div>

      <template v-else>
        <div class="photo-import__toolbar">
          <span class="photo-import__hint">识别出 {{ draftRows.length }} 行，可逐格修正后确认导入</span>
          <el-button size="small" text type="primary" @click="resetDraft">重新上传</el-button>
        </div>
        <ScheduleDraftTable :rows="draftRows" />
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :disabled="draftRows.length === 0 || confirming"
        :loading="confirming"
        @click="handleConfirm"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Loading, UploadFilled } from '@element-plus/icons-vue';
import ScheduleDraftTable from '@/components/ScheduleDraftTable.vue';
import { apiRecognizeSchedule, type IScheduleDraftRow } from '@/api/schedule';
import { readImageFile } from '@/utils/imageAttach';
import { confirmScheduleDraft } from '@/utils/scheduleDraft';

const props = defineProps<{
  modelValue: boolean;
  semesterId: number;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'success'): void;
}>();

const draftRows = ref<IScheduleDraftRow[]>([]);
const recognizing = ref(false);
const confirming = ref(false);
const recognizeError = ref('');

async function handleFileChange(uploadFile: { raw?: File }): Promise<void> {
  const file = uploadFile.raw;
  if (!file) return;
  recognizeError.value = '';
  recognizing.value = true;
  try {
    const image = await readImageFile(file);
    const result = await apiRecognizeSchedule(image.base64);
    if (result.success && result.rows.length > 0) {
      draftRows.value = result.rows;
    } else {
      recognizeError.value = result.error || '未识别出有效课表行';
    }
  } catch (e) {
    recognizeError.value = (e as Error).message || '识别失败';
  } finally {
    recognizing.value = false;
  }
}

function resetDraft(): void {
  draftRows.value = [];
  recognizeError.value = '';
}

async function handleConfirm(): Promise<void> {
  confirming.value = true;
  try {
    const result = await confirmScheduleDraft(props.semesterId, draftRows.value);
    if (!result) return; // 用户取消覆盖确认：留在草稿编辑态
    emit('update:modelValue', false);
    emit('success');
    resetDraft();
  } catch {
    // 错误由 request 拦截器统一提示
  } finally {
    confirming.value = false;
  }
}
</script>

<style scoped lang="scss">
.photo-import {
  &__upload {
    .el-upload {
      width: 100%;
    }
  }

  &__upload-icon {
    font-size: 48px;
    color: var(--brand-500);
  }

  &__recognizing {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--space-2);
    margin-top: var(--space-3);
    color: var(--brand-600);
    font-size: 14px;
  }

  &__error {
    margin-top: var(--space-3);
  }

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-3);
  }

  &__hint {
    font-size: 13px;
    color: var(--text-secondary);
  }
}
</style>
