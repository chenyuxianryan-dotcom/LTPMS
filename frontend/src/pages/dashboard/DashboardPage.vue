<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">概览</div>
        <div class="page-header__desc">{{ isAdmin ? '全校实验教学数据总览' : '我的实验教学数据总览' }}</div>
      </div>
    </div>

    <!-- 管理员视角：2×2 四象限方格图 -->
    <div v-if="isAdmin" class="dashboard-page__stats">
      <StatGridCard
        title="实验室使用情况"
        :cells="adminOverview.labCells ?? []"
        :columns="6"
        clickable
        total-label="间"
        used-label="使用中"
        free-label="空闲"
        link-text="查看详情"
        link-to="/laboratories"
        @cell-click="goLabs"
      >
        <template #subtitle>
          <span class="dashboard-page__num dashboard-page__num--used">{{ usedOf(adminOverview.labCells) }}</span>
          间使用中 ·
          <span class="dashboard-page__num">{{ freeOf(adminOverview.labCells) }}</span>
          间空闲 ·
          <strong class="dashboard-page__num--used">{{ percentOf(adminOverview.labCells) }}%</strong>
        </template>
      </StatGridCard>

      <StatGridCard
        title="课程开课情况"
        :cells="adminOverview.courseCells ?? []"
        :columns="6"
        clickable
        total-label="门"
        used-label="实验项目就绪"
        free-label="待建实验项目"
        link-text="查看详情"
        link-to="/courses"
        @cell-click="goCourses"
      >
        <template #subtitle>
          <span class="dashboard-page__num dashboard-page__num--used">{{ usedOf(adminOverview.courseCells) }}</span>
          门就绪 ·
          <span class="dashboard-page__num">{{ freeOf(adminOverview.courseCells) }}</span>
          门待建 ·
          <strong class="dashboard-page__num--used">{{ percentOf(adminOverview.courseCells) }}%</strong>
        </template>
      </StatGridCard>

      <StatGridCard
        title="实验项目情况"
        :cells="adminOverview.experimentCells ?? []"
        :columns="6"
        total-label="个"
        used-label="已建成"
        free-label="未建成"
        empty-hint="实验项目由教师创建"
      >
        <template #subtitle>
          共
          <span class="dashboard-page__num dashboard-page__num--used">{{ adminOverview.experimentCells?.length ?? 0 }}</span>
          个实验项目 · 由教师创建维护
        </template>
      </StatGridCard>

      <StatGridCard
        title="教师任课情况"
        :cells="adminOverview.teacherCells ?? []"
        :columns="6"
        clickable
        total-label="位"
        used-label="在任"
        free-label="未任"
        link-text="查看详情"
        link-to="/teachers"
        @cell-click="goTeachers"
      >
        <template #subtitle>
          <span class="dashboard-page__num dashboard-page__num--used">{{ usedOf(adminOverview.teacherCells) }}</span>
          位在任 ·
          <span class="dashboard-page__num">{{ freeOf(adminOverview.teacherCells) }}</span>
          位未任 ·
          <strong class="dashboard-page__num--used">{{ percentOf(adminOverview.teacherCells) }}%</strong>
        </template>
      </StatGridCard>
    </div>

    <!-- 教师视角：2×2（我的课程 / 我的实验项目 / 排课周历 / 最近查询） -->
    <div v-else class="dashboard-page__stats">
      <StatGridCard
        title="我的课程"
        :cells="teacherOverview.myCourseCells ?? []"
        :columns="6"
        clickable
        total-label="门"
        used-label="实验项目就绪"
        free-label="待建实验项目"
        link-text="查看详情"
        link-to="/courses"
        @cell-click="goCourseExperiments"
      >
        <template #subtitle>
          <span class="dashboard-page__num dashboard-page__num--used">{{ usedOf(teacherOverview.myCourseCells) }}</span>
          门就绪 ·
          <span class="dashboard-page__num">{{ freeOf(teacherOverview.myCourseCells) }}</span>
          门待建
        </template>
      </StatGridCard>

      <StatGridCard
        title="我的实验项目"
        :cells="teacherOverview.myExperimentCells ?? []"
        :columns="6"
        clickable
        total-label="个"
        used-label="当前学期"
        free-label="非当前学期"
        empty-hint="从当前学期课程进入创建"
        empty-action-text="去创建"
        empty-action-to="/experiments"
        link-text="查看详情"
        link-to="/experiments"
        @cell-click="goExperiments"
      >
        <template #subtitle>
          <span class="dashboard-page__num dashboard-page__num--used">{{ usedOf(teacherOverview.myExperimentCells) }}</span>
          个当前学期 ·
          <span class="dashboard-page__num">{{ freeOf(teacherOverview.myExperimentCells) }}</span>
          个非当前学期
        </template>
      </StatGridCard>

      <StatGridCard
        title="我的排课周历"
        :cells="weekCells"
        :columns="10"
        total-label="周"
        used-label="有课"
        free-label="空闲"
      >
        <template #subtitle>
          <span class="dashboard-page__num dashboard-page__num--used">{{ usedOf(weekCells) }}</span>
          周有课 ·
          <span class="dashboard-page__num">{{ freeOf(weekCells) }}</span>
          周空闲
        </template>
      </StatGridCard>

      <el-card class="dashboard-page__recent" shadow="never">
        <template #header>
          <div class="dashboard-page__recent-head">
            <span>最近查询</span>
            <span class="dashboard-page__recent-total">{{ recentQueries.length }} 条</span>
          </div>
        </template>
        <el-empty v-if="recentQueries.length === 0" description="暂无查询记录" :image-size="60" />
        <ul v-else class="dashboard-page__recent-list">
          <li v-for="(q, index) in recentQueries" :key="index" class="dashboard-page__recent-item">
            <span class="dashboard-page__recent-question" :title="q.question">{{ q.question }}</span>
            <span class="dashboard-page__recent-meta">
              <el-tag :type="q.success === 0 ? 'danger' : 'success'" size="small" effect="light">
                {{ q.success === 0 ? '失败' : '成功' }}
              </el-tag>
              <span class="dashboard-page__recent-time">{{ q.time }}</span>
            </span>
          </li>
        </ul>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiGetAdminOverview, apiGetTeacherOverview } from '@/api/dashboard';
