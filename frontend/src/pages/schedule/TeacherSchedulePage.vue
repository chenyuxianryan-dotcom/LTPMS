<template>
  <div class="page-container">
    <div class="page-header">
      <div class="teacher-schedule__identity">
        <el-button text :icon="ArrowLeft" class="teacher-schedule__back" @click="goBack">返回书架</el-button>
        <el-avatar :size="44" :src="resolveAvatarUrl(teacherId, teacher?.avatar)" class="teacher-schedule__avatar">
          {{ displayName.slice(0, 1) }}
        </el-avatar>
        <div>
          <div class="page-header__title">{{ displayName }} 的课表</div>
          <div class="page-header__desc">{{ headerDesc }}</div>
        </div>
      </div>
      <div class="filter-bar">
        <el-tag v-if="(progress?.revisionCount ?? 0) > 0" type="warning" effect="light">
          修订 {{ progress?.revisionCount }} 条
        </el-tag>
        <el-tag :type="uploaded ? 'success' : 'info'" effect="light">{{ uploaded ? '已导入' : '未导入' }}</el-tag>
        <el-button v-if="!uploaded" type="primary" plain :loading="reminding" @click="handleRemind">催办导入</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!loading && !hasData" description="" :image-size="96">
        <template #description>
          <div class="teacher-schedule__empty-title">{{ displayName }} 本学期尚未导入课表</div>
          <div class="teacher-schedule__empty-sub">可发送催办通知，提醒该教师前往「课程管理」页导入</div>
        </template>
        <el-button v-if="!uploaded" type="primary" :loading="reminding" @click="handleRemind">发送催办通知</el-button>
        <el-button @click="goBack">返回书架</el-button>
      </el-empty>

      <template v-else>
        <section v-if="scheduleItems.length > 0" class="teacher-schedule__section">
          <div class="teacher-schedule__section-head">
            <span class="teacher-schedule__section-title">周课表</span>
            <span class="teacher-schedule__section-sub">点击课程卡片可修改上课地点 / 班级 / 人数 / 时间</span>
          </div>
          <WeekGrid :items="scheduleItems" :loading="scheduleLoading" @select="openScheduleEdit" />
        </section>

        <section v-if="scheduleItems.length > 0" class="teacher-schedule__section">
          <div class="teacher-schedule__section-head">
            <span class="teacher-schedule__section-title">课表明细</span>
            <span class="teacher-schedule__section-sub">共 {{ scheduleItems.length }} 条</span>
            <div class="teacher-schedule__section-spacer" />
            <el-button
              type="danger"
              plain
              size="small"
              :disabled="selectedIds.length === 0"
              @click="handleBatchDelete"
            >
              批量删除（{{ selectedIds.length }}）
            </el-button>
          </div>
          <el-table :data="scheduleItems" border stripe @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="courseCode" label="课程号" width="120" />
            <el-table-column prop="courseName" label="课程名" min-width="170" show-overflow-tooltip />
            <el-table-column label="上课地点" width="150">
              <template #default="{ row }">{{ row.labName || row.labCode }}</template>
            </el-table-column>
            <el-table-column label="开放注记" min-width="130">
              <template #default="{ row }">
                <el-tooltip
                  v-if="noteOf(row.labCode).type !== 'none'"
                  :content="noteOf(row.labCode).tip"
                  placement="top"
                  :show-after="200"
                >
                  <el-tag :type="noteOf(row.labCode).tagType" size="small" :effect="noteOf(row.labCode).effect">
                    {{ noteOf(row.labCode).text }}
                  </el-tag>
                </el-tooltip>
                <span v-else class="teacher-schedule__muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="classNames" label="上课班级" min-width="170" show-overflow-tooltip />
            <el-table-column prop="studentCount" label="人数" width="80" align="right" class-name="tabular-nums" />
            <el-table-column prop="totalHours" label="实验学时" width="100" align="right" class-name="tabular-nums" />
            <el-table-column prop="rawScheduleText" label="上课时间" min-width="150" show-overflow-tooltip />
            <el-table-column label="修订" width="88">
              <template #default="{ row }">
                <el-link
                  v-if="row.revisionCount > 0"
                  type="warning"
                  :underline="false"
                  @click="openScheduleRevisions(row)"
                >
                  {{ row.revisionCount }} 条
                </el-link>
                <span v-else class="teacher-schedule__muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openScheduleEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="courses.length > 0" class="teacher-schedule__section">
          <div class="teacher-schedule__section-head">
            <span class="teacher-schedule__section-title">课程</span>
            <span class="teacher-schedule__section-sub">共 {{ courses.length }} 门（由课表导入自动生成）</span>
          </div>
          <div class="teacher-schedule__cards">
            <article v-for="course in courses" :key="course.id" class="course-card" :style="cardStyle(course)">
              <div class="course-card__head">
                <span class="course-card__code">{{ course.courseCode }}</span>
                <el-tag v-if="courseRevisionCount(course) > 0" size="small" type="warning" effect="light">
                  修订 {{ courseRevisionCount(course) }}
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
                <el-button link size="small" @click="openCourseEdit(course)">编辑</el-button>
                <el-button
                  v-if="courseRevisionCount(course) > 0"
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
    </div>

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
      :revision-count="editingCourse ? courseRevisionCount(editingCourse) : 0"
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
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft } from '@element-plus/icons-vue';
import CourseEditDialog from '@/components/schedule/CourseEditDialog.vue';
import RevisionDrawer from '@/components/schedule/RevisionDrawer.vue';
import ScheduleEditDialog from '@/components/schedule/ScheduleEditDialog.vue';
import WeekGrid from '@/components/schedule/WeekGrid.vue';
import { apiCourseRevisionFlags, apiListCourses } from '@/api/course';
import { apiGetLabOpeningOverview, apiListLabOpenings } from '@/api/labOpening';
import { apiBatchDeleteSchedules, apiGetUploadProgress, apiMySchedules, apiSendUploadRemind } from '@/api/schedule';
import { apiListSemesters } from '@/api/semester';
import { apiListTeachers } from '@/api/teacher';
import { resolveAvatarUrl } from '@/utils/avatar';
import { courseColor } from '@/utils/courseColor';
import { buildLabNoteIndex, labNoteOf, type ILabNote, type ILabNoteIndex } from '@/utils/labNote';
import type { ICourse, IMyScheduleItem, ITeacher, ITeacherUploadStatus } from '@/types/api';

