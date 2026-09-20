<template>
  <div v-loading="loading" class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">我的任务</div>
        <div class="page-header__desc">按课题分组查看老师派发的任务；逾期任务标红提醒</div>
      </div>
      <div class="filter-bar">
        <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 160px" @change="load">
          <el-option v-for="(v, k) in TASK_STATUS_MAP" :key="k" :value="k" :label="v.label" />
        </el-select>
      </div>
    </div>

    <el-empty v-if="!groups.length" description="暂无任务，等老师派发课题任务后会出现在这里" />

    <div v-for="group in groups" :key="group.projectId" class="my-tasks__group">
      <div class="my-tasks__group-head">
        <el-link type="primary" @click="router.push(`/experiments/${group.projectId}?tab=progress`)">
          {{ group.projectName }}
        </el-link>
        <span class="my-tasks__group-count">{{ group.tasks.length }} 项</span>
      </div>

      <section v-for="bucket in bucketsOf(group.tasks)" :key="bucket.key" class="my-tasks__bucket">
        <h4 class="my-tasks__bucket-title">
          {{ bucket.title }}
          <span class="my-tasks__bucket-num">{{ bucket.tasks.length }}</span>
        </h4>
        <div
          v-for="task in bucket.tasks"
          :key="task.id"
          class="my-tasks__card"
          :class="{ 'my-tasks__card--overdue': task.overdue }"
        >
          <div class="my-tasks__card-main">
            <div class="my-tasks__card-title">{{ task.title }}</div>
            <div class="my-tasks__card-desc">{{ task.description || '（无任务说明）' }}</div>
            <div class="my-tasks__card-meta">
              <el-tag size="small" :type="statusOf(task).tagType" effect="light">{{ statusOf(task).label }}</el-tag>
              <span v-if="task.deadline" :class="{ 'my-tasks__danger': task.overdue }">截止 {{ fmtDateTime(task.deadline) }}</span>
              <span v-if="task.datasetId">关联数据 #{{ task.datasetId }}</span>
            </div>
          </div>
          <div class="my-tasks__card-actions">
            <el-button v-if="task.status === 'NOT_STARTED' || task.status === 'REJECTED'" size="small" @click="handleStart(task)">
              开始任务
            </el-button>
            <el-button
              v-if="task.status === 'IN_PROGRESS' || task.status === 'REJECTED'"
              size="small"
              type="primary"
              @click="openSubmit(task)"
            >
              提交
            </el-button>
            <el-button size="small" link @click="openHistory(task)">提交历史</el-button>
          </div>
        </div>
      </section>
    </div>

    <TaskSubmitDialog v-model="submitVisible" :project-id="submitProjectId" :task="submitTask" @done="load" />

    <el-dialog v-model="historyVisible" title="我的提交历史" width="560px">
      <el-timeline v-if="historyItems.length">
        <el-timeline-item
          v-for="h in historyItems"
          :key="h.id"
          :timestamp="fmtFullDateTime(h.submittedAt)"
          :type="h.reviewedAt ? 'info' : 'primary'"
        >
          <div class="my-tasks__hist-content">{{ h.content }}</div>
          <div v-if="h.reviewedAt" class="my-tasks__hist-review">验收意见：{{ h.reviewComment || '通过（无意见）' }}</div>
          <div v-else class="my-tasks__hist-pending">待验收</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="还没有提交记录" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { apiListSubmissions, apiMyTasks, apiStartTask } from '@/api/project';
import { TASK_STATUS_MAP } from '@/constants';
import type { IMyTaskGroup, IProjectTask, ITaskSubmission } from '@/types/api';
import { fmtDateTime, fmtFullDateTime } from './projectUtils';
import TaskSubmitDialog from './components/TaskSubmitDialog.vue';

const router = useRouter();

const loading = ref(false);
const groups = ref<IMyTaskGroup[]>([]);
const statusFilter = ref<string>();

