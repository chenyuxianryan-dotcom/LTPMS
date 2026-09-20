<template>
  <div class="version-list">
    <el-table v-loading="loading" :data="versions" size="small" border>
      <el-table-column label="版本" width="70" align="center">
        <template #default="{ row }">v{{ row.versionNo }}</template>
      </el-table-column>
      <el-table-column prop="originalFilename" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="fileType" label="类型" width="90" />
      <el-table-column prop="uploaderName" label="上传人" width="100" />
      <el-table-column prop="note" label="说明" min-width="140" show-overflow-tooltip />
      <el-table-column label="上传时间" width="140">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="previewable(row)" link type="primary" @click="openPreview(row)">预览</el-button>
          <el-button link type="primary" @click="handleDownload(row)">下载</el-button>
          <el-button v-if="canDeleteVersion(row)" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="previewVisible" :title="`预览 · ${previewTarget?.originalFilename ?? ''}`" width="760px">
      <el-alert
        v-if="preview?.message"
        type="info"
        :closable="false"
        show-icon
        :title="preview.message"
        class="version-list__msg"
      />
      <el-table v-if="preview && preview.rows.length" :data="pagedRows" size="small" border max-height="420">
        <el-table-column
          v-for="(col, idx) in preview.rows[0]"
          :key="idx"
          :label="col || `列${idx + 1}`"
          :prop="String(idx)"
          min-width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row[idx] }}</template>
        </el-table-column>
      </el-table>
      <div v-if="preview && preview.truncated" class="version-list__trunc">
        仅预览前 {{ preview.rows.length - 1 }} 行，完整数据请下载
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiDeleteVersion, apiDownloadVersion, apiListVersions, apiPreviewVersion } from '@/api/project';
import { useUserStore } from '@/store/user';
import { saveBlob } from '@/utils/request';
import type { IDataset, IDatasetPreview, IDatasetVersion } from '@/types/api';
import { fmtDateTime, fmtSize } from '../projectUtils';

const props = defineProps<{ dataset: IDataset }>();
const emit = defineEmits<{ (e: 'changed'): void }>();

const userStore = useUserStore();
const loading = ref(false);
const versions = ref<IDatasetVersion[]>([]);

async function load(): Promise<void> {
  loading.value = true;
  try {
    versions.value = await apiListVersions(props.dataset.id);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/** 版本删除与后端 B3 对齐：上传者本人或归属教师——管理员只读（2026-09-20 权限边界，后端 403） */
function canDeleteVersion(row: IDatasetVersion): boolean {
  if (userStore.isAdmin) return false;
  if (!userStore.isStudent) return true; // 教师视角（能进管理 Tab 即归属老师）
  return row.uploaderId === userStore.user?.id;
}

function previewable(row: IDatasetVersion): boolean {
  const ext = (row.fileType || row.originalFilename).toLowerCase();
  return ext.includes('csv') || ext.includes('txt') || ext.includes('xls');
}

/* ------------------------------------------------------------- 预览 */

const previewVisible = ref(false);
const previewTarget = ref<IDatasetVersion | null>(null);
const preview = ref<IDatasetPreview | null>(null);

/** 首行当表头：数据行去掉 header 行展示 */
const pagedRows = computed<string[][]>(() => (preview.value ? preview.value.rows.slice(1) : []));

async function openPreview(row: IDatasetVersion): Promise<void> {
  previewTarget.value = row;
  preview.value = null;
  previewVisible.value = true;
  preview.value = await apiPreviewVersion(row.id);
}

/* ------------------------------------------------------------- 下载/删除 */

async function handleDownload(row: IDatasetVersion): Promise<void> {
  const blob = await apiDownloadVersion(row.id);
  saveBlob(blob, row.originalFilename);
}

async function handleDelete(row: IDatasetVersion): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除 v${row.versionNo}（${row.originalFilename}）的记录？文件本体保留。`, '删除版本', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await apiDeleteVersion(row.id);
  ElMessage.success('版本记录已删除');
  await load();
  emit('changed');
}
</script>

<style scoped lang="scss">
.version-list {
  padding: var(--space-2) var(--space-4);

  &__msg {
    margin-bottom: var(--space-3);
  }

  &__trunc {
    margin-top: var(--space-2);
    font-size: 12px;
    color: var(--text-tertiary);
  }
}
</style>
