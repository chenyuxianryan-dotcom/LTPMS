<template>
  <el-dialog
    :model-value="modelValue"
    :title="`课程资料 · ${courseName}`"
    width="720px"
    @update:model-value="emit('update:modelValue', $event)"
    @open="loadData"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="入库后阿实可带出处回答本课程问题（引用《文档标题》· 段落标题）；仅本人任教课程可见，学生侧待开放"
      class="course-material__notice"
    />

    <div class="course-material__upload">
      <el-input v-model="title" placeholder="文档标题（留空取文件名）" maxlength="200" class="course-material__title" />
      <el-upload
        ref="uploadRef"
        accept=".docx,.pdf,.txt,.md"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleFileChange"
      >
        <el-button :icon="UploadFilled">{{ file ? file.name : '选择文件' }}</el-button>
      </el-upload>
      <el-button type="primary" :loading="uploading" :disabled="!file" @click="handleUpload">
        {{ uploading ? '解析入库中' : '上传并入库' }}
      </el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe max-height="320">
      <el-table-column prop="title" label="文档标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="格式" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.fileType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="段落数" width="90" align="right" class-name="tabular-nums" />
      <el-table-column label="上传时间" width="160" class-name="tabular-nums">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleDownload(row)">下载</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && records.length === 0" description="暂无课程资料" :image-size="60" />

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import type { UploadFile, UploadInstance, UploadRawFile } from 'element-plus';
import {
  apiDeleteKnowledgeDocument,
  apiDownloadKnowledgeDocument,
  apiListKnowledgeDocuments,
  apiUploadKnowledgeDocument,
  type IKnowledgeDocument,
} from '@/api/knowledge';
import { fmtDateTime } from '@/pages/project/projectUtils';
import { saveBlob } from '@/utils/request';

/** 授课教师的课程资料入库入口：上传即解析切分向量化，检索侧按课程归属控制可见范围 */
const props = defineProps<{ modelValue: boolean; courseId: number; courseName: string }>();
const emit = defineEmits<{ 'update:modelValue': [boolean] }>();

const loading = ref(false);
const records = ref<IKnowledgeDocument[]>([]);
const title = ref('');
const file = ref<UploadRawFile | null>(null);
const uploading = ref(false);
const uploadRef = ref<UploadInstance>();

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    records.value = await apiListKnowledgeDocuments({ category: 'COURSE', courseId: props.courseId });
  } finally {
    loading.value = false;
  }
}

function handleFileChange(uploadFile: UploadFile): void {
  file.value = uploadFile.raw ?? null;
}

async function handleUpload(): Promise<void> {
  if (!file.value) return;
  uploading.value = true;
  try {
    const doc = await apiUploadKnowledgeDocument(file.value, 'COURSE', {
      title: title.value.trim(),
      courseId: props.courseId,
    });
    ElMessage.success(`已入库：${doc.chunkCount} 段，约 1 分钟后可被阿实检索到`);
    title.value = '';
    file.value = null;
    uploadRef.value?.clearFiles();
    await loadData();
  } finally {
    uploading.value = false;
  }
}

async function handleDownload(row: IKnowledgeDocument): Promise<void> {
  const blob = await apiDownloadKnowledgeDocument(row.id);
  saveBlob(blob, row.originalFilename);
}

async function handleDelete(row: IKnowledgeDocument): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除《${row.title}》？其全部段落将一并从检索库移除。`, '删除资料', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await apiDeleteKnowledgeDocument(row.id);
  ElMessage.success('已删除');
  await loadData();
}
</script>

<style scoped lang="scss">
.course-material__notice {
  margin-bottom: var(--space-3);
}

.course-material__upload {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.course-material__title {
  flex: 1;
}
</style>
