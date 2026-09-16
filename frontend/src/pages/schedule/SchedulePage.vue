<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">课表管理</div>
        <div class="page-header__desc">{{ viewDesc }}</div>
      </div>
      <div class="filter-bar">
        <el-select v-model="semesterId" placeholder="选择学期" style="width: 220px" @change="handleSearch">
          <el-option v-for="item in semesters" :key="item.id" :label="item.semesterName" :value="item.id" />
        </el-select>
        <el-radio-group v-model="view">
          <el-radio-button value="shelf">教师书架</el-radio-button>
          <el-radio-button value="table">全校总表</el-radio-button>
        </el-radio-group>
        <el-button
          v-if="view === 'table'"
          type="danger"
          plain
          :disabled="selectedIds.length === 0"
          @click="handleBatchDelete"
        >
          批量删除（{{ selectedIds.length }}）
        </el-button>
      </div>
    </div>

    <TeacherShelf v-if="view === 'shelf'" :semester-id="semesterId" @open="handleOpenTeacher" />

    <template v-else>
      <el-table v-loading="loading" :data="records" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="courseCode" label="课程号" width="120" />
        <el-table-column prop="courseName" label="课程名" min-width="180" />
        <el-table-column prop="teacherName" label="授课教师" width="110" />
        <el-table-column prop="labCode" label="上课地点" width="120" />
        <el-table-column label="开放注记" min-width="140">
          <template #default="{ row }">
            <el-tooltip v-if="noteOf(row.labCode).type !== 'none'" :content="noteOf(row.labCode).tip" placement="top" :show-after="200">
              <el-tag :type="noteOf(row.labCode).tagType" size="small" :effect="noteOf(row.labCode).effect">
                {{ noteOf(row.labCode).text }}
              </el-tag>
            </el-tooltip>
            <span v-else class="schedule-page__muted">—</span>
          </template>
        </el-table-column>
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import TeacherShelf from '@/components/schedule/TeacherShelf.vue';
import { apiGetLabOpeningOverview, apiListLabOpenings } from '@/api/labOpening';
import { apiBatchDeleteSchedules, apiListSchedules } from '@/api/schedule';
import { apiListSemesters } from '@/api/semester';
import { buildLabNoteIndex, labNoteOf, type ILabNote, type ILabNoteIndex } from '@/utils/labNote';
import { markFolderOpen, type IFolderContext } from '@/utils/folderTransition';
import type { ISchedule, ISemester } from '@/types/api';

const router = useRouter();
const semesterId = ref<number | null>(null);
const semesters = ref<ISemester[]>([]);
const view = ref<'shelf' | 'table'>('shelf');
const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const records = ref<ISchedule[]>([]);
const selectedIds = ref<number[]>([]);
const labNotes = ref<ILabNoteIndex>({ openingsByLab: new Map(), overviewMap: new Map() });

const viewDesc = computed(() =>
  view.value === 'shelf'
    ? '点击教师书本打开其课表；支持搜索教师、按导入状态筛选、一键催办未导入'
    : '全校课表总览；课表由教师本人在「课程管理」页导入，系统按「课程号 + 学期」自动归并生成实验课程',
);

/** 按实验室聚合的开放注记：矛盾 > 未开放 > 正常开放段数 */
function noteOf(labCode: string): ILabNote {
  return labNoteOf(labNotes.value, labCode);
}

async function loadSemesters(): Promise<void> {
  semesters.value = await apiListSemesters();
  // 默认选中当前学期
  const current = semesters.value.find((item) => item.status === 'ACTIVE');
  if (current) {
    semesterId.value = current.id;
  }
  await refreshAll();
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

/** 开放注记数据：开放时段按实验室分组 + 总览矛盾数；失败不阻塞课表主流程 */
async function loadOpenings(): Promise<void> {
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

/** 课表与开放注记一并刷新（开放注记仅总表视图需要） */
async function refreshAll(): Promise<void> {
  if (view.value === 'table') {
    await Promise.all([loadData(), loadOpenings()]);
  }
}

function handleSearch(): void {
  page.value = 1;
  void refreshAll();
}

function handleSelectionChange(rows: ISchedule[]): void {
  selectedIds.value = rows.map((row) => row.id);
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条课表记录吗？`, '批量删除确认', { type: 'warning' });
  await apiBatchDeleteSchedules(selectedIds.value);
  ElMessage.success('删除成功');
  selectedIds.value = [];
  await refreshAll();
}

/** 书架点击：登记过渡起点后跳转教师课表页 */
function handleOpenTeacher(ctx: IFolderContext): void {
  markFolderOpen(ctx);
  void router.push({
    path: `/schedules/teacher/${ctx.teacherId}`,
    query: semesterId.value ? { semesterId: String(semesterId.value) } : undefined,
  });
}

onMounted(() => {
  void loadSemesters();
});

watch(view, () => {
  if (view.value === 'table') {
    void refreshAll();
  }
});
</script>

<style scoped lang="scss">
.page-container__pagination {
  margin-top: var(--space-4);
  justify-content: flex-end;
}

.schedule-page__muted {
  color: var(--el-text-color-placeholder);
}
</style>
