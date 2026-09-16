<template>
  <div class="page-container lab-drum">
    <div class="page-header drum-head">
      <div class="drum-head__id">
        <div class="page-header__title">实验室管理</div>
        <div class="page-header__desc">维护实验室基础信息，支持 Excel 批量导入</div>
      </div>
      <div class="drum-head__console">
        <div class="drum-head__row">
          <div class="drum-nameplate">LTPMS LAB UNIT</div>
          <button
            type="button"
            class="drum-mute"
            :class="{ 'is-muted': drumMuted }"
            :title="drumMuted ? '音效已静音，点击开启' : '音效开启中，点击静音'"
            :aria-label="drumMuted ? '开启音效' : '静音'"
            @click="toggleDrumMute"
          >
            <el-icon :size="15">
              <Mute v-if="drumMuted" />
              <Headset v-else />
            </el-icon>
          </button>
          <div class="drum-led-strip" aria-hidden="true">
            <span
              v-for="n in 16"
              :key="n"
              class="drum-led-strip__dot"
              :class="{ 'is-hit': hitStep === n - 1 }"
              :style="{ '--i': n - 1 }"
            />
          </div>
        </div>
        <div class="filter-bar">
          <el-input v-model="keyword" placeholder="搜索编号 / 名称 / 负责人" clearable style="width: 240px" @keyup.enter="handleSearch" />
          <el-button :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
            批量删除<span v-if="selectedIds.length > 0" style="margin-left: 4px">({{ selectedIds.length }})</span>
          </el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建实验室</el-button>
          <el-button :icon="Upload" @click="openImport">批量导入</el-button>
        </div>
      </div>
    </div>

    <div class="drum-table-panel">
      <el-table
        v-loading="loading"
        :data="records"
        :row-class-name="rowClassName"
        @row-click="handleRowClick"
        @selection-change="(rows: ILaboratory[]) => (selectedIds = rows.map((r) => r.id))"
      >
        <el-table-column type="selection" width="48" :selectable="(row: ILaboratory) => true" />
        <el-table-column prop="labCode" label="实验室编号" width="140" />
        <el-table-column prop="labName" label="实验室名称" min-width="180" />
        <el-table-column prop="manager" label="负责人" width="120" />
        <el-table-column label="设备数" width="130" align="right" class-name="tabular-nums">
          <template #default="{ row }">
            <div class="drum-meter">
              <span class="drum-meter__value">{{ row.deviceCount }}</span>
              <span class="drum-meter__dots" aria-hidden="true">
                <i v-for="n in 10" :key="n" :class="{ 'is-on': n <= deviceDots(row.deviceCount) }" />
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" @click="openArchive(row)">AI 档案</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50]"
      class="page-container__pagination"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <el-dialog v-model="dialogVisible" class="drum-dialog" :title="editing ? '编辑实验室' : '新建实验室'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="实验室编号" prop="labCode">
          <el-input v-model="form.labCode" :disabled="editing" placeholder="如 36-401" />
        </el-form-item>
        <el-form-item label="实验室名称" prop="labName">
          <el-input v-model="form.labName" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.manager" />
        </el-form-item>
        <el-form-item label="设备数">
          <el-input-number v-model="form.deviceCount" :min="0" />
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importVisible"
      class="drum-dialog"
      title="实验室导入"
      action="/laboratories/import"
      template-url="/laboratories/import/template"
      @success="loadData"
    />

    <!-- 实验台数字档案（AI 生成，无档案降级显示统计） -->
    <ArchiveDialog
      v-model="archiveVisible"
      class="drum-dialog"
      entity-type="laboratory"
      :entity-id="archiveTarget?.id ?? null"
      :entity-title="archiveTarget ? `${archiveTarget.labCode} ${archiveTarget.labName}` : ''"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { Delete, Headset, Mute, Plus, Search, Upload } from '@element-plus/icons-vue';
import ArchiveDialog from '@/components/ArchiveDialog.vue';
import ImportDialog from '@/components/ImportDialog.vue';
import { apiCreateLaboratory, apiDeleteLaboratories, apiDeleteLaboratory, apiListLaboratories, apiUpdateLaboratory } from '@/api/laboratory';
import type { ILaboratory } from '@/types/api';
import { drumHitTick, drumMuted, playDrum, toggleDrumMute } from '@/utils/drumKit';

const keyword = ref('');
const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const records = ref<ILaboratory[]>([]);
const selectedIds = ref<number[]>([]);

