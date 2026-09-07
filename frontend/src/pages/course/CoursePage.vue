<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">课程管理</div>
        <div class="page-header__desc">{{ isAdmin ? '全校实验课程' : '我任教的实验课程' }}（由课表导入自动生成）</div>
      </div>
      <el-select v-model="semesterId" placeholder="选择学期" style="width: 220px" @change="loadData">
        <el-option v-for="item in semesters" :key="item.id" :label="item.semesterName" :value="item.id" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="courseCode" label="课程号" width="120" />
      <el-table-column prop="courseName" label="课程名" min-width="180" />
      <el-table-column v-if="isAdmin" prop="teacherName" label="授课教师" width="110" />
      <el-table-column prop="semesterName" label="学期" width="150" />
      <el-table-column prop="major" label="面向专业" min-width="160" show-overflow-tooltip />
      <el-table-column prop="totalHours" label="实验学时" width="100" align="right" class-name="tabular-nums" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goExperiments(row)">实验项目</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiListCourses } from '@/api/course';
import { apiListSemesters } from '@/api/semester';
import { useUserStore } from '@/store/user';
import type { ICourse, ISemester } from '@/types/api';

const router = useRouter();
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

const semesterId = ref<number | null>(null);
const semesters = ref<ISemester[]>([]);
const loading = ref(false);
const records = ref<ICourse[]>([]);

async function loadData(): Promise<void> {
  if (!semesterId.value) return;
  loading.value = true;
  try {
    records.value = await apiListCourses({ semesterId: semesterId.value });
  } finally {
    loading.value = false;
  }
}

function goExperiments(row: ICourse): void {
  void router.push({ path: '/experiments', query: { courseId: String(row.id) } });
}

onMounted(async () => {
  semesters.value = await apiListSemesters();
  const current = semesters.value.find((item) => item.status === 'ACTIVE');
  if (current) {
    semesterId.value = current.id;
  }
  await loadData();
});
</script>
