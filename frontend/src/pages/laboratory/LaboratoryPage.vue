<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">实验室管理</div>
        <div class="page-header__desc">维护实验室基础信息，支持 Excel 批量导入</div>
      </div>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索编号 / 名称 / 负责人" clearable style="width: 240px" @keyup.enter="handleSearch" />
        <el-button :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建实验室</el-button>
        <el-button :icon="Upload" @click="importVisible = true">批量导入</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="labCode" label="实验室编号" width="140" />
      <el-table-column prop="labName" label="实验室名称" min-width="180" />
      <el-table-column prop="manager" label="负责人" width="120" />
      <el-table-column prop="deviceCount" label="设备数" width="100" align="right" class-name="tabular-nums" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="success" @click="openArchive(row)">AI 档案</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50]"
      class="page-container__pagination"
      @current-change="loadData"
      @size-change="loadData"
    />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑实验室' : '新建实验室'" width="480px">
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
      title="实验室导入"
      action="/laboratories/import"
      template-url="/laboratories/import/template"
      @success="loadData"
    />

    <!-- 实验台数字档案（AI 生成，无档案降级显示统计） -->
    <ArchiveDialog
      v-model="archiveVisible"
      entity-type="laboratory"
      :entity-id="archiveTarget?.id ?? null"
      :entity-title="archiveTarget ? `${archiveTarget.labCode} ${archiveTarget.labName}` : ''"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { Plus, Search, Upload } from '@element-plus/icons-vue';
import ArchiveDialog from '@/components/ArchiveDialog.vue';
import ImportDialog from '@/components/ImportDialog.vue';
import { apiCreateLaboratory, apiDeleteLaboratory, apiListLaboratories, apiUpdateLaboratory } from '@/api/laboratory';
import type { ILaboratory } from '@/types/api';

const keyword = ref('');
const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const records = ref<ILaboratory[]>([]);

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
  page.value = 1;
  void loadData();
}

function openCreate(): void {
  editing.value = false;
  Object.assign(form, { id: 0, labCode: '', labName: '', manager: '', deviceCount: 0, status: 1 });
  dialogVisible.value = true;
}

function openEdit(row: ILaboratory): void {
  editing.value = true;
  Object.assign(form, row);
  dialogVisible.value = true;
}

const archiveVisible = ref(false);
const archiveTarget = ref<ILaboratory | null>(null);

function openArchive(row: ILaboratory): void {
  archiveTarget.value = row;
  archiveVisible.value = true;
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
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
  await ElMessageBox.confirm(`确定删除实验室「${row.labName}」吗？有关联数据时将无法删除。`, '删除确认', { type: 'warning' });
  await apiDeleteLaboratory(row.id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(() => {
  void loadData();
});
</script>

<style scoped lang="scss">
.page-container__pagination {
  margin-top: var(--space-4);
  justify-content: flex-end;
}
</style>
