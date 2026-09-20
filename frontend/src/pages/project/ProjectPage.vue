<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">课题项目</div>
        <div class="page-header__desc">{{ isAdmin ? '全部教师的课题项目（行内完成度与详情矩阵同源）' : '我的课题：建课题 → 组团队 → 派任务 → 验收闭环' }}</div>
      </div>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索课题名 / 编号" clearable style="width: 220px" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新建课题</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="filtered" border stripe>
      <el-table-column prop="projectCode" label="课题编号" width="140" />
      <el-table-column label="课题名称" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="goDetail(row.id)">{{ row.projectName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column v-if="isAdmin" prop="teacherName" label="指导教师" width="110" />
      <el-table-column prop="memberCount" label="成员数" width="90" align="center" />
      <el-table-column label="完成度" width="180" align="center">
        <template #default="{ row }">
          <el-progress :percentage="Math.round(row.projectRate)" :stroke-width="14" text-inside />
        </template>
      </el-table-column>
      <el-table-column label="逾期" width="80" align="center">
        <template #default="{ row }">
          <span :class="{ 'project-page__overdue': row.overdueCount > 0 }">{{ row.overdueCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="PROJECT_STATUS_MAP[row.status]?.tagType ?? 'info'" effect="light">
            {{ PROJECT_STATUS_MAP[row.status]?.label ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="goDetail(row.id)">进入</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑课题' : '新建课题'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="课题名称" prop="projectName">
          <el-input v-model="form.projectName" maxlength="64" placeholder="如：鼓点时序数据分析课题" />
        </el-form-item>
        <el-form-item label="课题描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { apiCreateProject, apiListProjects, apiUpdateProject } from '@/api/project';
import { PROJECT_STATUS_MAP } from '@/constants';
import { useUserStore } from '@/store/user';
import type { IProject } from '@/types/api';

const router = useRouter();
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

const loading = ref(false);
const records = ref<IProject[]>([]);
const keyword = ref('');

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return records.value;
  return records.value.filter(
    (p) => p.projectName.toLowerCase().includes(kw) || p.projectCode.toLowerCase().includes(kw),
  );
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    records.value = await apiListProjects();
  } finally {
    loading.value = false;
  }
}

onMounted(load);

function goDetail(id: number): void {
  router.push(`/projects/${id}`);
}

/* ------------------------------------------------------------- 建/改 */

const formVisible = ref(false);
const saving = ref(false);
const editingId = ref<number | null>(null);
const formRef = ref<FormInstance>();
const form = reactive({ projectName: '', description: '' });

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入课题名称', trigger: 'blur' }],
};

function openCreate(): void {
  editingId.value = null;
  form.projectName = '';
  form.description = '';
  formVisible.value = true;
}

function openEdit(row: IProject): void {
  editingId.value = row.id;
  form.projectName = row.projectName;
  form.description = row.description ?? '';
  formVisible.value = true;
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (editingId.value == null) {
      await apiCreateProject({ projectName: form.projectName, description: form.description || undefined });
      ElMessage.success('课题已创建');
    } else {
      await apiUpdateProject(editingId.value, { projectName: form.projectName, description: form.description });
      ElMessage.success('课题已更新');
    }
    formVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.project-page__overdue {
  font-weight: 700;
  color: var(--el-color-danger);
}
</style>
