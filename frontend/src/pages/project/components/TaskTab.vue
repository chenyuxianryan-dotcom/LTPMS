<template>
  <div>
    <div v-if="canManage" class="task-tab__bar">
      <el-button type="primary" :icon="Plus" @click="openCreate">派发任务</el-button>
      <span class="task-tab__hint">多人派发将复制为独立任务并通知学生；已提交任务不可删除、只能驳回后关闭（规则①）</span>
    </div>

    <div v-if="experiments.length" class="task-tab__filters">
      <el-select v-model="expFilter" clearable placeholder="按关联实验筛选" style="width: 240px">
        <el-option v-for="e in experiments" :key="e.id" :value="e.id" :label="e.experimentName" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="filteredTasks" border stripe>
      <el-table-column prop="title" label="任务标题" min-width="200" show-overflow-tooltip />
      <el-table-column v-if="experiments.length" label="关联实验" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.experimentName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="assigneeName" label="执行人" width="110" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusOf(row).tagType" effect="light">{{ statusOf(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="截止时间" width="150">
        <template #default="{ row }">
          <span :class="{ 'task-tab__overdue': row.overdue }">
            {{ fmtDateTime(row.deadline) }}<el-tag v-if="row.overdue" size="small" type="danger" effect="plain">逾期</el-tag>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="关联数据" width="90" align="center">
        <template #default="{ row }">{{ row.datasetId ? `#${row.datasetId}` : '-' }}</template>
      </el-table-column>
      <el-table-column v-if="canManage || auditView" label="操作" :width="canManage ? 250 : 150" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canManage && row.status === 'SUBMITTED'" link type="success" @click="openReview(row)">验收</el-button>
          <el-button v-if="canManage && canEdit(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openSubmissions(row)">提交记录</el-button>
          <el-button link type="primary" @click="openRevisions(row)">修订</el-button>
          <el-button v-if="canManage && canDelete(row)" link type="danger" @click="handleDelete(row)">
            {{ deleteLabel(row) }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <TaskFormDrawer
      v-model="formVisible"
      :project-id="project.id"
      :members="members"
      :datasets="datasets"
      :experiments="experiments"
      :edit-task="editTask"
      :prefill="prefill"
      @saved="load"
    />
    <TaskReviewDialog v-model="reviewVisible" :task="reviewTask" :submission="reviewSubmission" @done="load" />

    <!-- 提交记录（半透明：教师/管理员可见该任务提交流水） -->
    <el-dialog v-model="subsVisible" title="提交记录" width="620px">
      <el-timeline v-if="subs.length">
        <el-timeline-item
          v-for="s in subs"
          :key="s.id"
          :timestamp="fmtFullDateTime(s.submittedAt)"
          :type="s.reviewedAt ? 'info' : 'primary'"
        >
          <div><b>{{ s.submitterName }}</b> 提交</div>
          <div class="task-tab__sub-content">{{ s.content }}</div>
          <div v-if="s.reviewedAt" class="task-tab__sub-review">
            验收于 {{ fmtFullDateTime(s.reviewedAt) }}：{{ s.reviewComment || '通过（无意见）' }}
          </div>
          <div v-else class="task-tab__sub-pending">待验收</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无提交" :image-size="60" />
    </el-dialog>

    <!-- 修订留痕 -->
    <el-dialog v-model="revVisible" title="任务修订记录" width="620px">
      <el-table :data="revisions" size="small" border>
        <el-table-column label="字段" width="110">
          <template #default="{ row }">{{ TASK_REVISION_FIELD_LABELS[row.fieldName as string] ?? row.fieldName }}</template>
        </el-table-column>
        <el-table-column prop="oldValue" label="改前" min-width="120" show-overflow-tooltip />
        <el-table-column prop="newValue" label="改后" min-width="120" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="90" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  apiContainerExperiments,
  apiDeleteTask,
  apiListDatasets,
  apiListMembers,
  apiListSubmissions,
  apiListTaskRevisions,
  apiListTasks,
} from '@/api/project';
import { TASK_REVISION_FIELD_LABELS, TASK_STATUS_MAP } from '@/constants';
import type { IDataset, IExperiment, IProject, IProjectMember, IProjectTask, IRevisionView, ITaskSubmission } from '@/types/api';
import { fmtDateTime, fmtFullDateTime } from '../projectUtils';
import TaskFormDrawer from './TaskFormDrawer.vue';
import TaskReviewDialog from './TaskReviewDialog.vue';

/** auditView：管理员只读视角（2026-09-20 权限边界）——操作列仅保留提交记录/修订等读侧入口 */
const props = defineProps<{
  project: IProject;
  canManage: boolean;
  auditView?: boolean;
  /** T04：目录行「布置任务」跨 Tab 触发——prefill 预填内容，seq 每次自增以重复触发 */
  assignPrefill?: { title?: string; experimentId?: number } | null;
  assignSeq?: number;
}>();

