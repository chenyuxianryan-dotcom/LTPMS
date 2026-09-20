<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">
          实验项目
          <el-tag v-if="isAdmin" size="small" type="info" effect="plain" class="experiment-page__ro-tag">只读</el-tag>
        </div>
        <div class="page-header__desc">{{ headerDesc }}</div>
      </div>
      <div class="filter-bar">
        <el-segmented v-model="segment" :options="SEGMENT_OPTIONS" @change="load" />
        <el-input v-model="keyword" placeholder="搜索课题名 / 编号" clearable style="width: 200px" />
        <template v-if="isTeacher">
          <el-tooltip :content="migrationDisabledReason" placement="top" :disabled="!migrationDisabledReason">
            <span>
              <el-button :icon="RefreshLeft" :disabled="!!migrationDisabledReason" @click="openMigration">
                上学期智能迁移
              </el-button>
            </span>
          </el-tooltip>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建课题</el-button>
        </template>
      </div>
    </div>

    <el-table v-loading="loading" :data="filtered" border stripe>
      <el-table-column prop="projectCode" label="课题编号" width="130" class-name="font-mono" />
      <el-table-column label="课题名称" min-width="190">
        <template #default="{ row }">
          <el-link type="primary" @click="goDetail(row.id)">{{ row.projectName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="类型" min-width="188" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag :type="PROJECT_SOURCE_MAP[row.source]?.tagType ?? 'info'" effect="light" size="small">
            {{ PROJECT_SOURCE_MAP[row.source]?.label ?? row.source }}
          </el-tag>
          <span v-if="row.courseName" class="experiment-page__course">{{ row.courseName }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isAdmin" prop="teacherName" label="指导教师" width="90" />
      <el-table-column prop="memberCount" label="成员数" width="80" align="center" />
      <el-table-column label="完成度" width="160" align="center">
        <template #default="{ row }">
          <el-progress :percentage="Math.round(row.projectRate)" :stroke-width="10" />
        </template>
      </el-table-column>
      <el-table-column label="逾期" width="70" align="center">
        <template #default="{ row }">
          <span :class="{ 'experiment-page__overdue': row.overdueCount > 0 }">{{ row.overdueCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="PROJECT_STATUS_MAP[row.status]?.tagType ?? 'info'" effect="light">
            {{ PROJECT_STATUS_MAP[row.status]?.label ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" :width="isTeacher ? 158 : 80" fixed="right">
        <template #default="{ row }">
          <div class="experiment-page__ops">
            <template v-if="isTeacher">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.source === 'USER'" link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <el-button link type="primary" @click="goDetail(row.id)">进入</el-button>
          </div>
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

    <!-- 上学期智能迁移草稿确认 -->
    <el-dialog v-model="migrationVisible" title="上学期智能迁移" width="720px" :close-on-click-modal="false">
      <div v-loading="migrationLoading">
        <el-empty v-if="!migrationLoading && migrationDrafts.length === 0" description="上学期无可迁移的实验项目" :image-size="60" />
        <template v-else>
          <el-alert
            v-if="migrationDrafts.length > 0"
            :title="`共 ${migrationDrafts.length} 门课程可从上学期迁移，确认后批量复制实验项目（自动重编实验编号）`"
            type="info"
            show-icon
            :closable="false"
            class="experiment-page__migration-tip"
          />
          <el-table :data="migrationDrafts" border size="small" max-height="360">
            <el-table-column prop="courseCode" label="课程号" width="120" />
            <el-table-column prop="courseName" label="课程名" min-width="160" />
            <el-table-column prop="previousSemesterName" label="上学期" width="130" />
            <el-table-column prop="previousExperimentCount" label="可迁移实验数" width="110" align="right" class-name="tabular-nums" />
            <el-table-column label="实验项目" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.experimentNames.join('、') }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="migrationVisible = false">取消</el-button>
        <el-button type="primary" :disabled="migrationDrafts.length === 0" :loading="migrating" @click="handleMigrationConfirm">
          确认迁移
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { Plus, RefreshLeft } from '@element-plus/icons-vue';
import {
  apiCreateProject,
  apiDeleteProject,
  apiEnsureCourseContainer,
  apiEnsureMyCourseContainers,
  apiListProjects,
  apiUpdateProject,
} from '@/api/project';
import { apiListSemesters } from '@/api/semester';
import { apiMigrationConfirm, apiMigrationPreview, type IMigrationDraft } from '@/api/experiment';
import { PROJECT_SOURCE_MAP, PROJECT_STATUS_MAP } from '@/constants';
import { fmtDateTime } from '@/pages/project/projectUtils';
import { useUserStore } from '@/store/user';
import type { IProject, ISemester } from '@/types/api';

type TSegment = 'ALL' | 'SYSTEM' | 'USER';

const SEGMENT_OPTIONS: { label: string; value: TSegment }[] = [
  { label: '全部', value: 'ALL' },
  { label: '课程课题', value: 'SYSTEM' },
  { label: '我的课题', value: 'USER' },
];

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);
const isStudent = computed(() => userStore.isStudent);
/** 教师写侧身份（2026-09-20 权限边界）：新建/编辑/删除/迁移仅教师可用，管理员为只读 */
const isTeacher = computed(() => userStore.role === 'TEACHER');

const headerDesc = computed(() => {
  if (isStudent.value) return '我的实验项目：查看实验目录、任务与进度';
  if (isAdmin.value) return '只读视图：可浏览全部课程课题与独立课题，实验项目由课程教师维护';
  return '课程课题由系统按课程生成、承载实验目录；独立课题自建自管';
});

const loading = ref(false);
const records = ref<IProject[]>([]);
const keyword = ref('');
const segment = ref<TSegment>('ALL');

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
    const source = segment.value === 'ALL' ? undefined : segment.value;
    records.value = await apiListProjects(source);
  } finally {
    loading.value = false;
  }
}

function goDetail(id: number): void {
  void router.push(`/experiments/${id}`);
}

/* ------------------------------------------------------------- 读侧 ensure */

const semesters = ref<ISemester[]>([]);
const migrationDisabledReason = computed(() =>
  semesters.value.some((item) => item.status === 'ACTIVE') ? '' : '当前无进行中的学期，暂不可迁移',
);

/** 带参深链（课程页 / Dashboard 就绪度方格）：确保容器存在并直达其实验目录 Tab */
async function followCourseDeepLink(): Promise<boolean> {
  const courseId = Number(route.query.courseId);
  if (!courseId || isStudent.value) return false;
  const container = await apiEnsureCourseContainer(courseId);
  void router.replace({ path: `/experiments/${container.id}`, query: { tab: 'catalog' } });
  return true;
}

/** 管理员深链（2026-09-20 权限边界）：只在已加载列表中只读定位容器，绝不触发 ensure 写侧 */
async function followCourseDeepLinkReadonly(): Promise<boolean> {
  const courseId = Number(route.query.courseId);
  if (!courseId) return false;
  const container = records.value.find((p) => p.courseId === courseId && p.source === 'SYSTEM');
  if (container) {
    void router.replace({ path: `/experiments/${container.id}`, query: { tab: 'catalog' } });
  } else {
    ElMessage.info('该课程尚未生成实验项目容器，暂无可查看内容');
  }
  return false;
}

onMounted(async () => {
  // /semesters 是 ADMIN/TEACHER 端点（r2-H1 学生白名单未含），学生不查，避免 403 中断挂载
  if (isAdmin.value) {
    // 管理员只读（2026-09-20 权限边界）：不做任何 ensure 写侧，仅加载后只读定位深链容器
    await load();
    await followCourseDeepLinkReadonly();
    return;
  }
  if (!isStudent.value) {
    semesters.value = await apiListSemesters();
    if (await followCourseDeepLink()) return;
  } else {
    // 学生侧读侧 ensure（R1/AC1）：教师从未访问也保证本课容器与名单快照就位
    await apiEnsureMyCourseContainers().catch(() => undefined);
  }
  await load();
});

/* ------------------------------------------------------------- 建/改/删 */

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

async function handleDelete(row: IProject): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除独立课题「${row.projectName}」吗？已含成员/任务/数据的课题不可删除，可归档留痕。`,
      '删除确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await apiDeleteProject(row.id);
  ElMessage.success('删除成功');
  await load();
}

/* ------------------------------------------------------------- 智能迁移 */

const migrationVisible = ref(false);
const migrationLoading = ref(false);
const migrating = ref(false);
const migrationDrafts = ref<IMigrationDraft[]>([]);

async function openMigration(): Promise<void> {
  migrationVisible.value = true;
  migrationLoading.value = true;
  migrationDrafts.value = [];
  try {
    migrationDrafts.value = await apiMigrationPreview();
  } catch {
    // 错误由 request 拦截器统一提示
  } finally {
    migrationLoading.value = false;
  }
}

async function handleMigrationConfirm(): Promise<void> {
  migrating.value = true;
  try {
    const result = await apiMigrationConfirm();
    ElMessage.success(`迁移完成：${result.courseCount} 门课程，共 ${result.experimentCount} 个实验项目`);
    migrationVisible.value = false;
    await load();
  } catch {
    // 错误由 request 拦截器统一提示
  } finally {
    migrating.value = false;
  }
}
</script>

<style scoped lang="scss">
.experiment-page {
  &__ro-tag {
    margin-left: var(--space-2);
    vertical-align: middle;
  }

  &__course {
    margin-left: var(--space-2);
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__overdue {
    font-weight: 700;
    color: var(--el-color-danger);
  }

  &__ops {
    display: flex;
    align-items: center;
    gap: var(--space-2);

    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }

  &__migration-tip {
    margin-bottom: var(--space-3);
  }
}
</style>