import StatGridCard from '@/components/StatGridCard.vue';
import { useUserStore } from '@/store/user';
import type { IAdminOverview, IRecentQuery, IStatCell, ITeacherOverview } from '@/types/api';

const router = useRouter();
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

const adminOverview = ref<IAdminOverview>({});
const teacherOverview = ref<ITeacherOverview>({});
const recentQueries = ref<IRecentQuery[]>([]);

/** 周历数据映射为通用方格（code=周次，name=第 N 周） */
const weekCells = computed<IStatCell[]>(() =>
  (teacherOverview.value.weekCells ?? []).map((w) => ({
    code: String(w.week ?? ''),
    name: `第 ${w.week ?? '?'} 周`,
    used: !!w.used,
  })),
);

function usedOf(cells?: IStatCell[]): number {
  return (cells ?? []).filter((c) => c.used).length;
}

function freeOf(cells?: IStatCell[]): number {
  return (cells ?? []).length - usedOf(cells);
}

function percentOf(cells?: IStatCell[]): number {
  const total = (cells ?? []).length;
  return total === 0 ? 0 : Math.round((usedOf(cells) / total) * 100);
}

function goLabs(): void {
  router.push('/laboratories');
}

function goCourses(): void {
  router.push('/courses');
}

function goTeachers(): void {
  router.push('/teachers');
}

function goExperiments(): void {
  router.push('/experiments');
}

/** 教师点击「我的课程」格：直达该课程的实验项目页（补建实验项目） */
function goCourseExperiments(cell: IStatCell): void {
  router.push({ path: '/experiments', query: { courseId: String(cell.id ?? '') } });
}

onMounted(async () => {
  // 刷新直达 /dashboard 时 fetchMe 可能未返回，role 为空会走错分支（教师接口对管理员 403）
  if (!userStore.user) {
    await userStore.fetchMe().catch(() => undefined);
  }
  if (userStore.isAdmin) {
    adminOverview.value = await apiGetAdminOverview();
  } else {
    teacherOverview.value = await apiGetTeacherOverview();
    recentQueries.value = teacherOverview.value.recentQueries ?? [];
  }
});
</script>

<style scoped lang="scss">
.dashboard-page__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-6);

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
  }
}

.dashboard-page__num {
  color: var(--el-text-color-placeholder, #a8abb2);

  &--used {
    color: var(--el-color-primary, #378add);
  }
}

.dashboard-page__recent {
  border: 1px solid var(--border-subtle, #e4e7ed);
  border-radius: var(--border-radius-lg, 12px);
  height: 100%;

  :deep(.el-card__header) {
    padding: var(--space-4, 14px) var(--space-5, 16px) 4px;
    border-bottom: none;
  }

  :deep(.el-card__body) {
    padding: 4px var(--space-5, 16px) var(--space-4, 14px);
  }
}

.dashboard-page__recent-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.dashboard-page__recent-total {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary, #909399);
}

.dashboard-page__recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.dashboard-page__recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3, 10px);
  padding: 6px 10px;
  background: var(--el-fill-color-lighter, #fafafa);
  border-radius: 8px;
}

.dashboard-page__recent-question {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-primary, #303133);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__recent-meta {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
}

.dashboard-page__recent-time {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
