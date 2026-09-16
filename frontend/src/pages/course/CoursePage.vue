<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">课程管理</div>
        <div class="page-header__desc">{{ isAdmin ? '全校实验课程' : '我任教的实验课程' }}（由课表导入自动生成）</div>
      </div>
      <div class="filter-bar">
        <el-select v-model="semesterId" placeholder="选择学期" style="width: 220px" @change="loadAll">
          <el-option v-for="item in semesters" :key="item.id" :label="item.semesterName" :value="item.id" />
        </el-select>
        <template v-if="!isAdmin">
          <el-button plain :icon="Camera" :disabled="!semesterId" @click="photoImportVisible = true">拍照导入</el-button>
          <el-button type="primary" :icon="Upload" :disabled="!semesterId" @click="importVisible = true">
            导入我的课表
          </el-button>
        </template>
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
              <span>{{ course.totalHours }} 实验学时</span>
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
    <el-table v-else v-loading="loading" :data="records" border stripe>
      <el-table-column prop="courseCode" label="课程号" width="120" />
      <el-table-column prop="courseName" label="课程名" min-width="180" />
      <el-table-column prop="teacherName" label="授课教师" width="110" />
      <el-table-column prop="semesterName" label="学期" width="150" />
      <el-table-column prop="major" label="面向专业" min-width="160" show-overflow-tooltip />
      <el-table-column prop="totalHours" label="实验学时" width="100" align="right" class-name="tabular-nums" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goExperiments(row)">实验项目</el-button>
        </template>
      </el-table-column>
    </el-table>

    <ImportDialog
      v-if="!isAdmin"
      v-model="importVisible"
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
      :semester-id="semesterId"
      @success="loadAll"
    />

    <ScheduleEditDialog
      v-model="editVisible"
      :item="editingItem"
      :semester-id="semesterId"
      @success="loadAll"
      @revisions="openScheduleRevisions(editingItem)"
    />

    <CourseEditDialog
      v-model="courseEditVisible"
      :course="editingCourse"
      :revision-count="revisionCount(editingCourse)"
      @success="loadAll"
      @revisions="openCourseRevisions(editingCourse)"
    />

    <RevisionDrawer
      v-model="revisionVisible"
      :title="revisionTitle"
      :target-type="revisionTargetType"
      :target-id="revisionTargetId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Camera, Upload } from '@element-plus/icons-vue';
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
import type { ICourse, IMyScheduleItem, ISemester } from '@/types/api';

const router = useRouter();
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

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

function openScheduleEdit(item: IMyScheduleItem): void {
  editingItem.value = item;
  editVisible.value = true;
}

function openCourseEdit(course: ICourse | null): void {
  if (!course) return;
  editingCourse.value = course;
  courseEditVisible.value = true;
}

function openScheduleRevisions(item: IMyScheduleItem | null): void {
  if (!item) return;
  revisionTitle.value = `${item.courseName} · 修订记录`;
  revisionTargetType.value = 'SCHEDULE';
  revisionTargetId.value = item.id;
  revisionVisible.value = true;
}

function openCourseRevisions(course: ICourse | null): void {
  if (!course) return;
  revisionTitle.value = `${course.courseName} · 修订记录`;
  revisionTargetType.value = 'COURSE';
  revisionTargetId.value = course.id;
  revisionVisible.value = true;
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
  await loadAll();
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

.course-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-4);
  border: 1px solid;
  border-radius: var(--radius-lg);
  transition: box-shadow var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);

  &:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-2px);
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

    .el-button {
      opacity: 0.9;
    }
  }
}
</style>
