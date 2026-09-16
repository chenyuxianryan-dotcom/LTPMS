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
        clickable
        @cell-click="handleWeekCellClick"
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
    <!-- 今日开放实验室（两角色可见） -->
    <el-card v-loading="openLoading" class="dashboard-page__open" shadow="never">
      <template #header>
        <div class="dashboard-page__open-head">
          <span>今日开放实验室</span>
          <span class="dashboard-page__open-meta">{{ openMetaText }}</span>
        </div>
      </template>
      <el-empty v-if="!openToday || openToday.labs.length === 0" :description="openEmptyText" :image-size="60" />
      <div v-else class="dashboard-page__open-list">
        <div v-for="lab in openToday.labs" :key="lab.labCode" class="dashboard-page__open-item">
          <span class="dashboard-page__open-code">{{ lab.labCode }}</span>
          <span class="dashboard-page__open-name">{{ lab.labName }}</span>
          <el-tag size="small" type="info" effect="light">{{ lab.audience || '全校' }}</el-tag>
          <span class="dashboard-page__open-time">{{ lab.sectionText }}</span>
        </div>
      </div>
    </el-card>

    <!-- 周历点击展开：该周课程明细（受控模式，开关逻辑见 handleWeekCellClick） -->
    <el-popover
      :visible="weekPopover.visible"
      :virtual-ref="weekPopover.anchor"
      virtual-triggering
      placement="top"
      :width="330"
    >
      <div class="dashboard-page__week-pop">
        <div class="dashboard-page__week-pop-head">
          <span class="dashboard-page__week-pop-title">第 {{ weekPopover.week }} 周</span>
          <span class="dashboard-page__week-pop-count">
            {{ weekPopover.loading ? '加载中…' : `${weekPopover.items.length} 门课` }}
          </span>
        </div>
        <div v-if="!weekPopover.loading && weekPopover.items.length === 0" class="dashboard-page__week-pop-empty">
          本周无课
        </div>
        <ul v-else class="dashboard-page__week-pop-list">
          <li v-for="item in weekPopover.items" :key="item.id" class="dashboard-page__week-pop-item">
            <div class="dashboard-page__week-pop-line">
              <span class="dashboard-page__week-pop-name" :title="item.courseName">{{ item.courseName }}</span>
              <span class="dashboard-page__week-pop-lab">{{ item.labName || item.labCode }}</span>
            </div>
            <div class="dashboard-page__week-pop-meta">{{ slotText(item) }}</div>
            <div class="dashboard-page__week-pop-meta">{{ item.classNames }}</div>
          </li>
        </ul>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiGetAdminOverview, apiGetTeacherOverview } from '@/api/dashboard';
import { apiGetLabOpenToday } from '@/api/labOpening';
import { apiMySchedules } from '@/api/schedule';
import StatGridCard from '@/components/StatGridCard.vue';
import { WEEKDAY_LABELS, WEEK_TYPE_MAP } from '@/constants';
import { useUserStore } from '@/store/user';
import type {
  IAdminOverview,
  ILabOpenToday,
  IMyScheduleItem,
  IRecentQuery,
  IStatCell,
  ITeacherOverview,
} from '@/types/api';

const router = useRouter();
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

const adminOverview = ref<IAdminOverview>({});
const teacherOverview = ref<ITeacherOverview>({});
const recentQueries = ref<IRecentQuery[]>([]);
const openToday = ref<ILabOpenToday | null>(null);
const openLoading = ref(false);

/** 周历点击展开：锚点为被点击的周格，数据按周过滤 */
const weekPopover = reactive({
  visible: false,
  week: 0,
  anchor: null as HTMLElement | null,
  loading: false,
  items: [] as IMyScheduleItem[],
});

const openMetaText = computed(() => {
  const today = openToday.value;
  if (!today?.date) return '';
  return [
    today.semesterName,
    today.date,
    today.dayOfWeek ? `周${WEEKDAY_LABELS[today.dayOfWeek - 1]}` : '',
    today.weekNo ? `第 ${today.weekNo} 周` : '',
  ]
    .filter(Boolean)
    .join(' · ');
});

const openEmptyText = computed(() => {
  const today = openToday.value;
  if (!today) return '暂无数据';
  if (!today.semesterId) return '当前没有进行中的学期';
  if (today.holiday) return `${today.holidayName ?? '今日'} 闭馆`;
  if (!today.inSemester) return '当前不在学期教学周内';
  return '今日暂无开放的实验室';
});

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

