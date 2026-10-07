<template>
  <div class="review-inbox">
    <div class="review-inbox__header">
      <h2 class="review-inbox__title">待验收收件箱</h2>
      <el-tag v-if="isAdmin" type="info" effect="plain">管理员只读</el-tag>
      <span class="review-inbox__hint">名下全部课题的待验收提交，按提交时间先到先审；批量仅「通过」，驳回需逐条填写意见</span>
    </div>

    <div class="review-inbox__bar">
      <el-select
        v-model="projectFilter"
        clearable
        placeholder="全部课题"
        style="width: 260px"
        data-testid="inbox-project-filter"
      >
        <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button
        v-if="canManage"
        type="success"
        :disabled="!selection.length"
        :loading="batching"
        data-testid="inbox-batch-accept"
        @click="handleBatchAccept"
      >
        批量通过{{ selection.length ? `（${selection.length}）` : '' }}
      </el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <span class="review-inbox__count">共 {{ rows.length }} 条待验收</span>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      border
      stripe
      row-key="taskId"
      @selection-change="onSelectionChange"
      @expand-change="onExpandChange"
    >
      <el-table-column v-if="canManage" type="selection" width="42" />
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="review-inbox__expand">
            <el-timeline v-if="(subsOf[row.taskId] ?? []).length">
              <el-timeline-item
                v-for="s in subsOf[row.taskId]"
                :key="s.id"
                :timestamp="fmtFullDateTime(s.submittedAt)"
                :type="s.reviewedAt ? 'info' : 'primary'"
              >
                <div><b>{{ s.submitterName }}</b> 提交</div>
                <div class="review-inbox__sub-content">{{ s.content }}</div>
                <div v-if="s.reviewedAt" class="review-inbox__sub-review">
                  验收于 {{ fmtFullDateTime(s.reviewedAt) }}：{{ s.reviewComment || '通过（无意见）' }}
                </div>
                <div v-else class="review-inbox__sub-pending">待验收</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无提交记录" :image-size="50" />
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="projectName" label="课题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="taskTitle" label="任务" min-width="170" show-overflow-tooltip />
      <el-table-column label="学生" width="150">
        <template #default="{ row }">
          {{ row.studentName }}<span class="review-inbox__no">{{ row.studentNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="180">
        <template #default="{ row }">
          {{ fmtFullDateTime(row.submittedAt) }}
          <el-tag v-if="row.overdue" size="small" type="danger" effect="plain">逾期</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交摘要" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag v-if="row.datasetVersionId" size="small" effect="plain" class="review-inbox__ver">
            数据版本 #{{ row.datasetVersionId }}
          </el-tag>
          {{ row.content || '（无说明）' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <template v-if="canManage">
            <el-button link type="success" @click="openReview(row, 'ACCEPT')">通过</el-button>
            <el-button link type="danger" @click="openReview(row, 'REJECT')">驳回</el-button>
          </template>
          <span v-else class="review-inbox__readonly">只读</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          :description="projectFilter ? '该课题暂无待验收提交' : '暂无待验收提交，全部处理完毕'"
          :image-size="80"
        />
      </template>
    </el-table>

    <TaskReviewDialog
      v-model="reviewVisible"
      :task="reviewTask"
      :submission="reviewSubmission"
      :initial-decision="reviewInitial"
      @done="load"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiListSubmissions, apiReviewInbox, apiReviewTask } from '@/api/project';
import { useUserStore } from '@/store/user';
import type { IProjectTask, IReviewInboxItem, ITaskSubmission } from '@/types/api';
import { fmtFullDateTime } from '@/pages/project/projectUtils';
import TaskReviewDialog from '@/pages/project/components/TaskReviewDialog.vue';

const route = useRoute();
const userStore = useUserStore();

const isAdmin = computed(() => userStore.isAdmin);
/** 收件箱写操作仅归属教师；ADMIN 深链只读（沿用 2026-09-20 权限边界收口） */
const canManage = computed(() => !isAdmin.value);

const loading = ref(false);
const batching = ref(false);
const allRows = ref<IReviewInboxItem[]>([]);
const projectFilter = ref<number | undefined>(
  route.query.projectId ? Number(route.query.projectId) : undefined,
);

const rows = computed(() =>
  projectFilter.value ? allRows.value.filter((r) => r.projectId === projectFilter.value) : allRows.value,
);

const projectOptions = computed(() => {
  const seen = new Map<number, string>();
  for (const r of allRows.value) {
    if (!seen.has(r.projectId)) seen.set(r.projectId, r.projectName);
  }
  return [...seen.entries()].map(([id, name]) => ({ id, name }));
});

const subsOf = ref<Record<number, ITaskSubmission[]>>({});

async function load(): Promise<void> {
  loading.value = true;
  try {
    const openIds = Object.keys(subsOf.value).map(Number);
    allRows.value = await apiReviewInbox();
    subsOf.value = {};
    // 刷新后仍处展开态的行：重新拉取提交历史，避免展开区显示空缓存
    for (const id of openIds) {
      if (allRows.value.some((r) => r.taskId === id)) {
        subsOf.value[id] = await apiListSubmissions(id);
      }
    }
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/* ---------------------------------------------------------------- 展开提交内容 */

async function onExpandChange(row: IReviewInboxItem, expanded: IReviewInboxItem[]): Promise<void> {
  const isOpen = Array.isArray(expanded) ? expanded.some((r) => r.taskId === row.taskId) : true;
  if (!isOpen || subsOf.value[row.taskId]) return;
  subsOf.value[row.taskId] = await apiListSubmissions(row.taskId);
}

/* ---------------------------------------------------------------- 单条验收（通过/驳回） */

const reviewVisible = ref(false);
const reviewInitial = ref<'ACCEPT' | 'REJECT'>('ACCEPT');
const reviewTask = ref<IProjectTask | null>(null);
const reviewSubmission = ref<ITaskSubmission | null>(null);

function openReview(row: IReviewInboxItem, decision: 'ACCEPT' | 'REJECT'): void {
  reviewTask.value = {
    id: row.taskId,
    projectId: row.projectId,
    title: row.taskTitle,
    assigneeStudentId: row.studentId,
    assigneeName: row.studentName,
    status: 'SUBMITTED',
    overdue: row.overdue,
    createdAt: row.submittedAt,
  };
  reviewSubmission.value = {
    id: row.submissionId,
    taskId: row.taskId,
    submitterStudentId: row.studentId,
    submitterName: row.studentName,
    content: row.content ?? '',
    datasetVersionId: row.datasetVersionId,
    submittedAt: row.submittedAt,
  };
  reviewInitial.value = decision;
  reviewVisible.value = true;
}

/* ---------------------------------------------------------------- 批量通过 */

const selection = ref<IReviewInboxItem[]>([]);

function onSelectionChange(rowsSelected: IReviewInboxItem[]): void {
  selection.value = rowsSelected;
}

async function handleBatchAccept(): Promise<void> {
  const targets = [...selection.value];
  if (!targets.length) return;
  try {
    await ElMessageBox.confirm(
      `批量通过 ${targets.length} 条提交？通过后学生将收到验收通知，操作不可撤销。`,
      '批量通过',
      { type: 'warning', confirmButtonText: `通过 ${targets.length} 条`, cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  batching.value = true;
  const failures: { label: string; reason: string }[] = [];
  let ok = 0;
  try {
    for (const row of targets) {
      try {
        await apiReviewTask(row.taskId, { decision: 'ACCEPT' });
        ok += 1;
      } catch (err) {
        failures.push({
          label: `${row.projectName} / ${row.taskTitle}（${row.studentName ?? ''}）`,
          reason: err instanceof Error ? err.message : String(err),
        });
      }
    }
  } finally {
    batching.value = false;
  }
  if (failures.length) {
    await ElMessageBox.alert(
      `<div>成功 ${ok} 条，失败 ${failures.length} 条：</div><ul>${failures
        .map((f) => `<li>${f.label}：${f.reason}</li>`)
        .join('')}</ul>`,
      '批量通过结果',
      { dangerouslyUseHTMLString: true, type: 'warning' },
    );
  } else {
    ElMessage.success(`已批量通过 ${ok} 条提交`);
  }
  await load();
}
</script>

<style scoped lang="scss">
.review-inbox {
  padding: var(--space-4);

  &__header {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-3);
  }

  &__title {
    margin: 0;
    font-size: 18px;
  }

  &__hint {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__bar {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-3);
  }

  &__count {
    margin-left: auto;
    font-size: 13px;
    color: var(--text-secondary);
  }

  &__no {
    margin-left: 6px;
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__ver {
    margin-right: 6px;
  }

  &__readonly {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__expand {
    padding: var(--space-3) var(--space-5);
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
