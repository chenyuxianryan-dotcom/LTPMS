<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-header__title">实验项目</div>
        <div class="page-header__desc">维护我的实验项目；历史学期只读，可从上学期复制</div>
      </div>
      <div class="filter-bar">
        <el-select v-model="courseId" placeholder="选择课程" style="width: 280px" @change="loadExperiments">
          <el-option
            v-for="item in courses"
            :key="item.id"
            :label="`${item.courseName}（${item.semesterName ?? ''}）`"
            :value="item.id"
          />
        </el-select>
        <el-tooltip content="从上学期同课程号复制实验项目" placement="top">
          <span>
            <el-button :icon="CopyDocument" :disabled="!courseId || !isCurrentSemester" @click="handleCopyFromPrevious">
              从上学期复制
            </el-button>
          </span>
        </el-tooltip>
        <el-button :icon="RefreshLeft" :disabled="!isCurrentSemester" @click="openMigration">上学期智能迁移</el-button>
        <el-button type="primary" :icon="Plus" :disabled="!courseId || !isCurrentSemester" @click="openCreate">新建实验项目</el-button>
        <el-button :icon="Upload" :disabled="!courseId || !isCurrentSemester" @click="importVisible = true">批量导入</el-button>
      </div>
    </div>

    <el-alert
      v-if="courseId && !isCurrentSemester"
      type="info"
      :closable="false"
      show-icon
      title="历史学期数据只读，不可编辑"
      class="experiment-page__notice"
    />

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="experimentCode" label="实验编号" width="170" class-name="font-mono" />
      <el-table-column prop="experimentName" label="实验名称" min-width="180" />
      <el-table-column prop="category" label="类别" width="110" />
      <el-table-column prop="type" label="类型" width="110" />
      <el-table-column prop="subject" label="所属学科" width="120" />
      <el-table-column prop="requirement" label="要求" width="110" />
      <el-table-column prop="groupSize" label="每组人数" width="95" align="right" class-name="tabular-nums" />
      <el-table-column prop="hours" label="学时" width="80" align="right" class-name="tabular-nums" />
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="!isCurrentSemester" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="!isCurrentSemester" @click="handleDelete(row)">删除</el-button>
          <el-button link type="success" @click="openMaterial(row)">材料清单</el-button>
          <el-button link type="success" @click="openArchive(row)">AI 档案</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑实验项目' : '新建实验项目'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="实验名称" prop="experimentName">
          <el-input v-model="form.experimentName" />
        </el-form-item>
        <el-form-item label="实验类别" prop="category">
          <el-select v-model="form.category" filterable allow-create default-first-option clearable placeholder="选择或输入自定义类别" style="width: 100%">
            <el-option v-for="item in optionsFor('category')" :key="item.dictValue" :label="item.dictLabel" :value="item.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验类型" prop="type">
          <el-select v-model="form.type" filterable allow-create default-first-option clearable placeholder="选择或输入自定义类型" style="width: 100%">
            <el-option v-for="item in optionsFor('type')" :key="item.dictValue" :label="item.dictLabel" :value="item.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属学科" prop="subject">
          <el-select v-model="form.subject" filterable allow-create default-first-option clearable placeholder="选择或输入自定义学科" style="width: 100%">
            <el-option v-for="item in optionsFor('subject')" :key="item.dictValue" :label="item.dictLabel" :value="item.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验要求" prop="requirement">
          <el-select v-model="form.requirement" filterable allow-create default-first-option clearable placeholder="选择或输入自定义要求" style="width: 100%">
            <el-option v-for="item in optionsFor('requirement')" :key="item.dictValue" :label="item.dictLabel" :value="item.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验者类别" prop="experimenterType">
          <el-select v-model="form.experimenterType" filterable allow-create default-first-option clearable placeholder="选择或输入自定义类别" style="width: 100%">
            <el-option v-for="item in optionsFor('experimenter_type')" :key="item.dictValue" :label="item.dictLabel" :value="item.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="每组人数" prop="groupSize">
          <el-input-number v-model="form.groupSize" :min="1" />
        </el-form-item>
        <el-form-item label="实验学时" prop="hours">
          <el-input-number v-model="form.hours" :min="0.5" :step="0.5" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importVisible"
      title="实验项目导入"
      action="/experiments/import"
      template-url="/experiments/import/template"
      :extra-data="courseId ? { courseId } : undefined"
      @success="loadExperiments"
    />

    <!-- 上学期智能迁移草稿确认 -->
    <el-dialog v-model="migrationVisible" title="上学期智能迁移" width="720px" :close-on-click-modal="false">
      <div v-loading="migrationLoading">
        <el-empty v-if="!migrationLoading && migrationDrafts.length === 0" description="上学期无可迁移的实验项目" :image-size="60" />
        <template v-else>
          <el-alert
            v-if="migrationDrafts.length > 0"
            :title="`共 ${migrationDrafts.length} 门课程可从上学期迁移，确认后批量复制实验项目（自动重编实验编号）`"
            type="info"
            show-icon
            :closable="false"
            class="experiment-page__migration-tip"
          />
          <el-table :data="migrationDrafts" border size="small" max-height="360">
            <el-table-column prop="courseCode" label="课程号" width="120" />
            <el-table-column prop="courseName" label="课程名" min-width="160" />
            <el-table-column prop="previousSemesterName" label="上学期" width="130" />
            <el-table-column prop="previousExperimentCount" label="可迁移实验数" width="110" align="right" class-name="tabular-nums" />
            <el-table-column label="实验项目" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.experimentNames.join('、') }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="migrationVisible = false">取消</el-button>
        <el-button type="primary" :disabled="migrationDrafts.length === 0" :loading="migrating" @click="handleMigrationConfirm">
          确认迁移
        </el-button>
      </template>
    </el-dialog>

    <!-- 材料清单 -->
    <el-dialog v-model="materialVisible" :title="`材料清单 · ${materialTarget?.experimentName ?? ''}`" width="640px">
      <div v-loading="materialLoading">
        <el-empty v-if="!materialLoading && !materialText" description="暂无清单" :image-size="60" />
        <pre v-else class="experiment-page__material">{{ materialText }}</pre>
      </div>
      <template #footer>
        <el-button @click="materialVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!materialText" @click="copyMaterial">复制清单</el-button>
      </template>
    </el-dialog>

    <!-- 实验项目数字档案（AI 生成，共用 ArchiveDialog） -->
    <ArchiveDialog
      v-model="archiveVisible"
      entity-type="experiment"
      :entity-id="archiveTarget?.id ?? null"
      :entity-title="archiveTarget?.experimentName ?? ''"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { CopyDocument, Plus, RefreshLeft, Upload } from '@element-plus/icons-vue';
