<template>
  <div class="shelf-wrap">
    <div class="shelf-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索教师姓名 / 工号"
        clearable
        class="shelf-toolbar__search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="shelf-toolbar__chips">
        <button
          v-for="chip in chips"
          :key="chip.value"
          type="button"
          class="shelf-chip"
          :class="{ 'is-active': filter === chip.value }"
          @click="filter = chip.value"
        >
          {{ chip.label }} {{ chip.count }}
        </button>
      </div>
      <div class="shelf-toolbar__spacer" />
      <el-button
        type="primary"
        plain
        :disabled="pendingCount === 0"
        :loading="reminding"
        @click="handleRemindAll"
      >
        催办未导入（{{ pendingCount }}）
      </el-button>
    </div>

    <div ref="shelfRef" v-loading="loading" class="shelf">
      <el-empty v-if="!loading && list.length === 0" description="暂无教师账号" :image-size="80" />
      <el-empty
        v-else-if="!loading && filtered.length === 0"
        description="没有符合条件的教师"
        :image-size="80"
      />
      <div v-for="(row, rowIndex) in rows" v-else :key="rowIndex" class="shelf__row">
        <div class="shelf__books">
          <el-tooltip
            v-for="teacher in row"
            :key="teacher.id"
            :content="tipOf(teacher)"
            placement="top"
            :show-after="250"
          >
            <div
              class="book"
              :class="[`book--${widthTier(teacher)}`, { 'is-empty': teacher.scheduleCount === 0 }]"
              :style="bookStyle(teacher)"
              role="button"
              tabindex="0"
              :aria-label="`打开 ${teacher.realName} 的课表`"
              @click="handleBookClick(teacher, $event)"
              @keydown.enter.prevent="handleBookClick(teacher, $event)"
              @keydown.space.prevent="handleBookClick(teacher, $event)"
            >
              <el-avatar :size="40" :src="resolveAvatarUrl(teacher.id, teacher.avatar)" class="book__avatar">
                {{ teacher.realName.slice(0, 1) }}
              </el-avatar>
              <span class="book__name">{{ teacher.realName }}</span>
              <span class="book__count">{{ teacher.scheduleCount > 0 ? `${teacher.scheduleCount} 门` : '未导入' }}</span>
              <span
                v-if="teacher.revisionCount > 0"
                class="book__ribbon"
                :title="`修订 ${teacher.revisionCount} 条`"
              />
            </div>
          </el-tooltip>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { apiGetUploadProgress, apiSendUploadRemind } from '@/api/schedule';
import { apiListTeachers } from '@/api/teacher';
import { resolveAvatarUrl } from '@/utils/avatar';
import { courseColor, type ICourseColor } from '@/utils/courseColor';
import type { IFolderContext } from '@/utils/folderTransition';
import type { ITeacher } from '@/types/api';

const props = defineProps<{ semesterId: number | null }>();

const emit = defineEmits<{ (e: 'open', ctx: IFolderContext): void }>();

interface IShelfTeacher {
  id: number;
  realName: string;
  username: string;
  department?: string;
  avatar?: string | null;
  uploaded: boolean;
  scheduleCount: number;
  latestUploadAt?: string | null;
  revisionCount: number;
}

const BOOK_GAP = 14;
const BOOK_AVERAGE_WIDTH = 70;

const loading = ref(false);
const reminding = ref(false);
const list = ref<IShelfTeacher[]>([]);
const keyword = ref('');
const filter = ref<'all' | 'uploaded' | 'pending'>('all');
const shelfRef = ref<HTMLElement | null>(null);
const perRow = ref(12);
let observer: ResizeObserver | undefined;

const pendingCount = computed(() => list.value.filter((item) => !item.uploaded).length);
const uploadedCount = computed(() => list.value.filter((item) => item.uploaded).length);

const chips = computed(() => [
  { value: 'all' as const, label: '全部', count: list.value.length },
  { value: 'uploaded' as const, label: '已导入', count: uploadedCount.value },
  { value: 'pending' as const, label: '未导入', count: pendingCount.value },
]);

