<template>
  <div class="page-container unit-skin">
    <div class="page-header unit-head">
      <div class="unit-head__id">
        <div class="page-header__title">课程管理</div>
        <div class="page-header__desc">{{ isAdmin ? '全校实验课程' : '我任教的实验课程' }}（由课表导入自动生成）</div>
      </div>
      <div class="unit-head__console">
        <div class="unit-head__row">
          <div class="unit-nameplate">LTPMS COURSE UNIT</div>
          <div class="unit-led-strip" aria-hidden="true">
            <span
              v-for="n in 16"
              :key="n"
              class="unit-led-strip__dot"
              :class="{ 'is-hit': hitStep === n - 1 }"
              :style="{ '--i': n - 1 }"
            />
          </div>
        </div>
        <div class="filter-bar">
          <el-select v-model="semesterId" placeholder="选择学期" style="width: 220px" @change="handleSemesterChange">
            <el-option v-for="item in semesters" :key="item.id" :label="item.semesterName" :value="item.id" />
          </el-select>
          <template v-if="!isAdmin">
            <el-button plain :icon="Camera" :disabled="!semesterId" @click="openPhotoImport">拍照导入</el-button>
            <el-button type="primary" :icon="Upload" :disabled="!semesterId" @click="openImport">
              导入我的课表
            </el-button>
          </template>
          <!-- 管理员统一入口（2026-09-20 权限边界）：实验项目菜单已移至本模块页头，进入后为只读视图 -->
          <el-button v-else plain :icon="Box" @click="goExperimentProjects">实验项目</el-button>
        </div>
      </div>
    </div>

    <!-- 教师视图：周课表 + 课程卡片墙 -->
    <template v-if="!isAdmin">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="可导入本人授课课表 Excel：教师工号列留空或填写本人工号，系统自动归属到您名下，仅覆盖您本人已有课表"
        class="course-page__notice"
      />

      <section class="course-page__section">
        <div class="course-page__section-head">
          <span class="course-page__section-title">我的周课表</span>
          <span class="course-page__section-sub">
            {{ scheduleItems.length > 0 ? '点击课程卡片可修改上课地点 / 班级 / 人数 / 时间' : '本学期尚未导入课表' }}
          </span>
        </div>
        <WeekGrid :items="scheduleItems" :loading="scheduleLoading" @select="openScheduleEdit" />
      </section>

      <section class="course-page__section">
        <div class="course-page__section-head">
          <span class="course-page__section-title">我的课程</span>
          <span class="course-page__section-sub">共 {{ records.length }} 门</span>
        </div>
        <el-empty v-if="!loading && records.length === 0" description="暂无课程，导入课表后自动生成" :image-size="80" />
        <div v-else class="course-page__cards">
          <article
            v-for="course in records"
            :key="course.id"
            class="course-card"
            :style="cardStyle(course)"
          >
            <div class="course-card__head">
              <span class="course-card__code">{{ course.courseCode }}</span>
              <el-tag v-if="revisionCount(course) > 0" size="small" type="warning" effect="light">
                修订 {{ revisionCount(course) }}
              </el-tag>
            </div>
            <div class="course-card__name" :title="course.courseName">{{ course.courseName }}</div>
            <div class="course-card__meta">
              <span>{{ course.major || '面向专业待补充' }}</span>
            </div>
            <div class="course-card__meter">
              <span class="course-card__meter-value">{{ course.totalHours }}</span>
              <span class="course-card__meter-label">实验学时</span>
              <span class="course-card__meter-dots" aria-hidden="true">
                <i v-for="n in 10" :key="n" :class="{ 'is-on': n <= hoursDots(course.totalHours) }" />
              </span>
            </div>
            <div v-if="statsOf(course.courseCode)" class="course-card__stats">
              每周 {{ statsOf(course.courseCode)?.sections }} 节 · {{ statsOf(course.courseCode)?.classes }} 个教学班
            </div>
            <div class="course-card__actions">
              <el-button link size="small" @click="goExperiments(course)">实验项目</el-button>
              <el-button link size="small" @click="openCourseEdit(course)">编辑</el-button>
              <el-button
                v-if="revisionCount(course) > 0"
                link
                size="small"
                type="warning"
                @click="openCourseRevisions(course)"
              >
                修订记录
              </el-button>
            </div>
          </article>
        </div>
      </section>
    </template>

    <!-- 管理员视图：只读表格（可查看全校课程，编辑入口见课表审校） -->
    <div v-else class="unit-table-panel">
      <el-table
        v-loading="loading"
        :data="records"
        :row-class-name="rowClassName"
        @row-click="handleRowClick"
      >
        <el-table-column prop="courseCode" label="课程号" width="120" />
        <el-table-column prop="courseName" label="课程名" min-width="180" />
        <el-table-column prop="teacherName" label="授课教师" width="110" />
        <el-table-column prop="semesterName" label="学期" width="150" />
        <el-table-column prop="major" label="面向专业" min-width="160" show-overflow-tooltip />
        <el-table-column label="实验学时" width="150" align="right" class-name="tabular-nums">
          <template #default="{ row }">
            <div class="unit-meter">
              <span class="unit-meter__value">{{ row.totalHours }}</span>
              <span class="unit-meter__dots" aria-hidden="true">
                <i v-for="n in 10" :key="n" :class="{ 'is-on': n <= hoursDots(row.totalHours) }" />
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goExperiments(row)">实验项目</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <ImportDialog
      v-if="!isAdmin"
      v-model="importVisible"
      class="unit-dialog"
      title="导入我的课表"
      action="/schedules/import"
      template-url="/schedules/import/template"
      preview-url="/schedules/import/preview"
      :extra-data="semesterId ? { semesterId } : undefined"
      @success="loadAll"
    />

    <SchedulePhotoImportDialog
      v-if="!isAdmin && semesterId"
      v-model="photoImportVisible"
      class="unit-dialog"
      :semester-id="semesterId"
      @success="loadAll"
    />

    <ScheduleEditDialog
      v-model="editVisible"
      class="unit-dialog"
      :item="editingItem"
      :semester-id="semesterId"
      @success="loadAll"
      @revisions="openScheduleRevisions(editingItem)"
    />

    <CourseEditDialog
      v-model="courseEditVisible"
      class="unit-dialog"
      :course="editingCourse"
      :revision-count="revisionCount(editingCourse)"
      @success="loadAll"
      @revisions="openCourseRevisions(editingCourse)"
    />

    <RevisionDrawer
      v-model="revisionVisible"
      class="unit-dialog"
      :title="revisionTitle"
      :target-type="revisionTargetType"
      :target-id="revisionTargetId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Box, Camera, Upload } from '@element-plus/icons-vue';
