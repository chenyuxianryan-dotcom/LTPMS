<template>
  <div v-loading="loading" class="page-container">
    <template v-if="project">
      <div class="page-header">
        <div>
          <div class="page-header__title">
            <el-button link :icon="ArrowLeft" @click="router.push('/projects')">课题项目</el-button>
            / {{ project.projectName }}
            <el-tag class="detail-page__tag" :type="PROJECT_STATUS_MAP[project.status]?.tagType ?? 'info'" effect="light">
              {{ PROJECT_STATUS_MAP[project.status]?.label ?? project.status }}
            </el-tag>
          </div>
          <div class="page-header__desc">
            {{ project.projectCode }} · 指导教师 {{ project.teacherName ?? '-' }} · {{ project.memberCount }} 名成员
            <template v-if="isStudent"> · 完成度 {{ Math.round(project.projectRate) }}%</template>
          </div>
        </div>
        <div v-if="canManage" class="filter-bar">
          <el-button :icon="Edit" @click="editVisible = true">编辑课题</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="detail-page__tabs">
        <el-tab-pane label="成员" name="members">
          <MemberTab :project="project" :can-manage="canManage" @changed="load" />
        </el-tab-pane>
        <el-tab-pane label="任务" name="tasks">
          <TaskTab :project="project" :can-manage="canManage" @changed="load" />
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
import { ArrowLeft, Edit } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { apiGetProject, apiUpdateProject } from '@/api/project';
import { PROJECT_STATUS_MAP } from '@/constants';
import { useUserStore } from '@/store/user';
import type { IProject } from '@/types/api';
import MemberTab from './components/MemberTab.vue';
import TaskTab from './components/TaskTab.vue';
import DatasetTab from './components/DatasetTab.vue';
import ProgressTab from './components/ProgressTab.vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
const project = ref<IProject | null>(null);
const activeTab = ref((route.query.tab as string) || 'members');

const isStudent = computed(() => userStore.isStudent);
/** 管理视角：ADMIN 或归属教师 */
const canManage = computed(
  () => !isStudent.value && (userStore.isAdmin || project.value?.teacherId === userStore.user?.id),
);

async function load(): Promise<void> {
  const id = Number(route.params.id);
  loading.value = true;
  try {
    project.value = await apiGetProject(id);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
watch(() => route.params.id, load);

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