const route = useRoute();
const router = useRouter();
const teacherId = Number(route.params.teacherId);

const loading = ref(true);
const reminding = ref(false);
const semesterId = ref<number | null>(route.query.semesterId ? Number(route.query.semesterId) : null);
const semesterName = ref('');
const teacher = ref<ITeacher | null>(null);
const progress = ref<ITeacherUploadStatus | null>(null);

const scheduleLoading = ref(false);
const scheduleItems = ref<IMyScheduleItem[]>([]);
const courses = ref<ICourse[]>([]);
const courseRevisionFlags = ref<Record<string, number>>({});
const selectedIds = ref<number[]>([]);
const labNotes = ref<ILabNoteIndex>({ openingsByLab: new Map(), overviewMap: new Map() });

const editVisible = ref(false);
const editingItem = ref<IMyScheduleItem | null>(null);
const courseEditVisible = ref(false);
const editingCourse = ref<ICourse | null>(null);

const revisionVisible = ref(false);
const revisionTitle = ref('');
const revisionTargetType = ref<'SCHEDULE' | 'COURSE'>('SCHEDULE');
const revisionTargetId = ref<number | null>(null);

const uploaded = computed(() => progress.value?.uploaded ?? scheduleItems.value.length > 0);
const hasData = computed(() => scheduleItems.value.length > 0 || courses.value.length > 0);
const displayName = computed(
  () => teacher.value?.realName ?? scheduleItems.value[0]?.teacherName ?? `教师 #${teacherId}`,
);

const headerDesc = computed(() => {
  const parts = [`工号 ${teacher.value?.username ?? '—'}`, teacher.value?.department || '部门未设置'];
  if (semesterName.value) parts.push(semesterName.value);
  parts.push(
    progress.value?.latestUploadAt ? `最近上传 ${formatTime(progress.value.latestUploadAt)}` : '本学期未导入课表',
  );
  return parts.join(' · ');
});

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

function courseRevisionCount(course: ICourse): number {
  return courseRevisionFlags.value[course.id] ?? 0;
}

function cardStyle(course: ICourse): Record<string, string> {
  const color = courseColor(course.courseCode);
  return { background: color.bg, borderColor: color.border, color: color.fg };
}