const filtered = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return list.value.filter((item) => {
    if (filter.value === 'uploaded' && !item.uploaded) return false;
    if (filter.value === 'pending' && item.uploaded) return false;
    if (!text) return true;
    return (
      item.realName.toLowerCase().includes(text) ||
      item.username.toLowerCase().includes(text)
    );
  });
});

const rows = computed(() => {
  const result: IShelfTeacher[][] = [];
  for (let i = 0; i < filtered.value.length; i += perRow.value) {
    result.push(filtered.value.slice(i, i + perRow.value));
  }
  return result;
});

function widthTier(teacher: IShelfTeacher): 'thin' | 'mid' | 'thick' {
  if (teacher.scheduleCount === 0) return 'thin';
  return teacher.scheduleCount <= 2 ? 'mid' : 'thick';
}

function teacherColor(teacherId: number): ICourseColor {
  return courseColor(`teacher-${teacherId}`);
}

function bookStyle(teacher: IShelfTeacher): Record<string, string> {
  const color = teacherColor(teacher.id);
  return { '--book-bg': color.bg, '--book-border': color.border, '--book-fg': color.fg };
}

function tipOf(teacher: IShelfTeacher): string {
  const uploadText = teacher.uploaded
    ? `最近上传 ${formatTime(teacher.latestUploadAt)}`
    : '本学期未导入课表';
  const revisionText = teacher.revisionCount > 0 ? ` · 修订 ${teacher.revisionCount} 条` : '';
  return `${teacher.realName} · ${uploadText}${revisionText}`;
}

function formatTime(value?: string | null): string {
  if (!value) return '—';
  return value.replace('T', ' ').slice(0, 16);
}

function handleBookClick(teacher: IShelfTeacher, event: Event): void {
  const element = event.currentTarget as HTMLElement | null;
  if (!element) return;
  const rect = element.getBoundingClientRect();
  emit('open', {
    teacherId: teacher.id,
    label: teacher.realName,
    color: teacherColor(teacher.id),
    rect: { top: rect.top, left: rect.left, width: rect.width, height: rect.height },
  });
}

async function handleRemindAll(): Promise<void> {
  if (!props.semesterId || pendingCount.value === 0) return;
  const pending = list.value.filter((item) => !item.uploaded).map((item) => item.id);
  await ElMessageBox.confirm(
    `将向 ${pending.length} 位未导入课表的教师发送催办通知，确定继续吗？`,
    '批量催办确认',
    { type: 'warning', confirmButtonText: '发送催办' },
  );
  reminding.value = true;
  try {
    const count = await apiSendUploadRemind(props.semesterId, pending);
    ElMessage.success(`已向 ${count} 位教师发送催办通知`);
  } finally {
    reminding.value = false;
  }
}

function computePerRow(): void {
  const width = shelfRef.value?.clientWidth ?? 0;
  if (width <= 0) {
    perRow.value = 12;
    return;
  }
  perRow.value = Math.max(4, Math.floor((width + BOOK_GAP) / (BOOK_AVERAGE_WIDTH + BOOK_GAP)));
}

async function load(): Promise<void> {
  if (!props.semesterId) {
    list.value = [];
    return;
  }
  loading.value = true;
  try {
    const [teachers, progress] = await Promise.all([
      apiListTeachers({ page: 1, size: 500 }),
      apiGetUploadProgress(props.semesterId),
    ]);
    const progressMap = new Map(progress.map((item) => [item.teacherId, item]));
    list.value = teachers.records
      .filter((item: ITeacher) => item.status === 1)
      .map((item: ITeacher) => {
        const status = progressMap.get(item.id);
        return {
          id: item.id,
          realName: item.realName,
          username: item.username,
          department: item.department,
          avatar: item.avatar,
          uploaded: status?.uploaded ?? false,
          scheduleCount: status?.scheduleCount ?? 0,
          latestUploadAt: status?.latestUploadAt ?? null,
          revisionCount: status?.revisionCount ?? 0,
        };
      })
      .sort((a, b) => a.realName.localeCompare(b.realName, 'zh-Hans-CN'));
  } finally {
    loading.value = false;
  }
}

watch(() => props.semesterId, () => void load());

