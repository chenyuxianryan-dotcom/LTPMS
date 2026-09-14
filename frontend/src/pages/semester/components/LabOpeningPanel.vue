<template>
  <div class="opening-panel">
    <div class="opening-panel__toolbar">
      <div class="opening-panel__actions">
        <el-button type="primary" @click="batchVisible = true">批量设置</el-button>
        <el-button plain @click="copyVisible = true">从上学期复制</el-button>
        <el-button plain @click="exceptionVisible = true">例外日管理</el-button>
      </div>
      <span class="opening-panel__hint">
        覆盖式批量 / 复制；单间微调点击行首箭头展开时段
        <template v-if="selectedCodes.length"> · 已选 {{ selectedCodes.length }} 间</template>
      </span>
    </div>

    <el-table
      v-loading="loading"
      :data="labs"
      row-key="labCode"
      border
      stripe
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="opening-panel__expand">
            <div v-if="slotsOf(row.labCode).length" class="opening-panel__slots">
              <template v-for="slot in slotsOf(row.labCode)" :key="slot.id">
                <div class="opening-panel__slot" :class="{ 'opening-panel__slot--off': slot.enabled !== 1 }">
                  <span class="opening-panel__slot-time">
                    周{{ WEEKDAY_LABELS[slot.dayOfWeek - 1] }} 第 {{ slot.startSection }}-{{ slot.endSection }} 节
                  </span>
                  <span>{{ slot.weekRange }} 周</span>
                  <el-tag size="small" effect="plain">{{ WEEK_TYPE_MAP[slot.weekType] ?? slot.weekType }}</el-tag>
                  <el-tag size="small" type="info" effect="light">{{ slot.audience }}</el-tag>
                  <el-tag v-if="slot.enabled !== 1" size="small" type="warning" effect="light">停用</el-tag>
                  <span v-if="slot.remark" class="opening-panel__slot-remark">{{ slot.remark }}</span>
                  <span class="opening-panel__slot-ops">
                    <el-button link type="primary" @click="openSlotDialog(row, slot)">编辑</el-button>
                    <el-button link type="danger" @click="handleDeleteSlot(slot)">删除</el-button>
                  </span>
                </div>
              </template>
            </div>
            <div v-else class="opening-panel__empty">暂无开放时段</div>
            <el-button size="small" type="primary" plain @click="openSlotDialog(row, null)">添加时段</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="labCode" label="实验室编号" width="110" />
      <el-table-column prop="labName" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusOf(row.labCode).type" size="small" effect="light">{{ statusOf(row.labCode).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开放对象" width="110">
        <template #default="{ row }">{{ overviewMap.get(row.labCode)?.audience || '—' }}</template>
      </el-table-column>
      <el-table-column label="时段数" width="80" align="right">
        <template #default="{ row }">{{ slotsOf(row.labCode).length }}</template>
      </el-table-column>
      <el-table-column label="开放节时" width="90" align="right">
        <template #default="{ row }">{{ overviewMap.get(row.labCode)?.openSlotHours ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="矛盾" width="90">
        <template #default="{ row }">
          <el-tag v-if="(overviewMap.get(row.labCode)?.conflictCount ?? 0) > 0" type="danger" size="small" effect="dark">
            {{ overviewMap.get(row.labCode)?.conflictCount }} 处
          </el-tag>
          <span v-else class="opening-panel__muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <LabOpenSlotDialog
      v-model="slotVisible"
      :semester-id="semesterId"
      :lab-code="slotLab?.labCode ?? ''"
      :lab-name="slotLab?.labName"
      :editing="slotEditing"
      @saved="reload"
    />
    <LabOpenBatchDialog
      v-model="batchVisible"
      :semester-id="semesterId"
      :labs="labs"
      :selected-codes="selectedCodes"
      @saved="reload"
    />
    <LabOpenCopyDialog v-model="copyVisible" :semester-id="semesterId" :semesters="semesters" :labs="labs" @saved="reload" />
    <LabOpenExceptionDialog
      v-model="exceptionVisible"
      :semester-id="semesterId"
      :labs="labs"
      @changed="reload"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiDeleteLabOpening, apiGetLabOpeningOverview, apiListLabOpenings } from '@/api/labOpening';