/** 按实验室聚合的开放注记：矛盾 > 未开放 > 正常开放段数 */
function noteOf(labCode: string): ILabNote {
  return labNoteOf(labNotes.value, labCode);
}

function formatTime(value?: string | null): string {
  if (!value) return '—';
  return value.replace('T', ' ').slice(0, 16);
}

async function loadSemesterContext(): Promise<void> {
  const semesters = await apiListSemesters();
  if (!semesterId.value) {
    semesterId.value = semesters.find((item) => item.status === 'ACTIVE')?.id ?? semesters[0]?.id ?? null;
  }
  semesterName.value = semesters.find((item) => item.id === semesterId.value)?.semesterName ?? '';
}

async function loadTeacher(): Promise<void> {
  const [teachers, progressList] = await Promise.all([
    apiListTeachers({ page: 1, size: 500 }),
    semesterId.value ? apiGetUploadProgress(semesterId.value) : Promise.resolve([]),
  ]);
  teacher.value = teachers.records.find((item) => item.id === teacherId) ?? null;
  progress.value = progressList.find((item) => item.teacherId === teacherId) ?? null;
}

async function loadSchedules(): Promise<void> {
  if (!semesterId.value) return;
  scheduleLoading.value = true;
  try {
    scheduleItems.value = await apiMySchedules({ semesterId: semesterId.value, teacherId });
  } finally {
    scheduleLoading.value = false;
  }
}

async function loadCourses(): Promise<void> {
  if (!semesterId.value) return;
  const all = await apiListCourses({ semesterId: semesterId.value });
  courses.value = all.filter((item) => item.teacherId === teacherId);
  courseRevisionFlags.value =
    courses.value.length > 0 ? await apiCourseRevisionFlags(courses.value.map((item) => item.id)) : {};
}

/** 开放注记数据；失败不阻塞课表主流程 */
async function loadLabNotes(): Promise<void> {
  if (!semesterId.value) return;
  try {
    const [openings, overview] = await Promise.all([
      apiListLabOpenings({ semesterId: semesterId.value }),
      apiGetLabOpeningOverview(semesterId.value),
    ]);
    labNotes.value = buildLabNoteIndex(openings, overview.labs);
  } catch {
    labNotes.value = { openingsByLab: new Map(), overviewMap: new Map() };
  }
}

async function loadAll(): Promise<void> {
  if (!semesterId.value) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    await Promise.all([loadSchedules(), loadCourses(), loadLabNotes(), loadTeacher()]);
  } finally {
    loading.value = false;
  }
}

/** 返回书架：由 FolderTransition 反向播放「合上文件夹」动画 */
function goBack(): void {
  void router.push('/schedules');
}

function openScheduleEdit(item: IMyScheduleItem): void {
  editingItem.value = item;
  editVisible.value = true;
}

function openCourseEdit(course: ICourse): void {
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

function handleSelectionChange(rows: IMyScheduleItem[]): void {
  selectedIds.value = rows.map((row) => row.id);
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除 ${displayName.value} 的 ${selectedIds.value.length} 条课表记录吗？`,
    '批量删除确认',
    { type: 'warning' },
  );
  await apiBatchDeleteSchedules(selectedIds.value);
  ElMessage.success('删除成功');
  selectedIds.value = [];
  await loadAll();
}

async function handleRemind(): Promise<void> {
  if (!semesterId.value) return;
  await ElMessageBox.confirm(
    `将向 ${displayName.value} 发送课表上传催办通知，确定继续吗？`,
    '催办确认',
    { type: 'warning', confirmButtonText: '发送催办' },
  );
  reminding.value = true;
  try {
    await apiSendUploadRemind(semesterId.value, [teacherId]);
    ElMessage.success('已发送催办通知');
    await loadTeacher();
  } finally {
    reminding.value = false;
  }
}

onMounted(async () => {
  await loadSemesterContext();
  await loadAll();
});
</script>

<style scoped lang="scss">
.teacher-schedule {
  &__identity {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }

  &__back {
    flex: none;
  }

  &__avatar {
    flex: none;
    border: 2px solid var(--border-subtle);
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

  &__section-spacer {
    flex: 1;
  }

  &__muted {
    color: var(--el-text-color-placeholder);
  }

  &__empty-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-secondary);
  }

  &__empty-sub {
    margin-top: var(--space-1);
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