import CourseEditDialog from '@/components/schedule/CourseEditDialog.vue';
import ImportDialog from '@/components/ImportDialog.vue';
import RevisionDrawer from '@/components/schedule/RevisionDrawer.vue';
import ScheduleEditDialog from '@/components/schedule/ScheduleEditDialog.vue';
import SchedulePhotoImportDialog from '@/components/SchedulePhotoImportDialog.vue';
import WeekGrid from '@/components/schedule/WeekGrid.vue';
import { apiCourseRevisionFlags, apiListCourses } from '@/api/course';
import { apiMySchedules } from '@/api/schedule';
import { apiListSemesters } from '@/api/semester';
import { useUserStore } from '@/store/user';
import { courseColor } from '@/utils/courseColor';
import { tickUnitLed, unitLedStep } from '@/utils/unitLed';
import type { ICourse, IMyScheduleItem, ISemester } from '@/types/api';

const router = useRouter();
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

/** LED 灯带走位（无声）：切换学期 / 点击行 / 开弹窗时推进 */
const hitStep = unitLedStep();

const semesterId = ref<number | null>(null);
const semesters = ref<ISemester[]>([]);
const loading = ref(false);
const records = ref<ICourse[]>([]);
const importVisible = ref(false);
const photoImportVisible = ref(false);