onMounted(() => {
  observer = new ResizeObserver(computePerRow);
  if (shelfRef.value) {
    observer.observe(shelfRef.value);
  }
  computePerRow();
  void load();
});

onBeforeUnmount(() => {
  observer?.disconnect();
});
</script>

<style scoped lang="scss">
.shelf-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.shelf-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);

  &__search {
    width: 240px;
  }

  &__chips {
    display: flex;
    align-items: center;
    gap: var(--space-2);
  }

  &__spacer {
    flex: 1;
  }
}

.shelf-chip {
  padding: 5px 14px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);

  &:hover {
    border-color: var(--border-dark, var(--border-default));
    color: var(--text-primary);
  }

  &.is-active {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-5);
    color: var(--el-color-primary);
    font-weight: 600;
  }
}

.shelf {
  min-height: 260px;
  perspective: 1200px;

  &__row {
    position: relative;
    padding-bottom: 14px;
    margin-bottom: 30px;

    &::after {
      content: '';
      position: absolute;
      left: -8px;
      right: -8px;
      bottom: 0;
      height: 12px;
      border-radius: 3px;
      background: linear-gradient(180deg, var(--border-default), var(--bg-subtle));
      box-shadow: 0 8px 12px -8px rgba(15, 23, 42, 0.4);
    }
  }

  &__books {
    display: flex;
    align-items: flex-end;
    gap: 14px;
  }
}

.book {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 64px;
  height: 200px;
  padding: 16px 6px 12px;
  border: 1px solid var(--book-border);
  border-radius: 5px 8px 8px 5px;
  background:
    linear-gradient(168deg, rgba(255, 255, 255, 0.55), rgba(255, 255, 255, 0) 58%),
    var(--book-bg);
  box-shadow:
    0 3px 6px rgba(15, 23, 42, 0.16),
    inset -6px 0 7px -5px rgba(15, 23, 42, 0.22),
    inset 2px 0 3px -2px rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition:
    transform 0.18s var(--ease-out),
    box-shadow 0.18s var(--ease-out);

  &::after {
    content: '';
    position: absolute;
    top: 8px;
    right: 2px;
    bottom: 8px;
    width: 5px;
    border-radius: 3px;
    background: repeating-linear-gradient(
      0deg,
      rgba(255, 255, 255, 0.9) 0 2px,
      rgba(15, 23, 42, 0.08) 2px 3px
    );
    opacity: 0.55;
    pointer-events: none;
  }

  &--thin {
    width: 50px;
  }

  &--mid {
    width: 64px;
  }

  &--thick {
    width: 78px;
  }

  &.is-empty {
    filter: grayscale(0.9);
    opacity: 0.78;
  }

  &:hover,
  &:focus-visible {
    transform: translateZ(22px) translateY(-6px) rotateY(-4deg);
    box-shadow:
      0 16px 24px rgba(15, 23, 42, 0.24),
      inset -6px 0 7px -5px rgba(15, 23, 42, 0.22),
      inset 2px 0 3px -2px rgba(255, 255, 255, 0.6);
    outline: none;
  }

  &:focus-visible {
    border-color: var(--el-color-primary);
    box-shadow:
      0 0 0 2px var(--el-color-primary-light-7),
      0 16px 24px rgba(15, 23, 42, 0.24);
  }

  &__avatar {
    flex: none;
    border: 2px solid rgba(255, 255, 255, 0.85);
    box-shadow: 0 2px 4px rgba(15, 23, 42, 0.18);
  }

  &__name {
    margin-top: 12px;
    writing-mode: vertical-rl;
    text-orientation: upright;
    letter-spacing: 4px;
    font-size: 14px;
    font-weight: 600;
    color: var(--book-fg);
    max-height: 84px;
    overflow: hidden;
  }

  &__count {
    margin-top: auto;
    font-size: 10px;
    line-height: 14px;
    color: var(--book-fg);
    opacity: 0.72;
    white-space: nowrap;
  }

  &__ribbon {
    position: absolute;
    top: -1px;
    right: 9px;
    width: 9px;
    height: 34px;
    background: var(--el-color-warning);
    clip-path: polygon(0 0, 100% 0, 100% 100%, 50% 76%, 0 100%);
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.25);
  }
}
</style>