import { apiListLaboratories } from '@/api/laboratory';
import { apiListSemesters } from '@/api/semester';
import { WEEKDAY_LABELS, WEEK_TYPE_MAP } from '@/constants';
import type { ILabOpening, ILaboratory, ISemester, IOpenOverviewItem } from '@/types/api';
import LabOpenBatchDialog from './LabOpenBatchDialog.vue';
import LabOpenCopyDialog from './LabOpenCopyDialog.vue';
import LabOpenExceptionDialog from './LabOpenExceptionDialog.vue';
import LabOpenSlotDialog from './LabOpenSlotDialog.vue';

const props = defineProps<{ semesterId: number }>();
const emit = defineEmits<{ changed: [] }>();

const loading = ref(false);
const labs = ref<ILaboratory[]>([]);
const openings = ref<ILabOpening[]>([]);
const overviewMap = ref<Map<string, IOpenOverviewItem>>(new Map());
const semesters = ref<ISemester[]>([]);
const selectedCodes = ref<string[]>([]);

const slotVisible = ref(false);
const slotLab = ref<ILaboratory | null>(null);
const slotEditing = ref<ILabOpening | null>(null);
const batchVisible = ref(false);
const copyVisible = ref(false);
const exceptionVisible = ref(false);

const slotsByLab = computed(() => {
  const map = new Map<string, ILabOpening[]>();
  for (const item of openings.value) {
    const list = map.get(item.labCode) ?? [];
    list.push(item);
    map.set(item.labCode, list);
  }
  return map;
});

function slotsOf(labCode: string): ILabOpening[] {
  return slotsByLab.value.get(labCode) ?? [];
}

function statusOf(labCode: string): { label: string; type: 'success' | 'info' | 'warning' } {
  const rows = slotsOf(labCode);
  if (!rows.length) return { label: '未配置', type: 'info' };
  return rows.some((row) => row.enabled === 1) ? { label: '已配置', type: 'success' } : { label: '已停用', type: 'warning' };
}

function handleSelectionChange(rows: ILaboratory[]): void {
  selectedCodes.value = rows.map((row) => row.labCode);
}

async function reload(): Promise<void> {
  loading.value = true;
  try {
    const [labPage, openingList, overview, semesterList] = await Promise.all([
      apiListLaboratories({ page: 1, size: 200 }),
      apiListLabOpenings({ semesterId: props.semesterId }),
      apiGetLabOpeningOverview(props.semesterId),
      apiListSemesters(),
    ]);
    labs.value = labPage.records;
    openings.value = openingList;
    overviewMap.value = new Map(overview.labs.map((item) => [item.labCode, item]));
    semesters.value = semesterList;
  } finally {
    loading.value = false;
  }
}

function openSlotDialog(lab: ILaboratory, slot: ILabOpening | null): void {
  slotLab.value = lab;
  slotEditing.value = slot;
  slotVisible.value = true;
}

async function handleDeleteSlot(slot: ILabOpening): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除 周${WEEKDAY_LABELS[slot.dayOfWeek - 1]} 第 ${slot.startSection}-${slot.endSection} 节的开放时段吗？`,
    '删除确认',
    { type: 'warning' },
  );
  await apiDeleteLabOpening(slot.id);
  ElMessage.success('删除成功');
  await reload();
  emit('changed');
}

watch(() => props.semesterId, () => {
  selectedCodes.value = [];
  void reload();
});

onMounted(() => {
  void reload();
});
</script>

<style scoped lang="scss">
.opening-panel {
  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-3);
  }

  &__hint {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__expand {
    padding: var(--space-2) var(--space-3) var(--space-2) 60px;
  }

  &__slots {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    margin-bottom: var(--space-2);
  }

  &__slot {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 13px;

    &--off {
      opacity: 0.55;
    }
  }

  &__slot-time {
    font-weight: 600;
    min-width: 150px;
  }

  &__slot-remark {
    color: var(--el-text-color-secondary);
  }

  &__slot-ops {
    margin-left: auto;
  }

  &__empty {
    margin-bottom: var(--space-2);
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__muted {
    color: var(--el-text-color-placeholder);
  }
}
</style>