const dialogVisible = ref(false);
const importVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({ id: 0, labCode: '', labName: '', manager: '', deviceCount: 0, status: 1 });

const rules: FormRules = {
  labCode: [{ required: true, message: '请输入实验室编号', trigger: 'blur' }],
  labName: [{ required: true, message: '请输入实验室名称', trigger: 'blur' }],
};

/** LED 灯带走位：-1 表示尚无命中 */
const hitStep = computed(() => (drumHitTick.value > 0 ? (drumHitTick.value - 1) % 16 : -1));
/** 设备数点阵：每灯 6 台，10 灯满量程 */
function deviceDots(count: number): number {
  return Math.max(0, Math.min(10, Math.ceil(count / 6)));
}

const pulseRowId = ref<number | null>(null);
let pulseTimer: number | undefined;

function rowClassName({ row }: { row: ILaboratory; rowIndex: number }): string {
  const classes: string[] = [];
  if (selectedIds.value.includes(row.id)) {
    classes.push('is-pad-pressed');
  }
  if (pulseRowId.value === row.id) {
    classes.push('is-pad-hit');
  }
  return classes.join(' ');
}

function handleRowClick(row: ILaboratory, _column: unknown, event: Event): void {
  const target = event.target as HTMLElement | null;
  if (target?.closest('button, .el-button, .el-checkbox, input, a')) return;
  playDrum('tom', records.value.indexOf(row));
  pulseRowId.value = row.id;
  window.clearTimeout(pulseTimer);
  pulseTimer = window.setTimeout(() => {
    pulseRowId.value = null;
  }, 300);
}

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const result = await apiListLaboratories({ page: page.value, size: size.value, keyword: keyword.value || undefined });
    records.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  playDrum('rim');
  page.value = 1;
  selectedIds.value = [];
  void loadData();
}

function handlePageChange(): void {
  playDrum('rim');
  void loadData();
}

function handleSizeChange(): void {
  playDrum('rim');
  void loadData();
}

function openCreate(): void {
  playDrum('kick');
  editing.value = false;
  Object.assign(form, { id: 0, labCode: '', labName: '', manager: '', deviceCount: 0, status: 1 });
  dialogVisible.value = true;
}

function openImport(): void {
  playDrum('hat');
  importVisible.value = true;
}

function openEdit(row: ILaboratory): void {
  playDrum('rim');
  editing.value = true;
  Object.assign(form, row);
  dialogVisible.value = true;
}

const archiveVisible = ref(false);
const archiveTarget = ref<ILaboratory | null>(null);

function openArchive(row: ILaboratory): void {
  playDrum('hat');
  archiveTarget.value = row;
  archiveVisible.value = true;
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  playDrum('kick');
  saving.value = true;
  try {
    if (editing.value) {
      await apiUpdateLaboratory(form.id, { ...form });
    } else {
      await apiCreateLaboratory({ ...form });
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: ILaboratory): Promise<void> {
  playDrum('snare');
  await ElMessageBox.confirm(`确定删除实验室「${row.labName}」吗？有关联数据时将无法删除。`, '删除确认', {
    type: 'warning',
    customClass: 'drum-dialog',
  });
  await apiDeleteLaboratory(row.id);
  ElMessage.success('删除成功');
  await loadData();
}

async function handleBatchDelete(): Promise<void> {
  if (selectedIds.value.length === 0) return;
  playDrum('snare');
  await ElMessageBox.confirm(
    `确定批量删除选中的 ${selectedIds.value.length} 间实验室吗？已关联课表的实验室将被跳过。`,
    '批量删除确认',
    { type: 'warning', customClass: 'drum-dialog' },
  );
  const result = await apiDeleteLaboratories(selectedIds.value);
  if (result.failedCount === 0) {
    ElMessage.success(`已成功删除 ${result.successCount} 间实验室`);
  } else {
    ElMessage.warning(
      `成功 ${result.successCount} 条，失败 ${result.failedCount} 条。失败原因：${result.errors.map((e) => e.message).join('；')}`,
    );
  }
  selectedIds.value = [];
  await loadData();
}

onMounted(() => {
  void loadData();
});

onBeforeUnmount(() => {
  window.clearTimeout(pulseTimer);
});
</script>

<style scoped lang="scss">
.page-container__pagination {
  margin-top: var(--space-4);
  justify-content: flex-end;
}
</style>