async function load(): Promise<void> {
  loading.value = true;
  try {
    groups.value = await apiMyTasks(statusFilter.value);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

type TBucketKey = 'todo' | 'reviewing' | 'rejected' | 'done';

function bucketsOf(tasks: IProjectTask[]): { key: TBucketKey; title: string; tasks: IProjectTask[] }[] {
  const todo = tasks.filter((t) => t.status === 'NOT_STARTED' || t.status === 'IN_PROGRESS');
  const reviewing = tasks.filter((t) => t.status === 'SUBMITTED');
  const rejected = tasks.filter((t) => t.status === 'REJECTED');
  const done = tasks.filter((t) => t.status === 'ACCEPTED');
  const buckets: { key: TBucketKey; title: string; tasks: IProjectTask[] }[] = [
    { key: 'todo', title: '待办', tasks: todo },
    { key: 'reviewing', title: '已提交待验收', tasks: reviewing },
    { key: 'rejected', title: '被驳回（需重做）', tasks: rejected },
    { key: 'done', title: '已完成', tasks: done },
  ];
  return buckets.filter((b) => b.tasks.length > 0);
}

function statusOf(task: IProjectTask): { label: string; tagType: 'success' | 'warning' | 'danger' | 'info' | 'primary' } {
  const base = TASK_STATUS_MAP[task.status] ?? { label: task.status, tagType: 'info' as const };
  if (task.overdue) return { label: `${base.label}·逾期`, tagType: 'danger' };
  return base;
}

/* ------------------------------------------------------------- 开始/提交 */

async function handleStart(task: IProjectTask): Promise<void> {
  await apiStartTask(task.id);
  ElMessage.success('任务已开始（进行中）');
  await load();
}

const submitVisible = ref(false);
const submitTask = ref<IProjectTask | null>(null);
const submitProjectId = ref<number | null>(null);

function openSubmit(task: IProjectTask): void {
  submitTask.value = task;
  submitProjectId.value = task.projectId;
  submitVisible.value = true;
}

/* ------------------------------------------------------------- 提交历史（本人可见，Q6） */

const historyVisible = ref(false);
const historyItems = ref<ITaskSubmission[]>([]);

async function openHistory(task: IProjectTask): Promise<void> {
  historyItems.value = await apiListSubmissions(task.id);
  historyVisible.value = true;
}
</script>

<style scoped lang="scss">
.my-tasks {
  &__group {
    margin-bottom: var(--space-6);
  }

  &__group-head {
    display: flex;
    gap: var(--space-2);
    align-items: baseline;
    margin-bottom: var(--space-3);
    font-size: 16px;
    font-weight: 700;
  }

  &__group-count {
    font-size: 12px;
    font-weight: 400;
    color: var(--text-tertiary);
  }

  &__bucket {
    margin-bottom: var(--space-4);
  }

  &__bucket-title {
    margin: 0 0 var(--space-2);
    font-size: 13px;
    color: var(--text-secondary);
  }

  &__bucket-num {
    margin-left: 4px;
    color: var(--text-tertiary);
  }

  &__card {
    display: flex;
    gap: var(--space-4);
    align-items: center;
    justify-content: space-between;
    padding: var(--space-3) var(--space-4);
    margin-bottom: var(--space-2);
    background: var(--bg-card, #fff);
    border: 1px solid var(--border-color, var(--el-border-color-light));
    border-radius: 8px;

    &--overdue {
      border-color: var(--el-color-danger-light-5);
    }
  }

  &__card-title {
    font-weight: 600;
  }

  &__card-desc {
    margin-top: 2px;
    overflow: hidden;
    font-size: 12px;
    color: var(--text-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 560px;
  }

  &__card-meta {
    display: flex;
    gap: var(--space-3);
    align-items: center;
    margin-top: 6px;
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__danger {
    font-weight: 600;
    color: var(--el-color-danger);
  }

  &__hist-content {
    font-size: 13px;
    white-space: pre-wrap;
  }

  &__hist-review {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-color-success);
  }

  &__hist-pending {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-color-warning);
  }
}
</style>
