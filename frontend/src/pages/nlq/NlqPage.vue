<template>
  <div class="nlq-page">
    <!-- 主内容区 -->
    <div class="nlq-page__main">
      <div class="nlq-page__head">
        <div class="nlq-page__title">智能查询</div>
        <div class="nlq-page__desc">用自然语言查询实验数据 · 系统自动注入数据权限（NL2SQL）</div>
      </div>

      <!-- 查询卡片 -->
      <el-card class="nlq-page__query-card" shadow="never">
        <el-input
          ref="inputRef"
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="例如：2026-2027学年第1学期我开设了哪些实验课程？"
          @keydown.ctrl.enter="handleQuery"
          @keydown.meta.enter="handleQuery"
        />
        <div class="nlq-page__query-actions">
          <div class="nlq-page__examples">
            <el-tag
              v-for="example in NLQ_EXAMPLES"
              :key="example"
              class="nlq-page__example-tag"
              effect="plain"
              @click="question = example"
            >
              {{ example }}
            </el-tag>
          </div>
          <el-button type="primary" :loading="querying" @click="handleQuery">查询</el-button>
        </div>
      </el-card>

      <!-- 结果区 -->
      <el-card v-if="result" class="nlq-page__result-card" shadow="never">
        <!-- 失败 -->
        <el-result
          v-if="!result.success"
          icon="warning"
          :title="result.error ?? '该问题无法安全执行'"
          :sub-title="result.suggestions?.[0]"
        />

        <!-- 成功 -->
        <template v-else>
          <div class="nlq-page__result-meta">
            <el-tag effect="light">返回 {{ result.rowCount }} 条记录</el-tag>
            <el-tag v-if="result.executionTime" effect="info" type="info">耗时 {{ (result.executionTime / 1000).toFixed(1) }}s</el-tag>
          </div>

          <el-collapse v-if="result.sql" class="nlq-page__sql-collapse">
            <el-collapse-item name="sql">
              <template #title>
                <span class="font-mono nlq-page__sql-title">生成的 SQL · 自动注入数据权限</span>
              </template>
              <pre class="sql-block">{{ result.sql }}</pre>
            </el-collapse-item>
          </el-collapse>

          <el-table :data="tableRows" border stripe max-height="420" class="nlq-page__result-table">
            <el-table-column
              v-for="(col, index) in result.columns"
              :key="String(col)"
              :prop="`c${index}`"
              :label="String(col)"
              min-width="120"
            >
              <template #default="{ row }">
                {{ row[index] }}
              </template>
            </el-table-column>
          </el-table>

          <!-- 继续追问 -->
          <div v-if="result.suggestions && result.suggestions.length > 0" class="nlq-page__suggestions">
            <span class="nlq-page__suggestions-label">继续追问：</span>
            <el-tag
              v-for="suggestion in result.suggestions"
              :key="suggestion"
              class="nlq-page__example-tag"
              effect="plain"
              @click="question = suggestion"
            >
              {{ suggestion }}
            </el-tag>
          </div>
        </template>
      </el-card>

      <el-empty v-else description="输入问题开始查询" :image-size="100" class="nlq-page__empty" />
    </div>

    <!-- 历史侧栏 -->
    <div class="nlq-page__history">
      <div class="nlq-page__history-title">
        <span>查询历史</span>
        <el-button link type="primary" size="small" @click="loadHistory">刷新</el-button>
      </div>
      <el-empty v-if="history.length === 0" description="暂无记录" :image-size="60" />
      <el-timeline v-else class="nlq-page__timeline">
        <el-timeline-item
          v-for="item in history"
          :key="item.id"
          :type="item.success === 1 ? 'primary' : 'danger'"
          :timestamp="item.createdAt"
        >
          <div class="nlq-page__history-item">
            <div class="nlq-page__history-question" @click="question = item.question">{{ item.question }}</div>
            <div class="nlq-page__history-actions">
              <span class="nlq-page__history-summary">{{ item.resultSummary }}</span>
              <el-button link type="danger" size="small" @click="handleDeleteHistory(item.id)">删除</el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { apiDeleteNlqHistory, apiGetNlqHistory, apiNlqQuery } from '@/api/nlq';
import { ERROR_CODE, NLQ_EXAMPLES } from '@/constants';
import type { INlqResult, IQueryHistory } from '@/types/api';
import type { IBizError } from '@/utils/request';

