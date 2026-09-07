<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">概览</div>
        <div class="page-header__desc">{{ isAdmin ? '全校实验教学数据总览' : '我的实验教学数据总览' }}</div>
      </div>
    </div>

    <div v-if="isAdmin" class="dashboard-page__stats">
      <div v-for="item in adminCards" :key="item.label" class="stat-card">
        <div class="stat-card__label">{{ item.label }}</div>
        <div class="stat-card__value">{{ item.value }}</div>
      </div>
    </div>

    <template v-else>
      <div class="dashboard-page__stats">
        <div v-for="item in teacherCards" :key="item.label" class="stat-card">
          <div class="stat-card__label">{{ item.label }}</div>
          <div class="stat-card__value">{{ item.value }}</div>
        </div>
      </div>

      <el-card class="dashboard-page__recent" shadow="never">
        <template #header>最近查询记录</template>
        <el-empty v-if="recentQueries.length === 0" description="暂无查询记录" :image-size="80" />
        <el-table v-else :data="recentQueries" size="small">
          <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
          <el-table-column prop="resultSummary" label="结果" width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.success === 1 ? 'success' : 'danger'" effect="light" size="small">
                {{ row.success === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="170" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGetAdminOverview, apiGetTeacherOverview } from '@/api/dashboard';
import { useUserStore } from '@/store/user';
import type { IQueryHistory } from '@/types/api';

const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

const adminCards = ref<{ label: string; value: number }[]>([]);
const teacherCards = ref<{ label: string; value: number }[]>([]);
const recentQueries = ref<IQueryHistory[]>([]);

onMounted(async () => {
  if (userStore.isAdmin) {
    const overview = await apiGetAdminOverview();
    adminCards.value = [
      { label: '实验室总数', value: overview.labCount ?? 0 },
      { label: '课程总数', value: overview.courseCount ?? 0 },
      { label: '实验项目总数', value: overview.experimentCount ?? 0 },
      { label: '教师总数', value: overview.teacherCount ?? 0 },
    ];
  } else {
    const overview = await apiGetTeacherOverview();
    teacherCards.value = [
      { label: '我的课程数', value: overview.courseCount ?? 0 },
      { label: '实验项目数', value: overview.experimentCount ?? 0 },
    ];
    recentQueries.value = overview.recentQueries ?? [];
  }
});
</script>

<style scoped lang="scss">
.dashboard-page__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.dashboard-page__recent {
  border: 1px solid var(--border-subtle);
}
</style>
