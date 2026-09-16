<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">教师管理</div>
        <div class="page-header__desc">教师账号由管理员单独新增或批量导入；新增/重置后为默认密码 Ltpms@{工号}</div>
      </div>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索姓名 / 工号" clearable style="width: 220px" @keyup.enter="handleSearch" />
        <el-button :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          批量删除<span v-if="selectedIds.length > 0" style="margin-left: 4px">({{ selectedIds.length }})</span>
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增教师</el-button>
        <el-button :icon="Upload" @click="importVisible = true">批量导入</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="records" border stripe @selection-change="(rows: ITeacher[]) => (selectedIds = rows.map((r) => r.id))">
      <el-table-column type="selection" width="48" :selectable="(row: ITeacher) => true" />
      <el-table-column label="头像" width="76" align="center">
        <template #default="{ row }">
          <el-tooltip content="点击更换头像" placement="top">
            <el-avatar
              :size="40"
              :src="resolveAvatarUrl(row.id, row.avatar)"
              class="teacher-avatar"
              @click="openAvatarPicker(row)"
            >
              {{ row.realName?.slice(0, 1) }}
            </el-avatar>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="工号" width="120" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="department" label="所属院系" min-width="160" />
      <el-table-column prop="phone" label="联系电话" width="140" />
      <el-table-column label="默认密码" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.isDefaultPwd === 1" type="warning" effect="light">未修改</el-tag>
          <el-tag v-else type="success" effect="light">已修改</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="(value: string | number | boolean) => handleStatusChange(row, value)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="handleResetPassword(row)">重置密码</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑教师' : '新增教师'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="工号" prop="username">
          <el-input v-model="form.username" :disabled="editing" placeholder="如 2019001" maxlength="32" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" maxlength="32" />
        </el-form-item>
        <el-form-item label="所属院系" prop="department">
          <el-input v-model="form.department" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" maxlength="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <AvatarPickerDialog v-model="avatarPickerVisible" :user="pickerUser" @saved="onAvatarSaved" />
    <ImportDialog
      v-model="importVisible"
      title="教师导入"
      action="/teachers/import"
      template-url="/teachers/import/template"
      @success="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { Delete, Plus, Search, Upload } from '@element-plus/icons-vue';
import AvatarPickerDialog from '@/components/AvatarPickerDialog.vue';
import ImportDialog from '@/components/ImportDialog.vue';
import { apiCreateTeacher, apiDeleteTeachers, apiListTeachers, apiResetTeacherPassword, apiUpdateTeacher, apiUpdateTeacherStatus } from '@/api/teacher';
import { resolveAvatarUrl } from '@/utils/avatar';
import type { ITeacher } from '@/types/api';

const keyword = ref('');
const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const records = ref<ITeacher[]>([]);
const selectedIds = ref<number[]>([]);
const importVisible = ref(false);
const avatarPickerVisible = ref(false);
const pickerUser = ref<{ id: number; realName: string; avatar?: string | null } | null>(null);

const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({ id: 0, username: '', realName: '', department: '', phone: '' });

const rules: FormRules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
};

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const result = await apiListTeachers({ page: page.value, size: size.value, keyword: keyword.value || undefined });
    records.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  page.value = 1;
  selectedIds.value = [];
  void loadData();
}

/** 打开新增教师弹窗（初始密码 Ltpms@工号） */
function openCreate(): void {
  editing.value = false;
  Object.assign(form, { id: 0, username: '', realName: '', department: '', phone: '' });
  dialogVisible.value = true;
}

/** 打开编辑教师弹窗（工号不可修改） */
function openEdit(row: ITeacher): void {
  editing.value = true;
  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    department: row.department ?? '',
    phone: row.phone ?? '',
  });
  dialogVisible.value = true;
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (editing.value) {
      await apiUpdateTeacher(form.id, { realName: form.realName, department: form.department, phone: form.phone });
      ElMessage.success('保存成功');
    } else {
      await apiCreateTeacher({ username: form.username, realName: form.realName, department: form.department, phone: form.phone });
      ElMessage.success(`已新增教师「${form.realName}」，初始密码 Ltpms@${form.username}`);
    }
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

/** 打开头像选择器（管理员为教师更换头像） */
function openAvatarPicker(row: ITeacher): void {
  pickerUser.value = { id: row.id, realName: row.realName, avatar: row.avatar };
  avatarPickerVisible.value = true;
}

function onAvatarSaved(avatar: string): void {
  if (pickerUser.value) {
    const row = records.value.find((r) => r.id === pickerUser.value?.id);
    if (row) row.avatar = avatar;
  }
}

async function handleStatusChange(row: ITeacher, value: string | number | boolean): Promise<void> {
  const status: 0 | 1 = value === true || value === 1 ? 1 : 0;
  const action = status === 1 ? '启用' : '禁用';
  await ElMessageBox.confirm(`确定${action}教师「${row.realName}」的账号吗？`, '确认操作', { type: 'warning' });
  await apiUpdateTeacherStatus(row.id, status);
  row.status = status;
  ElMessage.success(`${action}成功`);
}

async function handleResetPassword(row: ITeacher): Promise<void> {
  await ElMessageBox.confirm(`确定重置「${row.realName}」的密码吗？重置后为默认密码 Ltpms@${row.username}。`, '重置确认', { type: 'warning' });
  await apiResetTeacherPassword(row.id);
  ElMessage.success('密码已重置');
  await loadData();
}

async function handleBatchDelete(): Promise<void> {
  if (selectedIds.value.length === 0) return;
  await ElMessageBox.confirm(
    `确定批量删除选中的 ${selectedIds.value.length} 位教师吗？已关联课表/课程的教师将被跳过。`,
    '批量删除确认',
    { type: 'warning' },
  );
  const result = await apiDeleteTeachers(selectedIds.value);
  if (result.failedCount === 0) {
    ElMessage.success(`已成功删除 ${result.successCount} 位教师`);
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
</script>

<style scoped lang="scss">
.page-container__pagination {
  margin-top: var(--space-4);
  justify-content: flex-end;
}

// 教师头像：可点击更换
.teacher-avatar {
  cursor: pointer;
  transition:
    box-shadow 0.2s ease,
    transform 0.2s ease;

  &:hover {
    box-shadow: 0 0 0 3px var(--brand-100);
    transform: scale(1.08);
  }
}
</style>
