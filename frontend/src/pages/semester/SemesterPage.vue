<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">学期管理</div>
        <div class="page-header__desc">学期状态由起止日期自动计算：进行中 / 未开始 / 已结束</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建学期</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="semesterName" label="学期" width="160">
        <template #default="{ row }">
          <span>{{ row.semesterName }}</span>
          <el-tag v-if="row.status === 'ACTIVE'" type="success" size="small" effect="light" style="margin-left: 8px">当前</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startDate" label="开始日期" width="130" />
      <el-table-column prop="endDate" label="结束日期" width="130" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="SEMESTER_STATUS_MAP[row.status]?.tagType ?? 'info'" effect="light">
            {{ SEMESTER_STATUS_MAP[row.status]?.label ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'UPCOMING'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-tooltip v-else content="仅未开始的学期可编辑" placement="top">
            <span><el-button link type="info" disabled>编辑</el-button></span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑学期' : '新建学期'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="学年" prop="academicYear">
          <el-input v-model="form.academicYear" :disabled="editing" placeholder="如 2026-2027" />
        </el-form-item>
        <el-form-item label="学期序号" prop="semesterNum">
          <el-radio-group v-model="form.semesterNum" :disabled="editing">
            <el-radio-button :value="1">第 1 学期</el-radio-button>
            <el-radio-button :value="2">第 2 学期</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" />
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
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { apiCreateSemester, apiListSemesters, apiUpdateSemester } from '@/api/semester';
import { SEMESTER_STATUS_MAP } from '@/constants';
import type { ISemester } from '@/types/api';

const loading = ref(false);
const records = ref<ISemester[]>([]);
const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({ id: 0, academicYear: '', semesterNum: 1, startDate: '', endDate: '' });

const rules: FormRules = {
  academicYear: [{ required: true, message: '请输入学年', trigger: 'blur' }],
  semesterNum: [{ required: true, message: '请选择学期序号', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
};

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    records.value = await apiListSemesters();
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editing.value = false;
  Object.assign(form, { id: 0, academicYear: '', semesterNum: 1, startDate: '', endDate: '' });
  dialogVisible.value = true;
}

function openEdit(row: ISemester): void {
  editing.value = true;
  Object.assign(form, { id: row.id, academicYear: row.academicYear, semesterNum: row.semesterNum, startDate: row.startDate, endDate: row.endDate });
  dialogVisible.value = true;
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (form.endDate <= form.startDate) {
    ElMessage.warning('结束日期必须晚于开始日期');
    return;
  }
  saving.value = true;
  try {
    if (editing.value) {
      await apiUpdateSemester(form.id, { startDate: form.startDate, endDate: form.endDate });
    } else {
      await apiCreateSemester({ ...form });
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  void loadData();
});
</script>
