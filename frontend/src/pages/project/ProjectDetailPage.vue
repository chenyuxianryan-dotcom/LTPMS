<template>
  <div v-loading="loading" class="page-container">
    <template v-if="project">
      <div class="page-header">
        <div>
          <div class="page-header__title">
            <el-button link :icon="ArrowLeft" @click="router.push('/experiments')">实验项目</el-button>
            / {{ project.projectName }}
            <el-tag class="detail-page__tag" :type="PROJECT_STATUS_MAP[project.status]?.tagType ?? 'info'" effect="light">
              {{ PROJECT_STATUS_MAP[project.status]?.label ?? project.status }}
            </el-tag>
            <el-tag class="detail-page__tag" :type="PROJECT_SOURCE_MAP[project.source]?.tagType ?? 'info'" effect="light">
              {{ PROJECT_SOURCE_MAP[project.source]?.label ?? project.source }}
            </el-tag>
            <el-tag v-if="isAdmin" class="detail-page__tag" type="info" effect="plain">只读</el-tag>
            <el-tooltip v-if="project.source === 'USER' && project.courseId" content="存量挂课课题：课程关联已锁定只读" placement="top">
              <el-tag class="detail-page__tag" type="warning" effect="plain">归类课程 #{{ project.courseId }}</el-tag>
            </el-tooltip>
          </div>
          <div class="page-header__desc">
            {{ project.projectCode }}
            <template v-if="isContainer && project.courseName"> · 课程 {{ project.courseName }}</template>
            · 指导教师 {{ project.teacherName ?? '-' }} · {{ project.memberCount }} 名成员
            <template v-if="isStudent"> · 完成度 {{ Math.round(project.projectRate) }}%</template>
          </div>
        </div>
        <div v-if="canManage" class="filter-bar">
          <el-button :icon="Edit" @click="editVisible = true">编辑课题</el-button>
          <el-button v-if="isContainer && project.status === 'ACTIVE'" :icon="FolderChecked" @click="handleArchive">归档</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="detail-page__tabs">
        <el-tab-pane v-if="isContainer" label="实验目录" name="catalog">
          <CatalogTab :project="project" :can-manage="canManage" />
        </el-tab-pane>
        <el-tab-pane label="成员" name="members">
          <MemberTab :project="project" :can-manage="canManage" @changed="load" />
        </el-tab-pane>
        <el-tab-pane label="任务" name="tasks">
          <TaskTab :project="project" :can-manage="canManage" :audit-view="isAdmin" @changed="load" />
        </el-tab-pane>
        <el-tab-pane label="数据" name="datasets">
          <DatasetTab :project="project" :can-manage="canManage" />
        </el-tab-pane>
        <el-tab-pane label="进度" name="progress">
          <ProgressTab :project-id="project.id" />
        </el-tab-pane>
      </el-tabs>

      <el-dialog v-model="editVisible" title="编辑课题" width="480px">
        <el-form :model="editForm" label-width="90px">
          <el-form-item label="课题名称">
            <el-input v-model="editForm.projectName" maxlength="64" />
          </el-form-item>
          <el-form-item label="课题描述">
            <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="500" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" @click="handleEditSave">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Edit, FolderChecked } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiArchiveProject, apiGetProject, apiUpdateProject } from '@/api/project';
import { PROJECT_SOURCE_MAP, PROJECT_STATUS_MAP } from '@/constants';
import { useUserStore } from '@/store/user';
import type { IProject } from '@/types/api';
import MemberTab from './components/MemberTab.vue';
import TaskTab from './components/TaskTab.vue';
import DatasetTab from './components/DatasetTab.vue';
import ProgressTab from './components/ProgressTab.vue';
import CatalogTab from '@/pages/experiment/components/CatalogTab.vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
const project = ref<IProject | null>(null);
const activeTab = ref((route.query.tab as string) || '');

const isStudent = computed(() => userStore.isStudent);
const isAdmin = computed(() => userStore.isAdmin);
/** 管理视角（2026-09-20 权限边界）：仅归属教师；ADMIN 全局只读（浏览但无编辑/归档入口） */
const canManage = computed(
  () => !isStudent.value && !isAdmin.value && project.value?.teacherId === userStore.user?.id,
);
/** 课程容器（M-Q3）：仅这类课题带「实验目录」Tab */
const isContainer = computed(() => project.value?.source === 'SYSTEM');

async function load(): Promise<void> {
  const id = Number(route.params.id);
  loading.value = true;
  try {
    project.value = await apiGetProject(id);
    const tabs = isContainer.value
      ? ['catalog', 'members', 'tasks', 'datasets', 'progress']
      : ['members', 'tasks', 'datasets', 'progress'];
    if (!tabs.includes(activeTab.value)) {
      // 深链带参（M-Q8）优先；否则容器落在实验目录、独立课题落在成员
      activeTab.value = isContainer.value ? 'catalog' : 'members';
    }
  } finally {
    loading.value = false;
  }
}

onMounted(load);
watch(() => route.params.id, load);

/* ------------------------------------------------------------- 归档定格 */

async function handleArchive(): Promise<void> {
  if (!project.value) return;
  try {
    await ElMessageBox.confirm(
      `归档后「${project.value.projectName}」定格为只读（成员/任务/实验目录保留，报表口径不受影响），不可再编辑。确定归档吗？`,
      '归档课题',
      { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await apiArchiveProject(project.value.id);
  ElMessage.success('已归档定格');
  await load();
}

/* ------------------------------------------------------------- 编辑课题 */

const editVisible = ref(false);
const editForm = reactive({ projectName: '', description: '' });

watch(editVisible, (open) => {
  if (open && project.value) {
    editForm.projectName = project.value.projectName;
    editForm.description = project.value.description ?? '';
  }
});

async function handleEditSave(): Promise<void> {
  if (!project.value) return;
  await apiUpdateProject(project.value.id, {
    projectName: editForm.projectName,
    description: editForm.description,
  });
  ElMessage.success('课题已更新');
  editVisible.value = false;
  await load();
}
</script>

<style scoped lang="scss">
.detail-page {
  &__tag {
    margin-left: var(--space-2);
    vertical-align: middle;
  }

  &__tabs {
    margin-top: var(--space-2);
  }
}
</style>
