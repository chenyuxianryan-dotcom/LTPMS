<template>
  <div>
    <div v-if="canManage" class="catalog-tab__bar">
      <el-tooltip :content="copyDisabledReason" placement="top" :disabled="!copyDisabledReason">
        <span>
          <el-button :icon="CopyDocument" :disabled="!canWrite || !!copyDisabledReason" @click="handleCopyFromPrevious">
            从上学期复制
          </el-button>
        </span>
      </el-tooltip>
      <el-button type="primary" :icon="Plus" :disabled="!canWrite" @click="openCreate">新建实验</el-button>
      <el-button :icon="Upload" :disabled="!canWrite" @click="importVisible = true">批量导入</el-button>
    </div>

    <el-alert
      v-if="canManage && !isCurrentSemester"
      type="info"
      :closable="false"
      show-icon
      title="历史学期数据只读，不可编辑"
      class="catalog-tab__notice"
    />
    <el-alert
      v-else-if="!canManage"
      type="info"
      :closable="false"
      show-icon
      :title="isAdminView ? '管理员只读视图：实验目录由课程教师维护' : '实验目录由课程教师维护，学生只读'"
      class="catalog-tab__notice"
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
      <!-- 操作列：教师全量；管理员只读视图保留材料清单/AI 档案（隐藏编辑/删除）；学生走 NLQ 白名单未放行（r2-H1） -->
      <el-table-column v-if="canManage || isAdminView" label="操作" :width="isAdminView ? 170 : 340" fixed="right">
        <template #default="{ row }">
          <template v-if="!isAdminView">
            <el-button link type="primary" :disabled="!canWrite" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :disabled="!canWrite" @click="handleDelete(row)">删除</el-button>
            <!-- T04：目录行布置任务——切「任务」Tab 并预填标题（实验名）+ 默认选中该实验；ADMIN 只读视图隐藏 -->
            <el-button link type="primary" :disabled="!canWrite" @click="handleAssignTask(row)">布置任务</el-button>
          </template>
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
      @success="load"
    />

    <!-- 材料清单 -->
    <el-dialog v-model="materialVisible" :title="`材料清单 · ${materialTarget?.experimentName ?? ''}`" width="640px">
      <div v-loading="materialLoading">
        <el-empty v-if="!materialLoading && !materialText" description="暂无清单" :image-size="60" />
        <pre v-else class="catalog-tab__material">{{ materialText }}</pre>
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { CopyDocument, Plus, Upload } from '@element-plus/icons-vue';
import ArchiveDialog from '@/components/ArchiveDialog.vue';
import ImportDialog from '@/components/ImportDialog.vue';
import {
  apiCopyAvailability,
  apiCopyFromPrevious,
  apiCreateExperiment,
  apiDeleteExperiment,
  apiUpdateExperiment,
} from '@/api/experiment';
import { apiContainerExperiments } from '@/api/project';
import { apiListSemesters } from '@/api/semester';
import { apiListDictsByType } from '@/api/dict';
import { apiNlqAgent } from '@/api/nlq';
import { DICT_TYPES, ERROR_CODE } from '@/constants';
import { useUserStore } from '@/store/user';
import type { IDictItem, IExperiment, IProject, ISemester } from '@/types/api';
import type { IBizError } from '@/utils/request';

const props = defineProps<{ project: IProject; canManage: boolean }>();

/** T04：目录行「布置任务」——上抛该行给父组件，由其切「任务」Tab 并预填 TaskFormDrawer */
const emit = defineEmits<{ (e: 'assign-task', row: IExperiment): void }>();

function handleAssignTask(row: IExperiment): void {
  emit('assign-task', row);
}

const userStore = useUserStore();
/** 管理员只读视图（2026-09-20 权限边界）：隐藏编辑/删除/批量导入，保留材料清单与 AI 档案等读侧入口 */
const isAdminView = computed(() => userStore.isAdmin);

const courseId = computed(() => props.project.courseId ?? null);

const records = ref<IExperiment[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const importVisible = ref(false);
const editing = ref(false);
const formRef = ref<FormInstance>();

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
  courseId: 0,
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

/* -------------------------------------------------- 学期口径与复制探针 */

const semesters = ref<ISemester[]>([]);

/** 容器所属课程是否属于进行中的学期（历史学期只读） */
const isCurrentSemester = computed(() => {
  if (props.project.semesterId == null) return false;
  const semester = semesters.value.find((item) => item.id === props.project.semesterId);
  return semester?.status === 'ACTIVE';
});

const canWrite = computed(() => props.canManage && isCurrentSemester.value);

const copyAvailable = ref<boolean | null>(null);
const previousSemesterName = ref<string | null>(null);

/** 置灰原因文案（R3：无上学期同课程号目录 → 置灰，43001 兜底不变） */
const copyDisabledReason = computed(() => {
  if (copyAvailable.value === null) return '正在检查上学期目录…';
  if (copyAvailable.value) return '';
  return previousSemesterName.value
    ? `上学期（${previousSemesterName.value}）无实验目录`
    : '上学期无同课程号课程';
});

async function loadCopyAvailability(): Promise<void> {
  if (!props.canManage || !courseId.value) return;
  try {
    const availability = await apiCopyAvailability(courseId.value);
    copyAvailable.value = availability.available;
    previousSemesterName.value = availability.previousSemesterName;
  } catch {
    copyAvailable.value = false;
    previousSemesterName.value = null;
  }
}

/* ------------------------------------------------------------- 目录读写 */

async function load(): Promise<void> {
  loading.value = true;
  try {
    records.value = await apiContainerExperiments(props.project.id);
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
    courseId: row.courseId,
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
    const { id, courseId: formCourseId, ...payload } = form;
    if (editing.value) {
      await apiUpdateExperiment(id, { ...payload, courseId: formCourseId });
    } else if (courseId.value) {
      await apiCreateExperiment(courseId.value, payload);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: IExperiment): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除实验项目「${row.experimentName}」吗？`, '删除确认', { type: 'warning' });
  } catch {
    return;
  }
  await apiDeleteExperiment(row.id);
  ElMessage.success('删除成功');
  await load();
}

async function handleCopyFromPrevious(): Promise<void> {
  if (!courseId.value) return;
  try {
    const result = await apiCopyFromPrevious(courseId.value);
    ElMessage.success(`已复制 ${result.copiedCount} 个实验项目`);
    await load();
  } catch (error) {
    if ((error as IBizError).code === ERROR_CODE.PREVIOUS_SEMESTER_EMPTY) {
      ElMessage.warning('上学期该课程无实验项目可复制');
    }
  }
}

/* ------------------------------------------------------------- 材料清单 */

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
  // 学期/字典/复制探针均为教师侧写入口的依赖（学生无权限且无写操作），学生只拉目录
  if (props.canManage) {
    semesters.value = await apiListSemesters();
    void Promise.all([loadDicts(), loadCopyAvailability()]);
  }
  await load();
});
</script>

<style scoped lang="scss">
.catalog-tab {
  &__bar {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-4);
  }

  &__notice {
    margin-bottom: var(--space-4);
  }

  &__material {
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
}
</style>
