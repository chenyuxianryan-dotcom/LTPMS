<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">课表管理</div>
        <div class="page-header__desc">Excel 导入课表，系统按「课程号 + 学期」自动归并生成实验课程</div>
      </div>
      <div class="filter-bar">
        <el-select v-model="semesterId" placeholder="选择学期" style="width: 220px" @change="handleSearch">
          <el-option v-for="item in semesters" :key="item.id" :label="item.semesterName" :value="item.id" />
        </el-select>
        <el-button type="primary" :icon="Upload" :disabled="!semesterId" @click="importVisible = true">导入课表</el-button>
        <el-button type="success" plain :icon="Camera" :disabled="!semesterId" @click="photoImportVisible = true">拍照导入</el-button>
        <el-button type="danger" plain :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          批量删除（{{ selectedIds.length }}）
        </el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="records" border stripe @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="44" />
      <el-table-column prop="courseCode" label="课程号" width="120" />
      <el-table-column prop="courseName" label="课程名" min-width="180" />
      <el-table-column prop="teacherName" label="授课教师" width="110" />
      <el-table-column prop="labCode" label="上课地点" width="120" />
      <el-table-column prop="classNames" label="上课班级" min-width="180" show-overflow-tooltip />
      <el-table-column prop="studentCount" label="人数" width="80" align="right" class-name="tabular-nums" />
      <el-table-column prop="totalHours" label="实验学时" width="100" align="right" class-name="tabular-nums" />
      <el-table-column prop="rawScheduleText" label="上课时间" min-width="150" show-overflow-tooltip />
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

    <ImportDialog
      v-model="importVisible"
      title="课表导入"
      action="/schedules/import"
      template-url="/schedules/import/template"
      preview-url="/schedules/import/preview"
      :extra-data="semesterId ? { semesterId } : undefined"
      @success="loadData"
    />

    <SchedulePhotoImportDialog
      v-if="semesterId"
      v-model="photoImportVisible"
      :semester-id="semesterId"
      @success="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Camera, Upload } from '@element-plus/icons-vue';
import ImportDialog from '@/components/ImportDialog.vue';
import SchedulePhotoImportDialog from '@/components/SchedulePhotoImportDialog.vue';
import { apiBatchDeleteSchedules, apiListSchedules } from '@/api/schedule';
import { apiListSemesters } from '@/api/semester';
import type { ISchedule, ISemester } from '@/types/api';

const semesterId = ref<number | null>(null);
const semesters = ref<ISemester[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const records = ref<ISchedule[]>([]);
const selectedIds = ref<number[]>([]);
const importVisible = ref(false);
const photoImportVisible = ref(false);

async function loadSemesters(): Promise<void> {
  semesters.value = await apiListSemesters();
  // 默认选中当前学期
  const current = semesters.value.find((item) => item.status === 'ACTIVE');
  if (current) {
    semesterId.value = current.id;
  }
  await loadData();
}

async function loadData(): Promise<void> {
  if (!semesterId.value) return;
  loading.value = true;
  try {
    const result = await apiListSchedules({ semesterId: semesterId.value, page: page.value, size: size.value });
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

function handleSelectionChange(rows: ISchedule[]): void {
  selectedIds.value = rows.map((row) => row.id);
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条课表记录吗？`, '批量删除确认', { type: 'warning' });
  await apiBatchDeleteSchedules(selectedIds.value);
  ElMessage.success('删除成功');
  selectedIds.value = [];
  await loadData();
}

onMounted(() => {
  void loadSemesters();
});
</script>

<style scoped lang="scss">
.page-container__pagination {
  margin-top: var(--space-4);
  justify-content: flex-end;
}
</style>
