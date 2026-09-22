<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">制度与流程</div>
        <div class="page-header__desc">
          入库后阿实的回答会带出处引用「《文档标题》· 段落标题」；仅校内检索，不外传
        </div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openUpload">上传文档</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="支持 docx / pdf / txt / md，单文件不超过 20MB；上传即解析、切分并向量化，入库后约 1 分钟内可被检索到"
      class="kb-page__notice"
    />

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="title" label="文档标题" min-width="240" show-overflow-tooltip />
      <el-table-column label="格式" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.fileType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="110" align="right" class-name="tabular-nums">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="段落数" width="100" align="right" class-name="tabular-nums" />
      <el-table-column prop="uploaderName" label="上传者" width="120" />
      <el-table-column label="上传时间" width="170" class-name="tabular-nums">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleDownload(row)">下载</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="uploadVisible" title="上传制度文档" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="文档标题">
          <el-input v-model="form.title" placeholder="留空则取文件名" maxlength="200" />
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            ref="uploadRef"
            drag
            accept=".docx,.pdf,.txt,.md"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            class="kb-page__upload"
          >
            <el-icon size="32"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择文件</em></div>
            <template #tip>
              <div class="el-upload__tip">docx / pdf / txt / md，扫描件（图片型 PDF）无法提取文字</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!file" @click="handleUpload">
          {{ uploading ? '解析入库中' : '上传并入库' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, UploadFilled } from '@element-plus/icons-vue';
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

/**
 * 制度与流程管理（管理员维护）：入库即可被阿实带出处检索。
 * 写权限仅 ADMIN（红线「ADMIN 全局只读」的知识库窄例外），教师侧走课程资料入口。
 */
const loading = ref(false);
const records = ref<IKnowledgeDocument[]>([]);

const uploadVisible = ref(false);
const uploading = ref(false);
const file = ref<UploadRawFile | null>(null);
const uploadRef = ref<UploadInstance>();
const form = ref<{ title: string }>({ title: '' });

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    records.value = await apiListKnowledgeDocuments({ category: 'POLICY' });
  } finally {
    loading.value = false;
  }
}

function openUpload(): void {
  uploadVisible.value = true;
}

function resetForm(): void {
  form.value = { title: '' };
  file.value = null;
  uploadRef.value?.clearFiles();
}

function handleFileChange(uploadFile: UploadFile): void {
  file.value = uploadFile.raw ?? null;
}

function handleExceed(files: File[]): void {
  uploadRef.value?.clearFiles();
  const raw = files[0] as UploadRawFile | undefined;
  if (raw) {
    uploadRef.value?.handleStart(raw);
    file.value = raw;
  }
}

async function handleUpload(): Promise<void> {
  if (!file.value) return;
  uploading.value = true;
  try {
    const doc = await apiUploadKnowledgeDocument(file.value, 'POLICY', { title: form.value.title.trim() });
    ElMessage.success(`已入库：${doc.chunkCount} 段，约 1 分钟后可被阿实检索到`);
    uploadVisible.value = false;
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
    await ElMessageBox.confirm(`删除《${row.title}》？其全部段落将一并从检索库移除。`, '删除文档', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await apiDeleteKnowledgeDocument(row.id);
  ElMessage.success('已删除');
  await loadData();
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

onMounted(loadData);
</script>

<style scoped lang="scss">
.kb-page__notice {
  margin-bottom: var(--space-4);
}

.kb-page__upload {
  width: 100%;
}
</style>