const loading = ref(false);
const tasks = ref<IProjectTask[]>([]);
const members = ref<IProjectMember[]>([]);
const datasets = ref<IDataset[]>([]);
/** T04：本容器实验目录（独立课题为空，隐藏筛选与关联项） */
const experiments = ref<IExperiment[]>([]);
/** T04：按关联实验筛选（客户端过滤，空=全部） */
const expFilter = ref<number | null>(null);

const filteredTasks = computed(() =>
  expFilter.value == null ? tasks.value : tasks.value.filter((t) => t.experimentId === expFilter.value),
);

async function load(): Promise<void> {
  loading.value = true;
  try {
    tasks.value = await apiListTasks(props.project.id);
    if (props.project.courseId != null) {
      experiments.value = await apiContainerExperiments(props.project.id);
    }
    if (props.canManage) {
      const [ms, ds] = await Promise.all([apiListMembers(props.project.id), apiListDatasets(props.project.id)]);
      members.value = ms;
      datasets.value = ds;
    }
  } finally {
    loading.value = false;
  }
}

onMounted(load);

function statusOf(row: IProjectTask): { label: string; tagType: 'success' | 'warning' | 'danger' | 'info' | 'primary' } {
  const base = TASK_STATUS_MAP[row.status] ?? { label: row.status, tagType: 'info' as const };
  if (row.overdue) return { label: `${base.label}·逾期`, tagType: 'danger' };
  return base;
}

/* ------------------------------------------------------------- 派发/编辑 */

const formVisible = ref(false);
const editTask = ref<IProjectTask | null>(null);
const prefill = ref<{ title?: string; experimentId?: number } | null>(null);

function openCreate(): void {
  editTask.value = null;
  prefill.value = null;
  formVisible.value = true;
}

function openEdit(row: IProjectTask): void {
  editTask.value = row;
  prefill.value = null;
  formVisible.value = true;
}

/**
 * T04：目录行「布置任务」跨 Tab 触发。父组件切到本 Tab 时本组件可能刚挂载、目录尚未加载完，
 * 故先确保目录就绪再开抽屉，否则「关联实验」下拉项因 experiments 为空而不显示。
 */
watch(
  () => props.assignSeq,
  async (seq) => {
    if (!seq) return;
    if (props.project.courseId != null && !experiments.value.length) {
      try {
        experiments.value = await apiContainerExperiments(props.project.id);
      } catch {
        /* 目录拉取失败不阻断派发，抽屉仍可用 */
      }
    }
    editTask.value = null;
    prefill.value = props.assignPrefill ?? null;
    formVisible.value = true;
  },
  { immediate: true },
);

/** 已验收任务锁定不可编辑；CLOSED 只读 */
function canEdit(row: IProjectTask): boolean {
  return row.status !== 'ACCEPTED' && row.status !== 'CLOSED';
}

/* ------------------------------------------------------------- 删除（规则①） */

function canDelete(row: IProjectTask): boolean {
  return row.status !== 'SUBMITTED' && row.status !== 'ACCEPTED';
}

function deleteLabel(row: IProjectTask): string {
  return row.status === 'REJECTED' ? '关闭' : '删除';
}

async function handleDelete(row: IProjectTask): Promise<void> {
  const closing = row.status === 'REJECTED';
  try {
    await ElMessageBox.confirm(
      closing
        ? `关闭任务「${row.title}」？关闭后进入台账（CLOSED），不再计入完成度分母。`
        : `软删除任务「${row.title}」？该操作会留痕。`,
      closing ? '关闭任务' : '删除任务',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await apiDeleteTask(row.id);
  ElMessage.success(closing ? '任务已关闭' : '任务已删除');
  await load();
}

/* ------------------------------------------------------------- 验收 */

const reviewVisible = ref(false);
const reviewTask = ref<IProjectTask | null>(null);
const reviewSubmission = ref<ITaskSubmission | null>(null);

async function openReview(row: IProjectTask): Promise<void> {
  const subs = await apiListSubmissions(row.id);
  const pending = subs.find((s) => !s.reviewedAt);
  if (!pending) {
    ElMessage.info('该任务没有待验收提交');
    await load();
    return;
  }
  reviewTask.value = row;
  reviewSubmission.value = pending;
  reviewVisible.value = true;
}

/* ------------------------------------------------------------- 提交记录 / 修订 */

const subsVisible = ref(false);
const subs = ref<ITaskSubmission[]>([]);

async function openSubmissions(row: IProjectTask): Promise<void> {
  subs.value = await apiListSubmissions(row.id);
  subsVisible.value = true;
}

const revVisible = ref(false);
const revisions = ref<IRevisionView[]>([]);

async function openRevisions(row: IProjectTask): Promise<void> {
  revisions.value = await apiListTaskRevisions(row.id);
  revVisible.value = true;
}
</script>

<style scoped lang="scss">
.task-tab {
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

  &__filters {
    margin-bottom: var(--space-3);
  }

  &__overdue {
    color: var(--el-color-danger);
  }

  &__sub-content {
    font-size: 13px;
    color: var(--text-secondary);
    white-space: pre-wrap;
  }

  &__sub-review {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-color-success);
  }

  &__sub-pending {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-color-warning);
  }
}
</style>