const inputRef = ref<{ focus: () => void } | null>(null);
const question = ref('');
const querying = ref(false);
const result = ref<INlqResult | null>(null);
const history = ref<IQueryHistory[]>([]);

/** 后端 rows 是二维数组，转成 el-table 需要的对象行 */
const tableRows = computed(() => {
  const rows = result.value?.rows ?? [];
  return rows.map((row) => {
    const rowObject: Record<string, unknown> = {};
    row.forEach((cell, index) => {
      rowObject[`c${index}`] = cell;
    });
    return rowObject;
  });
});

async function handleQuery(): Promise<void> {
  if (!question.value.trim() || querying.value) return;
  querying.value = true;
  result.value = null;
  try {
    result.value = await apiNlqQuery(question.value.trim());
    await loadHistory();
  } catch (error) {
    const bizError = error as IBizError;
    if (bizError.code === ERROR_CODE.NLQ_UNSAFE) {
      ElMessage.warning('该问题涉及写操作或危险语句，仅支持查询');
    } else if (bizError.code === ERROR_CODE.NLQ_MODEL_TIMEOUT) {
      ElMessage.warning('AI 服务暂时不可用，请稍后重试');
    } else if (bizError.code === 429) {
      ElMessage.warning('查询过于频繁（每分钟最多 10 次），请稍后再试');
    }
    // 44002（权限注入失败）等其余错误已由拦截器提示
  } finally {
    querying.value = false;
  }
}

async function loadHistory(): Promise<void> {
  const pageResult = await apiGetNlqHistory({ page: 1, size: 20 });
  history.value = pageResult.records;
}

async function handleDeleteHistory(id: number): Promise<void> {
  await ElMessageBox.confirm('确定删除这条查询历史吗？', '提示', { type: 'warning' });
  await apiDeleteNlqHistory(id);
  ElMessage.success('已删除');
  await loadHistory();
}

onMounted(() => {
  void loadHistory().catch(() => undefined);
  inputRef.value?.focus();
});
</script>

<style scoped lang="scss">
.nlq-page {
  display: flex;
  gap: 0;
  height: 100%;

  &__main {
    flex: 1;
    min-width: 0;
    padding: var(--content-pad);
    overflow-y: auto;
  }

  &__history {
    width: 304px;
    flex-shrink: 0;
    padding: var(--content-pad) var(--space-6);
    background: var(--bg-elevated);
    border-left: 1px solid var(--border-subtle);
    overflow-y: auto;
  }

  &__head {
    margin-bottom: var(--space-4);
  }

  &__title {
    font-size: 20px;
    font-weight: 600;
    color: var(--text-primary);
    line-height: 1.35;
  }

  &__desc {
    margin-top: var(--space-1);
    font-size: 13px;
    color: var(--text-tertiary);
  }

  &__query-card {
    border: 1px solid var(--border-subtle);
  }

  &__query-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
    margin-top: var(--space-3);
    flex-wrap: wrap;
  }

  &__examples {
    display: flex;
    gap: var(--space-2);
    flex-wrap: wrap;
  }

  &__example-tag {
    cursor: pointer;

    &:hover {
      color: var(--brand-700);
      border-color: var(--brand-600);
    }
  }

  &__result-card {
    margin-top: var(--space-4);
    border: 1px solid var(--border-subtle);
  }

  &__result-meta {
    display: flex;
    gap: var(--space-2);
    margin-bottom: var(--space-3);
  }

  &__sql-collapse {
    margin-bottom: var(--space-4);
    border: none;
  }

  &__sql-title {
    font-size: 13px;
    color: var(--text-secondary);
  }

  &__result-table {
    width: 100%;
  }

  &__suggestions {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-top: var(--space-4);
    flex-wrap: wrap;
  }

  &__suggestions-label {
    font-size: 13px;
    color: var(--text-tertiary);
  }

  &__empty {
    margin-top: var(--space-16);
  }

  &__history-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-4);
    font-size: 16px;
    font-weight: 500;
  }

  &__timeline {
    padding-left: var(--space-1);
  }

  &__history-item {
    cursor: default;
  }

  &__history-question {
    font-size: 14px;
    color: var(--text-primary);
    cursor: pointer;

    &:hover {
      color: var(--text-link);
    }
  }

  &__history-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: var(--space-1);
  }

  &__history-summary {
    font-size: 12px;
    color: var(--text-quaternary);
  }
}
</style>
