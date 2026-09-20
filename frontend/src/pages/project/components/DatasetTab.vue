<template>
  <div>
    <div class="dataset-tab__bar">
      <template v-if="!isAdminView">
        <el-button type="primary" :icon="Upload" :loading="uploading" @click="pickFile">上传数据集</el-button>
        <span class="dataset-tab__hint">
          支持 xlsx / csv / txt / zip / 图片（≤50MB，按内容魔数校验）；同名文件将弹出「挂为新版本 / 换名新建」二选一（只增不覆盖）
        </span>
        <input ref="fileInput" type="file" class="dataset-tab__file" @change="onFilePicked" />
      </template>
      <!-- 管理员只读（2026-09-20 权限边界）：可展开版本、下载/预览，不可上传/改名/删除 -->
      <span v-else class="dataset-tab__hint">管理员只读视图：展开数据集可查看版本、预览与下载，上传与改名/删除由课程教师或学生成员操作</span>
    </div>

    <el-table v-loading="loading" :data="datasets" border stripe row-key="id">
      <el-table-column type="expand">
        <template #default="{ row }">
          <VersionList :dataset="row" @changed="load" />
        </template>
      </el-table-column>
      <el-table-column prop="datasetName" label="数据集名称" min-width="180" />
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="最新版本" width="90" align="center">
        <template #default="{ row }">v{{ row.latestVersion }}</template>
      </el-table-column>
      <el-table-column prop="createdByName" label="创建人" width="110" />
      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <!-- 操作列（传新版本/改名/删除）均为写侧：管理员只读视图整体隐藏 -->
      <el-table-column v-if="!isAdminView" label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="uploadNewVersion(row)">传新版本</el-button>
          <el-button v-if="canManageDataset(row)" link type="primary" @click="openEdit(row)">改名/描述</el-button>
          <el-button v-if="canManageDataset(row)" link type="danger" @click="handleDeleteDataset(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 改名/描述 -->
    <el-dialog v-model="editVisible" title="编辑数据集" width="440px">
      <el-form label-width="70px">
        <el-form-item label="名称">
          <el-input v-model="editForm.datasetName" maxlength="128" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="handleEditSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新版本上传（带说明） -->
    <el-dialog v-model="versionVisible" title="上传新版本" width="440px">
      <el-form label-width="70px">
        <el-form-item label="文件">
          <span>{{ pendingFile?.name ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="版本说明">
          <el-input v-model="versionNote" type="textarea" :rows="2" maxlength="255" placeholder="如：补全 5 月缺采数据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="confirmAddVersion">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Upload } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiAddVersion, apiCreateDataset, apiDeleteDataset, apiListDatasets, apiUpdateDataset } from '@/api/project';
import { ERROR_CODE } from '@/constants';
import { useUserStore } from '@/store/user';
import type { IDataset, IProject } from '@/types/api';
import { baseName, fmtDateTime } from '../projectUtils';
import VersionList from './VersionList.vue';

const props = defineProps<{ project: IProject; canManage: boolean }>();

const userStore = useUserStore();
/** 管理员只读视图（2026-09-20 权限边界）：后端写侧已 403，前端同步隐藏写入口 */
const isAdminView = computed(() => userStore.isAdmin);
const loading = ref(false);
const uploading = ref(false);
const datasets = ref<IDataset[]>([]);

async function load(): Promise<void> {
  loading.value = true;
  try {
    datasets.value = await apiListDatasets(props.project.id);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/** 数据集改名/删除权限与后端 requireManager 对齐：管理视角或创建者本人；管理员只读（后端 403） */
function canManageDataset(row: IDataset): boolean {
  if (userStore.isAdmin) return false;
  return props.canManage || row.createdById === userStore.user?.id;
}

/* ------------------------------------------------------------- 新建上传（含 C4 二选一） */

const fileInput = ref<HTMLInputElement>();
const pendingForVersion = ref<IDataset | null>(null);
const pendingFile = ref<File | null>(null);
const versionVisible = ref(false);
const versionNote = ref('');

function pickFile(): void {
  pendingForVersion.value = null;
  fileInput.value?.click();
}

function uploadNewVersion(row: IDataset): void {
  pendingForVersion.value = row;
  fileInput.value?.click();
}

async function onFilePicked(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  const target = pendingForVersion.value;
  if (target) {
    pendingFile.value = file;
    versionNote.value = '';
    versionVisible.value = true;
    return;
  }
  await createWithConflictFlow(file);
}

async function confirmAddVersion(): Promise<void> {
  if (!pendingFile.value || !pendingForVersion.value) return;
  uploading.value = true;
  try {
    await apiAddVersion(pendingForVersion.value.id, pendingFile.value, versionNote.value || undefined);
    ElMessage.success('新版本已上传');
    versionVisible.value = false;
    await load();
  } finally {
    uploading.value = false;
  }
}

/** r2-C4：同名冲突（业务码 46001）→ 二选一「挂为 v(N+1) / 换名新建」 */
async function createWithConflictFlow(file: File): Promise<void> {
  uploading.value = true;
  try {
    await apiCreateDataset(props.project.id, file);
    ElMessage.success('数据集已创建（v1）');
  } catch (error) {
    const biz = error as { code?: number; message?: string };
    if (biz.code === ERROR_CODE.DATASET_NAME_CONFLICT) {
      await handleNameConflict(file);
    }
    // 其余错误已由拦截器统一提示
  } finally {
    uploading.value = false;
  }
}

async function handleNameConflict(file: File): Promise<void> {
  const name = baseName(file.name);
  const existing = datasets.value.find((d) => d.datasetName === name);
  try {
    await ElMessageBox.confirm(
      `已存在同名数据集「${name}」（当前 v${existing?.latestVersion ?? '?'}）。挂载为新版本保留历史版本线，或换个名称新建。`,
      '同名数据集',
      {
        type: 'warning',
        confirmButtonText: existing ? `挂为 v${(existing.latestVersion ?? 0) + 1}` : '挂为新版本',
        cancelButtonText: '换名新建',
        distinguishCancelAndClose: true,
      },
    );
    if (!existing) {
      ElMessage.warning('未在同课题列表中定位到同名数据集，请展开列表手动上传新版本');
      return;
    }
    pendingFile.value = file;
    pendingForVersion.value = existing;
    versionNote.value = '';
    versionVisible.value = true;
  } catch (action) {
    if (action === 'cancel') {
      const newName = await promptNewName(name);
      if (!newName) return;
      uploading.value = true;
      try {
        await apiCreateDataset(props.project.id, file, newName);
        ElMessage.success(`数据集「${newName}」已创建（v1）`);
      } finally {
        uploading.value = false;
      }
    }
  }
}

async function promptNewName(defaultName: string): Promise<string | null> {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的数据集名称', '换名新建', {
      inputValue: `${defaultName}-新`,
      inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '名称不能为空'),
    });
    return value.trim();
  } catch {
    return null;
  }
}

/* ------------------------------------------------------------- 改名/删除 */

const editVisible = ref(false);
const editing = ref<IDataset | null>(null);
const editForm = reactive({ datasetName: '', description: '' });

function openEdit(row: IDataset): void {
  editing.value = row;
  editForm.datasetName = row.datasetName;
  editForm.description = row.description ?? '';
  editVisible.value = true;
}

async function handleEditSave(): Promise<void> {
  if (!editing.value) return;
  await apiUpdateDataset(editing.value.id, { datasetName: editForm.datasetName, description: editForm.description });
  ElMessage.success('已保存');
  editVisible.value = false;
  await load();
}

async function handleDeleteDataset(row: IDataset): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除数据集「${row.datasetName}」及其全部版本记录？文件本身保留在存储层。`,
      '删除数据集',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await apiDeleteDataset(row.id);
  ElMessage.success('数据集已删除');
  await load();
}
</script>

<style scoped lang="scss">
.dataset-tab {
  &__bar {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-4);
  }

  &__hint {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__file {
    display: none;
  }
}
</style>
