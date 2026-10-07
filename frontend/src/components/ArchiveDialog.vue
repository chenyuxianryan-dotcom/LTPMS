<template>
  <el-dialog
    :model-value="modelValue"
    :title="`数字档案 · ${entityTitle}`"
    width="640px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <el-empty v-if="!loading && !hasText && !hasStats" description="暂无档案数据" :image-size="60" />
      <template v-else>
        <pre v-if="hasText" class="archive-dialog__text">{{ result?.text }}</pre>
        <el-alert
          v-else-if="!loading && hasStats"
          :title="result?.error ?? '档案生成失败，以下为真实统计数据'"
          type="warning"
          show-icon
          :closable="false"
          class="archive-dialog__fallback"
        />
        <el-descriptions v-if="hasStats" :column="2" border size="small" class="archive-dialog__stats">
          <el-descriptions-item v-for="(value, key) in statEntries" :key="key" :label="String(key)">
            {{ value }}
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="result?.cached" class="archive-dialog__cached">
          <el-tag size="small" type="info" effect="plain">
            {{ result?.cached_at ? `缓存档案 · 数据截至 ${result.cached_at}` : '缓存档案（24 小时内有效）' }}
          </el-tag>
        </div>
      </template>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button :icon="RefreshRight" :loading="loading" @click="load(true)">重新生成</el-button>
      <el-button type="primary" :disabled="!hasText" @click="handleCopy">复制档案</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { RefreshRight } from '@element-plus/icons-vue';
import { apiNlqArchive, type INlqArchiveResult } from '@/api/nlq';

/**
 * 实验台数字档案对话框（T3.2）：实验室/实验项目共用一份（防臃肿复用约定）。
 * 生成失败时降级展示真实统计数据（红线：无档案时不显示编造内容）。
 */
const props = defineProps<{
  modelValue: boolean;
  entityType: 'laboratory' | 'experiment';
  entityId: number | null;
  entityTitle: string;
}>();

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>();

const loading = ref(false);
const result = ref<INlqArchiveResult | null>(null);

const hasText = computed(() => !!result.value?.success && !!result.value?.text);
const hasStats = computed(() => !!result.value?.stats && Object.keys(result.value.stats).length > 0);

/** 扁平化统计数据（basic/usage 嵌套拍平为 label: value） */
const statEntries = computed<Record<string, string>>(() => {
  const stats = result.value?.stats;
  if (!stats) return {};
  const flat: Record<string, string> = {};
  const walk = (obj: Record<string, unknown>, prefix = ''): void => {
    for (const [key, value] of Object.entries(obj)) {
      if (value !== null && typeof value === 'object') {
        walk(value as Record<string, unknown>, prefix ? `${prefix}.${key}` : key);
      } else {
        flat[prefix ? `${prefix}.${key}` : key] = String(value ?? '-');
      }
    }
  };
  walk(stats);
  return flat;
});

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    result.value = null;
    load(false);
  },
  { immediate: false },
);

/** 加载档案；force=true 绕过 24h 缓存重新生成（收掉上批「缓存无刷新入口」遗留） */
function load(force: boolean): void {
  if (props.entityId == null || loading.value) return;
  loading.value = true;
  apiNlqArchive(props.entityType, props.entityId, force)
    .then((res) => {
      result.value = res;
      if (force && res?.success) {
        ElMessage.success('已重新生成');
      }
    })
    .catch(() => {
      // 错误由 request 拦截器统一提示；此处保持空态兜底
      if (!force) result.value = null;
    })
    .finally(() => {
      loading.value = false;
    });
}

async function handleCopy(): Promise<void> {
  if (!result.value?.text) return;
  try {
    await navigator.clipboard.writeText(result.value.text);
    ElMessage.success('已复制到剪贴板');
  } catch {
    ElMessage.warning('复制失败，请手动选择复制');
  }
}
</script>

<style scoped lang="scss">
.archive-dialog__text {
  margin: 0 0 var(--space-3);
  padding: var(--space-4);
  background: var(--bg-subtle);
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
}

.archive-dialog__fallback {
  margin-bottom: var(--space-3);
}

.archive-dialog__stats {
  margin-bottom: var(--space-2);
}

.archive-dialog__cached {
  margin-top: var(--space-2);
}
</style>
