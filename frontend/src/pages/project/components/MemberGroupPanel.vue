<template>
  <div class="group-panel">
    <div class="group-panel__bar">
      <span class="group-panel__title">实验分组</span>
      <el-select
        v-model="experimentId"
        class="group-panel__picker"
        placeholder="选择实验"
        :loading="catalogLoading"
        @change="load"
      >
        <el-option
          v-for="item in experiments"
          :key="item.id"
          :label="`${item.experimentName}（每组 ${item.groupSize} 人）`"
          :value="item.id"
        />
      </el-select>
      <template v-if="canManage && experimentId">
        <el-button type="primary" :icon="Refresh" :loading="acting" @click="handleAutoGroup">
          {{ plan?.groups.length ? '按目录基准重排' : '一键均分' }}
        </el-button>
        <el-button :icon="Plus" @click="openCreate">新建组</el-button>
      </template>
      <span class="group-panel__hint">每组人数取自「实验目录」设置，不可在此手填</span>
    </div>

    <el-alert
      v-if="inconsistent"
      type="warning"
      :closable="false"
      show-icon
      :title="inconsistentText"
      class="group-panel__notice"
    />
    <el-alert
      v-else-if="plan && plan.memberCount === 0"
      type="info"
      :closable="false"
      show-icon
      title="课题暂无成员，请先在上方名单区添加/导入成员，再回来分组"
      class="group-panel__notice"
    />

    <div v-loading="catalogLoading || loading">
      <el-empty
        v-if="!catalogLoading && experiments.length === 0"
        :image-size="60"
        description="该课程实验目录为空 —— 先到「实验目录」Tab 新建或导入实验，才能按每组人数分组"
      />
      <el-empty
        v-else-if="!loading && plan && plan.groups.length === 0"
        :image-size="60"
        :description="canManage
          ? `「${plan.experimentName}」尚未分组，点「一键均分」按每组 ${plan.baselineSize} 人自动分组`
          : `「${plan.experimentName}」尚未分组，等待教师按每组 ${plan.baselineSize} 人分组`"
      />
      <div v-else-if="plan" class="group-panel__summary">
        成员 {{ plan.memberCount }} 人 · 已分组 {{ plan.groupedCount }} · 未分组 {{ plan.ungroupedCount }} ·
        组数 {{ plan.actualGroupCount }}（按每组 {{ plan.baselineSize }} 人应 {{ plan.expectedGroupCount }} 组）
      </div>

      <div v-if="plan && plan.groups.length" class="group-panel__grid">
        <div v-for="group in plan.groups" :key="group.id" class="group-panel__card">
          <div class="group-panel__card-head">
            <span class="group-panel__card-name">{{ group.groupName }}</span>
            <el-tag size="small" effect="plain">{{ group.studentIds.length }} 人</el-tag>
            <el-tag v-if="group.studentIds.length > plan.baselineSize" size="small" type="danger">
              超每组 {{ plan.baselineSize }} 人
            </el-tag>
            <el-button v-if="canManage" link type="danger" :icon="Delete" @click="handleDeleteGroup(group)">
              删除
            </el-button>
          </div>
          <div class="group-panel__members">
            <el-tag
              v-for="studentId in group.studentIds"
              :key="studentId"
              :closable="canManage"
              effect="light"
              @close="handleRemoveMember(group, studentId)"
            >
              {{ nameOf(studentId) }}
            </el-tag>
            <span v-if="group.studentIds.length === 0" class="group-panel__void">空组</span>
          </div>
          <el-button v-if="canManage" link type="primary" :icon="Plus" @click="openAssign(group)">加人</el-button>
        </div>

        <div v-if="canManage && plan.ungroupedCount" class="group-panel__card group-panel__card--pool">
          <div class="group-panel__card-head">
            <span class="group-panel__card-name">未分组</span>
            <el-tag size="small" type="warning" effect="plain">{{ plan.ungroupedCount }} 人</el-tag>
          </div>
          <div class="group-panel__members">
            <el-tag v-for="studentId in ungroupedIds" :key="studentId" effect="plain">
              {{ nameOf(studentId) }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新建分组" width="420px">
      <el-form label-width="60px" @submit.prevent>
        <el-form-item label="组名">
          <el-input v-model="createName" maxlength="64" placeholder="如 A 组 / 第一组" @keyup.enter="handleCreate" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!createName.trim()" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" :title="`加入「${assignTarget?.groupName ?? ''}」`" width="420px">
      <el-select v-model="assignIds" multiple filterable class="group-panel__assign" placeholder="选择学生（已在其他组的学生会被移过来）">
        <el-option
          v-for="member in assignCandidates"
          :key="member.studentId"
          :label="`${member.realName}（${member.studentNo}）`"
          :value="member.studentId"
        />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :disabled="assignIds.length === 0" :loading="acting" @click="handleAssign">
          移入 {{ assignIds.length || '' }} 人
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { Delete, Plus, Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  apiAssignGroupMembers,
  apiAutoGroup,
  apiContainerExperiments,
  apiCreateGroup,
  apiDeleteGroup,
  apiListGroupPlan,
  apiRemoveGroupMember,
} from '@/api/project';
import type { IExperiment, IProject, IProjectGroup, IProjectGroupPlan, IProjectMember } from '@/types/api';

/**
 * 成员板块的实验分组面板：分组按「每个实验各一套」组织，每组人数唯一事实源是实验目录 group_size，
 * 目录基准变更只做不一致提示 + 显式重排（重排覆盖式，二次确认），不自动改写教师的手工分组。
 */
const props = defineProps<{ project: IProject; canManage: boolean; members: IProjectMember[] }>();

const experiments = ref<IExperiment[]>([]);
const catalogLoading = ref(false);
const loading = ref(false);
const acting = ref(false);
const experimentId = ref<number | null>(null);
const plan = ref<IProjectGroupPlan | null>(null);

async function loadCatalog(): Promise<void> {
  catalogLoading.value = true;
  try {
    experiments.value = await apiContainerExperiments(props.project.id);
  } finally {
    catalogLoading.value = false;
  }
  if (experiments.value.length && experimentId.value == null) {
    experimentId.value = experiments.value[0].id;
    await load();
  }
}

async function load(): Promise<void> {
  if (experimentId.value == null) {
    plan.value = null;
    return;
  }
  loading.value = true;
  try {
    plan.value = await apiListGroupPlan(props.project.id, experimentId.value);
  } finally {
    loading.value = false;
  }
}

onMounted(loadCatalog);

// 名单增删后组数概览需跟随刷新（服务端计数为准）
watch(
  () => props.members.length,
  () => {
    if (plan.value) void load();
  },
);

function nameOf(studentId: number): string {
  const member = props.members.find((item) => item.studentId === studentId);
  return member?.realName || `学生#${studentId}`;
}

const groupedIds = computed(() =>
  (plan.value?.groups ?? []).flatMap((group) => group.studentIds),
);

const ungroupedIds = computed(() =>
  props.members.map((m) => m.studentId).filter((id) => !groupedIds.value.includes(id)),
);

const inconsistent = computed(() => {
  const current = plan.value;
  if (!current || current.memberCount === 0) return false;
  return current.mismatch || current.expectedGroupCount !== current.actualGroupCount;
});

const inconsistentText = computed(() => {
  const current = plan.value;
  if (!current) return '';
  const parts: string[] = [];
  if (current.mismatch) {
    parts.push(`目录现值每组 ${current.baselineSize} 人，当前分组的建组基准或人数已不符`);
  }
  if (current.expectedGroupCount !== current.actualGroupCount) {
    parts.push(`按目录应分 ${current.expectedGroupCount} 组，当前 ${current.actualGroupCount} 组`);
  }
  if (props.canManage) {
    parts.push('点「按目录基准重排」可覆盖重排');
  }
  return parts.join('；');
});

async function handleAutoGroup(): Promise<void> {
  if (experimentId.value == null) return;
  const current = plan.value;
  if (current?.groups.length) {
    try {
      await ElMessageBox.confirm(
        `重排将清空「${current.experimentName}」当前 ${current.actualGroupCount} 个分组及组内归属，` +
          `按目录每组 ${current.baselineSize} 人重新均分。`,
        '按目录基准重排',
        { type: 'warning', confirmButtonText: '重排', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
  }
  acting.value = true;
  try {
    plan.value = await apiAutoGroup(props.project.id, experimentId.value);
    ElMessage.success(`已按每组 ${plan.value?.baselineSize ?? ''} 人分为 ${plan.value?.actualGroupCount ?? 0} 组`);
  } finally {
    acting.value = false;
  }
}

/* ------------------------------------------------------------- 手动建组 */

const createVisible = ref(false);
const createName = ref('');

function openCreate(): void {
  createName.value = '';
  createVisible.value = true;
}

async function handleCreate(): Promise<void> {
  if (experimentId.value == null || !createName.value.trim()) return;
  acting.value = true;
  try {
    await apiCreateGroup(props.project.id, { experimentId: experimentId.value, groupName: createName.value.trim() });
    ElMessage.success('已新建分组');
    createVisible.value = false;
    await load();
  } finally {
    acting.value = false;
  }
}

async function handleDeleteGroup(group: IProjectGroup): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除「${group.groupName}」？组内 ${group.studentIds.length} 名学生将退回未分组，不移出课题。`,
      '删除分组',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await apiDeleteGroup(props.project.id, group.id);
  ElMessage.success('已删除分组');
  await load();
}

/* ------------------------------------------------------------- 组内加减人 */

const assignVisible = ref(false);
const assignTarget = ref<IProjectGroup | null>(null);
const assignIds = ref<number[]>([]);

const assignCandidates = computed(() =>
  props.members.filter((m) => !assignTarget.value?.studentIds.includes(m.studentId)),
);

function openAssign(group: IProjectGroup): void {
  assignTarget.value = group;
  assignIds.value = [];
  assignVisible.value = true;
}

async function handleAssign(): Promise<void> {
  if (!assignTarget.value) return;
  acting.value = true;
  try {
    plan.value = await apiAssignGroupMembers(props.project.id, assignTarget.value.id, assignIds.value);
    ElMessage.success('已移入');
    assignVisible.value = false;
  } finally {
    acting.value = false;
  }
}

async function handleRemoveMember(group: IProjectGroup, studentId: number): Promise<void> {
  plan.value = await apiRemoveGroupMember(props.project.id, group.id, studentId);
}
</script>

<style scoped lang="scss">
.group-panel {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);

  &__bar {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-4);
  }

  &__title {
    font-weight: 600;
  }

  &__picker {
    width: 280px;
  }

  &__hint {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__notice {
    margin-bottom: var(--space-4);
  }

  &__summary {
    margin-bottom: var(--space-3);
    font-size: 13px;
    color: var(--text-secondary);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: var(--space-3);
  }

  &__card {
    padding: var(--space-3);
    background: var(--bg-subtle);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);

    &--pool {
      border-style: dashed;
    }
  }

  &__card-head {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-2);
  }

  &__card-name {
    font-weight: 600;
  }

  &__members {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-1) var(--space-2);
    margin-bottom: var(--space-2);
  }

  &__void {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__assign {
    width: 100%;
  }
}
</style>