import ImportDialog from '@/components/ImportDialog.vue';
import {
  apiCopyFromPrevious,
  apiCreateExperiment,
  apiDeleteExperiment,
  apiListExperiments,
  apiMigrationConfirm,
  apiMigrationPreview,
  apiUpdateExperiment,
  type IMigrationDraft,
} from '@/api/experiment';
import { apiNlqAgent } from '@/api/nlq';
import { apiListCourses } from '@/api/course';
import { apiListSemesters } from '@/api/semester';
import { apiListDictsByType } from '@/api/dict';
import { DICT_TYPES, ERROR_CODE } from '@/constants';
import type { ICourse, IDictItem, IExperiment, ISemester } from '@/types/api';
import type { IBizError } from '@/utils/request';

const currentRoute = useRoute();

const courses = ref<ICourse[]>([]);
const semesters = ref<ISemester[]>([]);
const courseId = ref<number | null>(null);
const records = ref<IExperiment[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const importVisible = ref(false);
const editing = ref(false);
const formRef = ref<FormInstance>();

// 上学期智能迁移
const migrationVisible = ref(false);
const migrationLoading = ref(false);
const migrating = ref(false);
const migrationDrafts = ref<IMigrationDraft[]>([]);

// 材料清单
const materialVisible = ref(false);
const materialLoading = ref(false);
const materialText = ref('');
const materialTarget = ref<IExperiment | null>(null);

// 数字档案
const archiveVisible = ref(false);
const archiveTarget = ref<IExperiment | null>(null);

function openArchive(row: IExperiment): void {
  archiveTarget.value = row;
  archiveVisible.value = true;
}

const dictOptions = reactive<Record<string, IDictItem[]>>({
  category: [],
  type: [],
  subject: [],
  requirement: [],
  experimenter_type: [],
});

/** 字典类型 → 表单字段名 映射 */
const DICT_TO_FORM_KEY: Record<string, keyof typeof form> = {
  category: 'category',
  type: 'type',
  subject: 'subject',
  requirement: 'requirement',
  experimenter_type: 'experimenterType',
};

/**
 * 返回某字典类型的下拉选项；若当前表单值（含历史/手动输入的自定义值）不在字典中，
 * 则动态补一个同文案选项，保证编辑时能正确回显。
 */
function optionsFor(dictType: string): IDictItem[] {
  const options = [...(dictOptions[dictType] ?? [])];
  const value = form[DICT_TO_FORM_KEY[dictType]] as string;
  if (value && !options.some((item) => item.dictValue === value)) {
    options.push({ id: 0, dictType, dictLabel: value, dictValue: value, sortOrder: 0, status: 1 } as IDictItem);
  }
  return options;
}

const EMPTY_FORM = {
  id: 0,
  experimentName: '',
  category: '',
  type: '',
  subject: '',
  requirement: '',
  experimenterType: '',
  groupSize: 1,
  hours: 2,
  sortOrder: 0,
};
const form = reactive({ ...EMPTY_FORM });

const rules: FormRules = {
  experimentName: [{ required: true, message: '请输入实验名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择实验类别', trigger: 'change' }],
  type: [{ required: true, message: '请选择实验类型', trigger: 'change' }],
  subject: [{ required: true, message: '请选择所属学科', trigger: 'change' }],
  requirement: [{ required: true, message: '请选择实验要求', trigger: 'change' }],
  experimenterType: [{ required: true, message: '请选择实验者类别', trigger: 'change' }],
};

/** 当前课程是否属于进行中的学期（历史学期只读） */
const isCurrentSemester = computed(() => {
  const course = courses.value.find((item) => item.id === courseId.value);
  if (!course) return false;
  const semester = semesters.value.find((item) => item.id === course.semesterId);
  return semester?.status === 'ACTIVE';
});

async function loadCourses(): Promise<void> {
  courses.value = await apiListCourses();
  const fromQuery = Number(currentRoute.query.courseId);
  if (fromQuery && courses.value.some((item) => item.id === fromQuery)) {
    courseId.value = fromQuery;
  } else if (courses.value.length > 0) {
    courseId.value = courses.value[0].id;
  }
  await loadExperiments();
}

async function loadExperiments(): Promise<void> {
  if (!courseId.value) return;
  loading.value = true;
  try {
    records.value = await apiListExperiments({ courseId: courseId.value });
  } finally {
    loading.value = false;
  }
}

async function loadDicts(): Promise<void> {
  await Promise.all(DICT_TYPES.map(async (item) => {
    dictOptions[item.value] = await apiListDictsByType(item.value);
  }));
}

function openCreate(): void {
  editing.value = false;
  Object.assign(form, EMPTY_FORM);
  dialogVisible.value = true;
}

function openEdit(row: IExperiment): void {
  editing.value = true;
  Object.assign(form, {
    id: row.id,
    experimentName: row.experimentName,
    category: row.category,
    type: row.type,
    subject: row.subject,
    requirement: row.requirement,
    experimenterType: row.experimenterType,
    groupSize: row.groupSize,
    hours: row.hours,
    sortOrder: row.sortOrder,
  });
  dialogVisible.value = true;
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const { id, ...payload } = form;
    if (editing.value) {
      await apiUpdateExperiment(id, payload);
    } else if (courseId.value) {
      await apiCreateExperiment(courseId.value, payload);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadExperiments();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: IExperiment): Promise<void> {
  await ElMessageBox.confirm(`确定删除实验项目「${row.experimentName}」吗？`, '删除确认', { type: 'warning' });
  await apiDeleteExperiment(row.id);
  ElMessage.success('删除成功');
  await loadExperiments();
}

async function handleCopyFromPrevious(): Promise<void> {
  if (!courseId.value) return;
  try {
    const result = await apiCopyFromPrevious(courseId.value);
    ElMessage.success(`已复制 ${result.copiedCount} 个实验项目`);
    await loadExperiments();
  } catch (error) {
    if ((error as IBizError).code === ERROR_CODE.PREVIOUS_SEMESTER_EMPTY) {
      ElMessage.warning('上学期该课程无实验项目可复制');
    }
  }
}

async function openMigration(): Promise<void> {
  migrationVisible.value = true;
  migrationLoading.value = true;
  migrationDrafts.value = [];
  try {
    migrationDrafts.value = await apiMigrationPreview();
  } catch {
    // 错误由 request 拦截器统一提示
  } finally {
    migrationLoading.value = false;
  }
}

async function handleMigrationConfirm(): Promise<void> {
  migrating.value = true;
  try {
    const result = await apiMigrationConfirm();
    ElMessage.success(`迁移完成：${result.courseCount} 门课程，共 ${result.experimentCount} 个实验项目`);
    migrationVisible.value = false;
    await loadExperiments();
  } catch {
    // 错误由 request 拦截器统一提示
  } finally {
    migrating.value = false;
  }
}

async function openMaterial(row: IExperiment): Promise<void> {
  materialTarget.value = row;
  materialVisible.value = true;
  materialLoading.value = true;
  materialText.value = '';
  try {
    const result = await apiNlqAgent(
      `帮我生成实验项目「${row.experimentName}」（类别：${row.category ?? '-'}，要求：${row.requirement ?? '-'}，每组人数：${row.groupSize ?? '-'}）所需的器材与耗材清单，按「名称 | 数量 | 备注」三列输出`,
    );
    materialText.value = result.answer || '未能生成清单，请稍后重试';
  } catch {
    materialText.value = '生成失败，请稍后重试';
  } finally {
    materialLoading.value = false;
  }
}

async function copyMaterial(): Promise<void> {
  try {
    await navigator.clipboard.writeText(materialText.value);
    ElMessage.success('已复制到剪贴板');
  } catch {
    ElMessage.warning('复制失败，请手动选择复制');
  }
}

onMounted(async () => {
  semesters.value = await apiListSemesters();
  await loadDicts();
  await loadCourses();
});
</script>

<style scoped lang="scss">
.experiment-page__notice {
  margin-bottom: var(--space-4);
}

.experiment-page__migration-tip {
  margin-bottom: var(--space-3);
}

.experiment-page__material {
  margin: 0;
  padding: var(--space-4);
  background: var(--bg-subtle);
  border-radius: var(--radius-md);
  font-family: monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