const scheduleLoading = ref(false);
const scheduleItems = ref<IMyScheduleItem[]>([]);
const courseRevisionFlags = ref<Record<string, number>>({});

const editVisible = ref(false);
const editingItem = ref<IMyScheduleItem | null>(null);
const courseEditVisible = ref(false);
const editingCourse = ref<ICourse | null>(null);

const revisionVisible = ref(false);
const revisionTitle = ref('');
const revisionTargetType = ref<'SCHEDULE' | 'COURSE'>('SCHEDULE');
const revisionTargetId = ref<number | null>(null);

/** 实验学时点阵：每灯 8 学时，10 灯满量程 80 */
function hoursDots(hours: number): number {
  return Math.max(0, Math.min(10, Math.ceil(hours / 8)));
}

/** 课程卡片统计：每周节数 = 各行时间槽节次跨度之和；教学班数 = 课表行数 */
const courseStats = computed<Map<string, { sections: number; classes: number }>>(() => {
  const map = new Map<string, { sections: number; classes: number }>();
  for (const item of scheduleItems.value) {
    const stat = map.get(item.courseCode) ?? { sections: 0, classes: 0 };
    stat.classes += 1;
    for (const slot of item.slots) {
      stat.sections += slot.endSection - slot.startSection + 1;
    }
    map.set(item.courseCode, stat);
  }
  return map;
});

function statsOf(courseCode: string): { sections: number; classes: number } | undefined {
  return courseStats.value.get(courseCode);
}

function revisionCount(course: ICourse | null): number {
  if (!course) return 0;
  return courseRevisionFlags.value[course.id] ?? 0;
}

function cardStyle(course: ICourse): Record<string, string> {
  const color = courseColor(course.courseCode);
  return { background: color.bg, borderColor: color.border, color: color.fg };
}

const pulseRowId = ref<number | null>(null);
let pulseTimer: number | undefined;

function rowClassName({ row }: { row: ICourse }): string {
  return pulseRowId.value === row.id ? 'is-pad-hit' : '';
}

function handleRowClick(row: ICourse, _column: unknown, event: Event): void {
  const target = event.target as HTMLElement | null;
  if (target?.closest('button, .el-button, .el-checkbox, input, a')) return;
  tickUnitLed();
  pulseRowId.value = row.id;
  window.clearTimeout(pulseTimer);
  pulseTimer = window.setTimeout(() => {
    pulseRowId.value = null;
  }, 300);
}

async function loadCourses(): Promise<void> {
  if (!semesterId.value) return;
  loading.value = true;
  try {
    records.value = await apiListCourses({ semesterId: semesterId.value });
    if (!isAdmin.value && records.value.length > 0) {
      courseRevisionFlags.value = await apiCourseRevisionFlags(records.value.map((item) => item.id));
    } else {
      courseRevisionFlags.value = {};
    }
  } finally {
    loading.value = false;
  }
}

async function loadScheduleItems(): Promise<void> {
  if (!semesterId.value || isAdmin.value) return;
  scheduleLoading.value = true;
  try {
    scheduleItems.value = await apiMySchedules({ semesterId: semesterId.value });
  } finally {
    scheduleLoading.value = false;
  }
}

async function loadAll(): Promise<void> {
  await Promise.all([loadCourses(), loadScheduleItems()]);
}

function handleSemesterChange(): void {
  tickUnitLed();
  void loadAll();
}

function openImport(): void {
  tickUnitLed();
  importVisible.value = true;
}

function openPhotoImport(): void {
  tickUnitLed();
  photoImportVisible.value = true;
}

function openScheduleEdit(item: IMyScheduleItem): void {
  tickUnitLed();
  editingItem.value = item;
  editVisible.value = true;
}

