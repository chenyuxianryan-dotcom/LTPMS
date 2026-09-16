<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="420px"
    @update:model-value="emit('update:modelValue', $event)"
    @open="load"
  >
    <div v-loading="loading" class="revision-drawer">
      <el-empty v-if="!loading && records.length === 0" description="暂无修订记录" :image-size="72" />
      <div v-for="(rev, index) in records" :key="index" class="revision-item">
        <div class="revision-item__head">
          <span class="revision-item__field">{{ REVISION_FIELD_LABELS[rev.fieldName] ?? rev.fieldName }}</span>
          <el-tag size="small" :type="rev.sourceRole === 'ADMIN' ? 'danger' : 'primary'" effect="light">
            {{ rev.sourceRole === 'ADMIN' ? '管理员' : '教师' }}
          </el-tag>
        </div>
        <div class="revision-item__values">
          <span class="revision-item__old" :title="display(rev.oldValue)">{{ display(rev.oldValue) }}</span>
          <el-icon class="revision-item__arrow"><Right /></el-icon>
          <span class="revision-item__new" :title="display(rev.newValue)">{{ display(rev.newValue) }}</span>
        </div>
        <div class="revision-item__foot">{{ rev.operatorName }} · {{ formatTime(rev.createdAt) }}</div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Right } from '@element-plus/icons-vue';
import { apiListCourseRevisions } from '@/api/course';
import { apiListScheduleRevisions } from '@/api/schedule';
import { REVISION_FIELD_LABELS } from '@/constants';
import type { IRevisionView } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  title: string;
  targetType: 'SCHEDULE' | 'COURSE';
  targetId: number | null;
}>();

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>();

const loading = ref(false);
const records = ref<IRevisionView[]>([]);

async function load(): Promise<void> {
  if (!props.targetId) return;
  loading.value = true;
  try {
    records.value =
      props.targetType === 'SCHEDULE'
        ? await apiListScheduleRevisions(props.targetId)
        : await apiListCourseRevisions(props.targetId);
  } finally {
    loading.value = false;
  }
}

function display(value?: string | null): string {
  return value === null || value === undefined || value === '' ? '（空）' : value;
}

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '';
}
</script>

<style scoped lang="scss">
.revision-drawer {
  min-height: 120px;
}

.revision-item {
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--border-subtle);

  &:last-child {
    border-bottom: none;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-2);
  }

  &__field {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__values {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 13px;
    margin-bottom: var(--space-1);
  }

  &__old {
    color: var(--text-tertiary);
    text-decoration: line-through;
    max-width: 40%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__arrow {
    color: var(--text-quaternary);
    flex-shrink: 0;
  }

  &__new {
    color: var(--brand-600);
    font-weight: 500;
    max-width: 52%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__foot {
    font-size: 12px;
    color: var(--text-quaternary);
  }
}
</style>
