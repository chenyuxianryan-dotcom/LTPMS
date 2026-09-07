<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">字典管理</div>
        <div class="page-header__desc">维护实验项目的枚举值（类别 / 类型 / 学科 / 要求 / 实验者类别）</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增字典项</el-button>
    </div>

    <el-tabs v-model="activeType" @tab-change="loadData">
      <el-tab-pane v-for="item in DICT_TYPES" :key="item.value" :label="item.label" :name="item.value" />
    </el-tabs>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="dictLabel" label="显示值" min-width="160" />
      <el-table-column prop="dictValue" label="存储值" min-width="160" />
      <el-table-column prop="sortOrder" label="排序" width="90" align="center" class-name="tabular-nums" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑字典项' : '新增字典项'" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="字典类型">
          <el-input :model-value="activeTypeLabel" disabled />
        </el-form-item>
        <el-form-item label="显示值" prop="dictLabel">
          <el-input v-model="form.dictLabel" placeholder="如 基础实验" />
        </el-form-item>
        <el-form-item label="存储值" prop="dictValue">
          <el-input v-model="form.dictValue" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { apiCreateDict, apiDeleteDict, apiListDictsByType, apiUpdateDict } from '@/api/dict';
import { DICT_TYPES } from '@/constants';
import type { IDictItem } from '@/types/api';

const activeType = ref<string>('category');
const loading = ref(false);
const records = ref<IDictItem[]>([]);
const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({ id: 0, dictLabel: '', dictValue: '', sortOrder: 0, status: 1 });

const activeTypeLabel = computed(() => DICT_TYPES.find((item) => item.value === activeType.value)?.label ?? '');

const rules: FormRules = {
  dictLabel: [{ required: true, message: '请输入显示值', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入存储值', trigger: 'blur' }],
};

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    records.value = await apiListDictsByType(activeType.value);
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editing.value = false;
  Object.assign(form, { id: 0, dictLabel: '', dictValue: '', sortOrder: records.value.length, status: 1 });
  dialogVisible.value = true;
}

function openEdit(row: IDictItem): void {
  editing.value = true;
  Object.assign(form, row);
  dialogVisible.value = true;
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (editing.value) {
      await apiUpdateDict(form.id, { ...form });
    } else {
      await apiCreateDict({ dictType: activeType.value, ...form });
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: IDictItem): Promise<void> {
  await ElMessageBox.confirm(`确定删除字典项「${row.dictLabel}」吗？`, '删除确认', { type: 'warning' });
  await apiDeleteDict(row.id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(() => {
  void loadData();
});
</script>