function openCourseEdit(course: ICourse | null): void {
  if (!course) return;
  tickUnitLed();
  editingCourse.value = course;
  courseEditVisible.value = true;
}

function openScheduleRevisions(item: IMyScheduleItem | null): void {
  if (!item) return;
  tickUnitLed();
  revisionTitle.value = `${item.courseName} · 修订记录`;
  revisionTargetType.value = 'SCHEDULE';
  revisionTargetId.value = item.id;
  revisionVisible.value = true;
}

function openCourseRevisions(course: ICourse | null): void {
  if (!course) return;
  tickUnitLed();
  revisionTitle.value = `${course.courseName} · 修订记录`;
  revisionTargetType.value = 'COURSE';
  revisionTargetId.value = course.id;
  revisionVisible.value = true;
}

function goExperiments(row: ICourse): void {
  tickUnitLed();
  void router.push({ path: '/experiments', query: { courseId: String(row.id) } });
}

/** 管理员统一入口（2026-09-20 权限边界）：实验项目菜单已移至课程管理页头，进入后为只读视图 */
function goExperimentProjects(): void {
  tickUnitLed();
  void router.push('/experiments');
}

onMounted(async () => {
  semesters.value = await apiListSemesters();
  const current = semesters.value.find((item) => item.status === 'ACTIVE');
  if (current) {
    semesterId.value = current.id;
  }
  await loadAll();
});

onBeforeUnmount(() => {
  window.clearTimeout(pulseTimer);
});
</script>

<style scoped lang="scss">
.course-page {
  &__notice {
    margin-bottom: var(--space-5);
  }

  &__section {
    margin-bottom: var(--space-6);
  }

  &__section-head {
    display: flex;
    align-items: baseline;
    gap: var(--space-3);
    margin-bottom: var(--space-3);
  }

  &__section-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__section-sub {
    font-size: 12px;
    color: var(--text-quaternary);
  }

  &__cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(264px, 1fr));
    gap: var(--space-4);
  }
}

/* 周课表卡片：按压物理（仅本页生效，不改动共享组件） */
.unit-skin :deep(.week-card) {
  box-shadow: var(--unit-key-shadow);

  &:active {
    transform: translateY(1px);
    box-shadow: var(--unit-key-shadow-press);
  }
}

.course-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-4);
  border: 1px solid;
  border-radius: var(--radius-lg);
  box-shadow: var(--unit-key-shadow);
  transition: box-shadow var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);

  &:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-2px);
  }

  &:active {
    transform: translateY(1px);
    box-shadow: var(--unit-key-shadow-press);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-2);
    min-height: 22px;
  }

  &__code {
    font-family: var(--font-mono);
    font-size: 12px;
    opacity: 0.75;
  }

  &__name {
    font-size: 15px;
    font-weight: 600;
    line-height: 22px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    font-size: 12px;
    opacity: 0.85;

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  /* 实验学时：点阵读数（沿用课程色，随卡片文字色走） */
  &__meter {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-top: var(--space-1);
    font-size: 12px;
  }

  &__meter-value {
    font-family: var(--font-mono);
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.02em;
  }

  &__meter-label {
    opacity: 0.75;
  }

  &__meter-dots {
    display: inline-flex;
    align-items: center;
    gap: 2px;

    i {
      width: 3.5px;
      height: 3.5px;
      border-radius: 50%;
      background: currentcolor;
      opacity: 0.25;
    }

    i.is-on {
      opacity: 1;
    }
  }

  &__stats {
    font-size: 12px;
    font-weight: 500;
    opacity: 0.9;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    margin-top: var(--space-2);
    padding-top: var(--space-2);
    border-top: 1px dashed currentcolor;

    .el-button.is-link {
      height: 26px;
      padding: 0 10px;
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
      background-color: var(--bg-elevated);
      box-shadow: var(--unit-key-shadow);
      opacity: 1;

      &:active {
        transform: translateY(1px);
        box-shadow: var(--unit-key-shadow-press);
      }
    }
  }
}
</style>
