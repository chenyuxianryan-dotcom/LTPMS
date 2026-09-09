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
        <el-table :data="draftRows" border size="small" max-height="360">
          <el-table-column label="课程号" width="110">
            <template #default="{ row }"><el-input v-model="row.courseCode" size="small" /></template>
          </el-table-column>
          <el-table-column label="课程名" min-width="150">
            <template #default="{ row }"><el-input v-model="row.courseName" size="small" /></template>
          </el-table-column>
          <el-table-column label="总学时" width="90">
            <template #default="{ row }"><el-input v-model.number="row.totalHours" size="small" /></template>
          </el-table-column>
          <el-table-column label="教师工号" width="110">
            <template #default="{ row }"><el-input v-model="row.teacherNo" size="small" /></template>
          </el-table-column>
          <el-table-column label="实验室编号" width="120">
            <template #default="{ row }"><el-input v-model="row.labCode" size="small" /></template>
          </el-table-column>
          <el-table-column label="上课班级" min-width="140">
            <template #default="{ row }"><el-input v-model="row.classNames" size="small" /></template>
          </el-table-column>
          <el-table-column label="人数" width="80">
            <template #default="{ row }"><el-input v-model.number="row.studentCount" size="small" /></template>
          </el-table-column>
          <el-table-column label="上课时间" min-width="150">
            <template #default="{ row }"><el-input v-model="row.rawScheduleText" size="small" /></template>
          </el-table-column>
        </el-table>
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
import { ElMessage } from 'element-plus';
import { Loading, UploadFilled } from '@element-plus/icons-vue';
import { apiImportScheduleRows, apiRecognizeSchedule, type IScheduleDraftRow } from '@/api/schedule';

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
    const base64 = await toBase64(file);
    const result = await apiRecognizeSchedule(base64);
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

function toBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      // 去掉 data:image/...;base64, 前缀
      resolve(result.split(',')[1] ?? result);
    };
    reader.onerror = () => reject(new Error('读取图片失败'));
    reader.readAsDataURL(file);
  });
}

function resetDraft(): void {
  draftRows.value = [];
  recognizeError.value = '';
}

async function handleConfirm(): Promise<void> {
  confirming.value = true;
  try {
    const result = await apiImportScheduleRows(props.semesterId, draftRows.value);
    ElMessage.success(`导入成功 ${result.successCount} 条`);
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
