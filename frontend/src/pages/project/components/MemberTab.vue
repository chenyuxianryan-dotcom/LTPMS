<template>
  <div>
    <div v-if="canManage" class="member-tab__bar">
      <el-button type="primary" :icon="Plus" @click="openAdd">添加成员</el-button>
      <el-button :icon="Upload" @click="importVisible = true">导入名单</el-button>
      <span class="member-tab__hint">导入：Excel 四列（学号/姓名/班级/专业），陌生学号自动建账号 Ltpms@学号</span>
    </div>

    <el-table v-loading="loading" :data="members" border stripe>
      <el-table-column prop="studentNo" label="学号" width="140" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="className" label="班级" min-width="140" />
      <el-table-column prop="major" label="专业" min-width="140" />
      <el-table-column label="来源" width="130">
        <template #default="{ row }">
          <el-tag effect="plain" size="small">{{ MEMBER_SOURCE_MAP[row.source as string] ?? row.source }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="加入时间" width="150">
        <template #default="{ row }">{{ fmtDateTime(row.joinedAt) }}</template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleRemove(row)">移出</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :image-size="60">
          <template #description>
            <template v-if="canManage">
              该课名单尚未导入，容器暂无成员 —— 先去导入课程名单，再回容器继续组队
            </template>
            <template v-else>暂无成员</template>
          </template>
          <el-button v-if="canManage" type="primary" :icon="Upload" @click="importVisible = true">导入课程名单</el-button>
        </el-empty>
      </template>
    </el-table>

    <!-- 学生库勾选带入 -->
    <el-dialog v-model="addVisible" title="从学生库添加成员" width="640px">
      <div class="member-tab__search">
        <el-input v-model="searchKeyword" placeholder="搜索学号 / 姓名" clearable style="width: 220px" @keyup.enter="handleSearch" />
        <el-button :icon="Search" @click="handleSearch">搜索</el-button>
      </div>
      <el-table
        ref="pickTableRef"
        v-loading="searching"
        :data="candidates"
        border
        max-height="360"
        row-key="id"
        @selection-change="(rows: IStudent[]) => (picked = rows)"
      >
        <el-table-column type="selection" width="48" reserve-selection />
        <el-table-column prop="studentNo" label="学号" width="140" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="className" label="班级" min-width="140" />
        <el-table-column prop="major" label="专业" min-width="140" />
      </el-table>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :disabled="picked.length === 0" @click="handleAdd">
          带入 {{ picked.length || '' }} 人
        </el-button>
      </template>
    </el-dialog>

    <ImportDialog v-model="importVisible" title="导入学生名单" :action="`/projects/${project.id}/members/import`" @success="onImported" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Plus, Search, Upload } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus';
import ImportDialog from '@/components/ImportDialog.vue';
import { apiAddMembers, apiListMembers, apiRemoveMember, apiSearchStudents } from '@/api/project';
import { MEMBER_SOURCE_MAP } from '@/constants';
import type { IProject, IProjectMember, IStudent, IStudentImportResult } from '@/types/api';
import { fmtDateTime } from '../projectUtils';

const props = defineProps<{ project: IProject; canManage: boolean }>();
const emit = defineEmits<{ (e: 'changed'): void }>();

const loading = ref(false);
const members = ref<IProjectMember[]>([]);

async function load(): Promise<void> {
  loading.value = true;
  try {
    members.value = await apiListMembers(props.project.id);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/* ------------------------------------------------------------- 勾选带入 */

const addVisible = ref(false);
const searching = ref(false);
const searchKeyword = ref('');
const candidates = ref<IStudent[]>([]);
const picked = ref<IStudent[]>([]);
const pickTableRef = ref<TableInstance>();

function openAdd(): void {
  picked.value = [];
  candidates.value = [];
  searchKeyword.value = '';
  addVisible.value = true;
}

async function handleSearch(): Promise<void> {
  searching.value = true;
  try {
    const rows = await apiSearchStudents({ keyword: searchKeyword.value || undefined });
    const existing = new Set(members.value.map((m) => m.studentId));
    candidates.value = rows.filter((r) => !existing.has(r.id));
  } finally {
    searching.value = false;
  }
}

async function handleAdd(): Promise<void> {
  const count = await apiAddMembers(
    props.project.id,
    picked.value.map((s) => s.id),
  );
  ElMessage.success(`已带入 ${count} 名成员`);
  addVisible.value = false;
  pickTableRef.value?.clearSelection();
  await load();
  emit('changed');
}

/* ------------------------------------------------------------- 导入名单 */

const importVisible = ref(false);

async function onImported(result: unknown): Promise<void> {
  const receipt = result as IStudentImportResult | undefined;
  if (receipt && receipt.errors?.length) {
    ElMessageBox.alert(
      receipt.errors.map((e) => `第 ${e.row} 行：${e.reason}`).join('\n'),
      `导入完成：成功 ${receipt.successCount}（新建账号 ${receipt.createdAccounts}），异常 ${receipt.errors.length} 行`,
      { type: 'warning' },
    );
  }
  await load();
  emit('changed');
}

/* ------------------------------------------------------------- 移出 */

async function handleRemove(row: IProjectMember): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `将 ${row.realName} 移出课题？其未完成（待领取/进行中/被驳回）任务会被关闭留痕，已提交/已验收任务不受影响。`,
      '移出成员',
      { type: 'warning', confirmButtonText: '移出', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await apiRemoveMember(props.project.id, row.studentId);
  ElMessage.success('已移出');
  await load();
  emit('changed');
}
</script>

<style scoped lang="scss">
.member-tab {
  &__bar {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-4);
  }

  &__hint {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  &__search {
    display: flex;
    gap: var(--space-2);
    margin-bottom: var(--space-3);
  }
}
</style>
