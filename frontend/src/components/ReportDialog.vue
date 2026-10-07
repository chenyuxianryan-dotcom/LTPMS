<template>
  <el-dialog
    :model-value="modelValue"
    :title="`全项目报告 · ${projectName}`"
    width="760px"
    top="6vh"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="generating" :element-loading-text="stageText">
      <el-empty v-if="!generating && !hasText && !hasStats" description="暂无报告数据" :image-size="60" />
      <template v-else>
        <el-alert
          v-if="!hasText && hasStats"
          :title="result?.error ?? '报告生成失败，以下为真实统计数据'"
          type="warning"
          show-icon
          :closable="false"
          class="report-dialog__fallback"
        />
        <div v-if="hasText" class="report-dialog__meta">
          <el-tag size="small" type="info" effect="plain">
            {{ result?.cached ? `缓存报告 · 数据截至 ${result?.generated_at ?? '—'}` : `数据截至 ${result?.generated_at ?? '—'}` }}
          </el-tag>
        </div>
        <pre v-if="hasText" class="report-dialog__text">{{ result?.text }}</pre>
        <el-descriptions v-if="!hasText && hasStats" :column="2" border size="small">
          <el-descriptions-item v-for="(value, key) in statEntries" :key="key" :label="String(key)">
            {{ value }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button :icon="RefreshRight" :loading="generating" @click="generate(true)">重新生成</el-button>
      <el-button
        type="primary"
        :icon="Download"
        :loading="exporting"
        :disabled="!hasText"
        @click="handleExportPdf"
      >
        导出 PDF
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Download, RefreshRight } from '@element-plus/icons-vue';
import { apiNlqReport, type INlqReportResult } from '@/api/nlq';
import { apiExportReportPdf } from '@/api/project';
import { saveBlob } from '@/utils/request';

/**
 * 全项目报告对话框（T09）：生成中进度 → 六节预览 → 导出 PDF / 重新生成。
 * 红线：模型不可用时降级展示确定性统计（stats），不白屏；缓存命中显示「数据截至 …」。
 * 附录成员完成情况表由后端导出 PDF 时从库直出，不经此组件、不进模型上下文。
 */
const props = defineProps<{
  modelValue: boolean;
  projectId: number | null;
  projectName: string;
}>();

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>();

const generating = ref(false);
const exporting = ref(false);
const stageText = ref('正在聚合数据并生成报告…');
const result = ref<INlqReportResult | null>(null);

const hasText = computed(() => !!result.value?.success && !!result.value?.text);
const hasStats = computed(() => !!result.value?.stats && Object.keys(result.value.stats).length > 0);

/** 降级统计扁平化（仅在无正文时展示） */
const statEntries = computed<Record<string, string>>(() => {
  const stats = result.value?.stats;
  if (!stats) return {};
  const flat: Record<string, string> = {};
  const walk = (obj: Record<string, unknown>, prefix = ''): void => {
    for (const [key, value] of Object.entries(obj)) {
      if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
        walk(value as Record<string, unknown>, prefix ? `${prefix}.${key}` : key);
      } else {
        flat[prefix ? `${prefix}.${key}` : key] = Array.isArray(value) ? `[${(value as unknown[]).length} 项]` : String(value ?? '-');
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
    if (props.projectId == null) return;
    generate(false);
  },
  { immediate: false },
);

async function generate(force: boolean): Promise<void> {
  if (props.projectId == null || generating.value) return;
  generating.value = true;
  stageText.value = force ? '正在重新生成报告…' : '正在聚合数据并生成报告…';
  try {
    result.value = await apiNlqReport(props.projectId, force);
    if (force && result.value?.success) {
      ElMessage.success('已重新生成');
    }
  } catch {
    // 业务错误由 request 拦截器统一提示；保持空态兜底
    result.value = null;
  } finally {
    generating.value = false;
  }
}

async function handleExportPdf(): Promise<void> {
  if (props.projectId == null || !result.value?.text) return;
  exporting.value = true;
  try {
    const blob = await apiExportReportPdf(props.projectId, {
      title: `实验项目报告 · ${props.projectName}`,
      text: result.value.text,
      generatedAt: result.value.generated_at,
    });
    saveBlob(blob, `项目报告_${props.projectName}_${dateStamp()}.pdf`);
    ElMessage.success('已开始下载');
  } catch {
    // 下载守卫已提示业务错误
  } finally {
    exporting.value = false;
  }
}

function dateStamp(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`;
}
</script>

<style scoped lang="scss">
.report-dialog__meta {
  margin-bottom: var(--space-2);
}

.report-dialog__text {
  margin: 0;
  padding: var(--space-4);
  max-height: 60vh;
  overflow-y: auto;
  background: var(--bg-subtle);
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

.report-dialog__fallback {
  margin-bottom: var(--space-3);
}
</style>