/** 点击周历格：以该格为锚点弹出该周课程明细，再次点击同一格收起 */
function handleWeekCellClick(cell: IStatCell, event: MouseEvent): void {
  const week = Number(cell.code);
  if (weekPopover.visible && weekPopover.week === week) {
    weekPopover.visible = false;
    return;
  }
  weekPopover.week = week;
  weekPopover.anchor = event.currentTarget as HTMLElement;
  weekPopover.visible = true;
  void loadWeekItems(week);
}

async function loadWeekItems(week: number): Promise<void> {
  weekPopover.items = [];
  const semesterId = openToday.value?.semesterId;
  if (!semesterId) return;
  weekPopover.loading = true;
  try {
    weekPopover.items = await apiMySchedules({ semesterId, week });
  } finally {
    weekPopover.loading = false;
  }
}

/** 点击弹层与方格之外的区域收起周历弹层 */
function handleDocumentClick(event: MouseEvent): void {
  if (!weekPopover.visible) return;
  const target = event.target as HTMLElement | null;
  if (target?.closest('.dashboard-page__week-pop') || target?.closest('.stat-grid-card__cell')) return;
  weekPopover.visible = false;
}

/** 时间槽文案：周天 + 节次 + 单双周标记 */
function slotText(item: IMyScheduleItem): string {
  return item.slots
    .map((slot) => {
      const weekType = slot.weekType !== 'ALL' ? `（${WEEK_TYPE_MAP[slot.weekType] ?? slot.weekType}）` : '';
      return `周${WEEKDAY_LABELS[slot.dayOfWeek - 1]} 第${slot.startSection}-${slot.endSection}节${weekType}`;
    })
    .join('、');
}

onMounted(async () => {
  document.addEventListener('click', handleDocumentClick);
  openLoading.value = true;
  // 刷新直达 /dashboard 时 fetchMe 可能未返回，role 为空会走错分支（教师接口对管理员 403）
  if (!userStore.user) {
    await userStore.fetchMe().catch(() => undefined);
  }
  try {
    if (userStore.isAdmin) {
      adminOverview.value = await apiGetAdminOverview();
    } else {
      teacherOverview.value = await apiGetTeacherOverview();
      recentQueries.value = teacherOverview.value.recentQueries ?? [];
    }
    openToday.value = await apiGetLabOpenToday().catch(() => null);
  } finally {
    openLoading.value = false;
  }
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick);
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

.dashboard-page__open {
  border: 1px solid var(--border-subtle, #e4e7ed);
  border-radius: var(--border-radius-lg, 12px);

  :deep(.el-card__header) {
    padding: var(--space-4, 14px) var(--space-5, 16px) 4px;
    border-bottom: none;
  }

  :deep(.el-card__body) {
    padding: 4px var(--space-5, 16px) var(--space-4, 14px);
  }
}

.dashboard-page__open-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.dashboard-page__open-meta {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary, #909399);
}

.dashboard-page__open-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2, 8px);
}

.dashboard-page__open-item {
  display: flex;
  align-items: center;
  gap: var(--space-2, 8px);
  padding: 6px 12px;
  background: var(--el-fill-color-lighter, #fafafa);
  border-radius: 8px;
  font-size: 13px;
}

.dashboard-page__open-code {
  font-family: monospace;
  font-weight: 600;
  color: var(--el-color-primary, #378add);
}

.dashboard-page__open-name {
  color: var(--el-text-color-primary, #303133);
}

.dashboard-page__open-time {
  color: var(--el-text-color-secondary, #909399);
}

.dashboard-page__week-pop {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dashboard-page__week-pop-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.dashboard-page__week-pop-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary, #909399);
}

.dashboard-page__week-pop-empty {
  padding: 12px 0;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-placeholder, #a8abb2);
}

.dashboard-page__week-pop-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  max-height: 260px;
  overflow-y: auto;
  list-style: none;
}

.dashboard-page__week-pop-item {
  padding: 6px 10px;
  background: var(--el-fill-color-lighter, #fafafa);
  border-radius: 8px;
}

.dashboard-page__week-pop-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.dashboard-page__week-pop-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__week-pop-lab {
  flex-shrink: 0;
  font-family: monospace;
  font-size: 12px;
  color: var(--el-color-primary, #378add);
}

.dashboard-page__week-pop-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
