<template>
  <div class="nlq-page">
    <!-- 主内容区 -->
    <div class="nlq-page__main">
      <!-- 查询台（空态居中 hero；有结果后沉到顶部） -->
      <div class="asy-terminal" :class="{ 'asy-terminal--active': result || querying }">
        <!-- 终端机头 -->
        <div class="asy-terminal__head">
          <span class="asy-terminal__lamp" aria-hidden="true"></span>
          <span class="asy-terminal__code">LTPMS · ASY-01 智能查询终端</span>
          <span class="asy-terminal__scope" :title="isAdmin ? '管理员：可查询全校数据' : '教师：自动注入权限，仅可见本人任教数据'">
            {{ isAdmin ? 'SCOPE:ALL' : 'SCOPE:SELF' }}
          </span>
        </div>

        <!-- 问候语 -->
        <div class="asy-terminal__greeting">{{ greeting }}，我是阿实</div>
        <div class="asy-terminal__sub">实验室数据随口问 · 系统自动注入数据权限（NL2SQL，只读）</div>

        <!-- Composer：查询按钮内嵌 -->
        <div class="asy-composer" :class="{ 'asy-composer--busy': querying }">
          <span class="asy-composer__prompt" aria-hidden="true">❯</span>
          <el-input
            ref="inputRef"
            v-model="question"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入问题，例如：这学期我开设了哪些实验课程？"
            class="asy-composer__input"
            @keydown.ctrl.enter="handleQuery"
            @keydown.meta.enter="handleQuery"
          />
          <div class="asy-composer__bar">
            <span class="asy-composer__hint">CTRL+⏎ 执行 · 只读查询 · 强制 LIMIT</span>
            <el-button
              class="asy-composer__exec"
              :class="{ 'asy-composer__exec--ready': question.trim() }"
              :loading="querying"
              @click="handleQuery"
            >
              {{ querying ? '检索中' : '查询 ⏎' }}
            </el-button>
          </div>
          <span class="asy-composer__scan" aria-hidden="true"></span>
        </div>

        <!-- 提示词：垂直渐虚堆叠（上清晰 → 下模糊，hover 恢复） -->
        <div class="asy-examples">
          <button
            v-for="(example, index) in NLQ_EXAMPLES"
            :key="example"
            type="button"
            class="asy-examples__card"
            :style="{ '--stagger': index }"
            @click="question = example"
          >
            <span class="asy-examples__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="asy-examples__text">{{ example }}</span>
            <span class="asy-examples__load">载入 ↵</span>
          </button>
        </div>

        <!-- 空态引导行（无结果时显示） -->
        <div v-if="!result && !querying" class="asy-boot">
          <span class="asy-boot__chevron">❯</span>
          <span class="asy-boot__text">等待指令</span>
          <span class="asy-boot__caret" aria-hidden="true"></span>
        </div>
      </div>

      <!-- 结果区（逻辑与数据结构保持不变，仅外壳风格化） -->
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
            <span class="nlq-page__meta-dot" aria-hidden="true"></span>
            <span class="nlq-page__meta-text">返回 {{ result.row_count ?? result.rowCount ?? 0 }} 条记录</span>
            <span v-if="(result.execution_time ?? result.executionTime) != null" class="nlq-page__meta-text nlq-page__meta-text--dim">
              耗时 {{ ((result.execution_time ?? result.executionTime ?? 0) / 1000).toFixed(1) }}s
            </span>
            <span class="nlq-page__meta-scope">{{ isAdmin ? 'SCOPE:ALL' : 'SCOPE:SELF' }}</span>
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
    </div>

    <!-- 历史侧栏（结构保持原样） -->
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
import { useUserStore } from '@/store/user';
import type { INlqResult, IQueryHistory } from '@/types/api';
import type { IBizError } from '@/utils/request';

const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

const inputRef = ref<{ focus: () => void } | null>(null);
const question = ref('');
const querying = ref(false);
const result = ref<INlqResult | null>(null);
const history = ref<IQueryHistory[]>([]);

/** 时间感知问候（Claude 式 temporal greeting，让终端有人味） */
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

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
/**
 * ASY-01 检索终端 —— 与阿实悬浮面板同源的磷光绿终端语言。
 * 磷光绿色板为页面局部变量（先例：NlqWidget 悬浮面板，tokens.css 未收录）；
 * 其余间距/圆角/阴影/文字色一律引用 design tokens。
 */
$phosphor-bg: #0c1f16;      // 终端底（同 NlqWidget 步骤条）
$phosphor-border: #1e3a2b;  // 终端描边
$phosphor-text: #6ee7a0;    // 磷光绿主文字
$phosphor-dim: #3f8f66;     // 暗磷光（次要文字）
$phosphor-bright: #a7f3c9;  // 高亮磷光（hover）

.nlq-page {
  display: flex;
  gap: 0;
  height: 100%;

  &__main {
    flex: 1;
    min-width: 0;
    padding: var(--content-pad);
    overflow-y: auto;
    display: flex;
    flex-direction: column;
  }

  /* ===== 查询台容器：空态垂直居中，有结果后沉顶 ===== */
  .asy-terminal {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
    max-width: 720px;
    margin: 0 auto;
    padding-top: var(--space-16);

    /* 有结果/查询中：收缩为顶部构图，让位给结果区 */
    &--active {
      padding-top: var(--space-6);
    }
  }

  /* ===== 终端机头 ===== */
  .asy-terminal__head {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-8);
    font-family: var(--font-mono);
    font-size: 12px;
    letter-spacing: 0.08em;
    color: var(--text-tertiary);
  }

  .asy-terminal__lamp {
    width: 8px;
    height: 8px;
    border-radius: var(--radius-full);
    background: $phosphor-text;
    box-shadow: 0 0 8px rgba(110, 231, 160, 0.8);
    animation: asy-breathe 2.4s var(--ease-in-out) infinite;
  }

  .asy-terminal__scope {
    margin-left: var(--space-2);
    padding: 2px 10px;
    border: 1px solid var(--border-default);
    border-radius: var(--radius-full);
    font-size: 11px;
    color: var(--text-secondary);
    background: var(--bg-elevated);
    cursor: help;
  }

  /* ===== 问候 ===== */
  .asy-terminal__greeting {
    font-size: 30px;
    font-weight: 600;
    color: var(--text-primary);
    line-height: 1.3;
    letter-spacing: 0.01em;
  }

  .asy-terminal__sub {
    margin-top: var(--space-2);
    margin-bottom: var(--space-5);
    font-size: 13px;
    color: var(--text-tertiary);
  }

  /* ===== Composer：深色终端块（同 SQL 代码块的深底先例） ===== */
  .asy-composer {
    position: relative;
    width: 100%;
    background: $phosphor-bg;
    border: 1px solid $phosphor-border;
    border-radius: var(--radius-2xl);
    box-shadow: var(--shadow-lg), inset 0 0 32px rgba(110, 231, 160, 0.05);
    padding: var(--space-4) var(--space-4) var(--space-3);
    overflow: hidden;
    transition: border-color var(--duration-normal) var(--ease-out), box-shadow var(--duration-normal) var(--ease-out);

    &:focus-within {
      border-color: $phosphor-dim;
      box-shadow: var(--shadow-lg), 0 0 0 3px rgba(110, 231, 160, 0.12), inset 0 0 32px rgba(110, 231, 160, 0.07);
    }

    /* 查询中：磷光扫描线掠过 */
    &--busy .asy-composer__scan {
      opacity: 1;
      animation: asy-scan 1.6s linear infinite;
    }
  }

  /* 扫描线（默认隐藏） */
  .asy-composer__scan {
    position: absolute;
    inset: 0;
    pointer-events: none;
    opacity: 0;
    background: linear-gradient(
      to bottom,
      transparent 0%,
      rgba(110, 231, 160, 0.06) 46%,
      rgba(110, 231, 160, 0.14) 50%,
      rgba(110, 231, 160, 0.06) 54%,
      transparent 100%
    );
    background-size: 100% 240%;
  }

  /* mono 提示符 */
  .asy-composer__prompt {
    position: absolute;
    top: 22px;
    left: 18px;
    font-family: var(--font-mono);
    font-size: 14px;
    color: $phosphor-dim;
    user-select: none;
  }

  .asy-composer__input {
    padding-left: var(--space-6);

    :deep(.el-textarea__inner) {
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      font-family: var(--font-mono);
      font-size: 14px;
      line-height: 1.7;
      color: $phosphor-text;
      caret-color: $phosphor-bright;

      &::placeholder {
        color: rgba(110, 231, 160, 0.38);
      }
    }
  }

  .asy-composer__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: var(--space-3);
  }

  .asy-composer__hint {
    font-family: var(--font-mono);
    font-size: 11px;
    letter-spacing: 0.06em;
    color: rgba(110, 231, 160, 0.4);
    user-select: none;
  }

  /* 内嵌执行按钮：磷光绿实底，有问题可发时点亮 */
  .asy-composer__exec {
    border: none;
    border-radius: var(--radius-lg);
    background: rgba(110, 231, 160, 0.14);
    color: $phosphor-dim;
    font-family: var(--font-mono);
    font-weight: 600;
    transition: all var(--duration-normal) var(--ease-out);

    &--ready {
      background: $phosphor-text;
      color: $phosphor-bg;

      &:hover {
        background: $phosphor-bright;
        color: $phosphor-bg;
      }
    }

    &:hover:not(&--ready):not(.is-loading) {
      background: rgba(110, 231, 160, 0.22);
      color: $phosphor-text;
    }

    &.is-loading {
      background: rgba(110, 231, 160, 0.14);
      color: $phosphor-text;
    }
  }

  /* ===== 提示词：垂直渐虚堆叠（上清晰 → 下模糊） ===== */
  .asy-examples {
    width: 100%;
    margin-top: var(--space-8);
    display: flex;
    flex-direction: column;
  }

  .asy-examples__card {
    position: relative;
    display: flex;
    align-items: center;
    gap: var(--space-3);
    width: 100%;
    padding: var(--space-4) var(--space-5);
    text-align: left;
    background: var(--bg-elevated);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-xl);
    box-shadow: var(--shadow-sm);
    cursor: pointer;
    transition:
      filter var(--duration-normal) var(--ease-out),
      opacity var(--duration-normal) var(--ease-out),
      transform var(--duration-normal) var(--ease-out),
      border-color var(--duration-normal) var(--ease-out),
      box-shadow var(--duration-normal) var(--ease-out);
    animation: asy-rise 0.5s var(--ease-out) both;
    animation-delay: calc(var(--stagger) * 90ms);

    /* 渐虚堆叠：负边距叠压 + blur/opacity 逐级递增 */
    & + & {
      margin-top: -10px;
      padding-top: calc(var(--space-4) + 10px);
    }

    &:nth-child(1) {
      filter: blur(0);
      opacity: 1;
      z-index: 3;
    }

    &:nth-child(2) {
      filter: blur(1.2px);
      opacity: 0.6;
      z-index: 2;
    }

    &:nth-child(3) {
      filter: blur(2.4px);
      opacity: 0.32;
      z-index: 1;
    }

    &:hover {
      filter: blur(0);
      opacity: 1;
      transform: translateY(-2px);
      border-color: var(--brand-500);
      box-shadow: var(--shadow-md);
      z-index: 5;
    }
  }

  .asy-examples__index {
    flex-shrink: 0;
    font-family: var(--font-mono);
    font-size: 12px;
    color: var(--text-quaternary);
  }

  .asy-examples__text {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .asy-examples__load {
    flex-shrink: 0;
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--text-quaternary);
    opacity: 0;
    transition: opacity var(--duration-fast) var(--ease-out);
  }

  .asy-examples__card:hover .asy-examples__load {
    opacity: 1;
    color: var(--brand-600);
  }

  /* ===== boot 空态行：闪烁光标 ===== */
  .asy-boot {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-top: var(--space-10);
    font-family: var(--font-mono);
    font-size: 13px;
    color: var(--text-tertiary);
  }

  .asy-boot__chevron {
    color: $phosphor-dim;
  }

  .asy-boot__caret {
    width: 8px;
    height: 15px;
    background: $phosphor-dim;
    animation: asy-caret 1.1s steps(1) infinite;
  }

  /* ===== 结果区 ===== */
  &__result-card {
    width: 100%;
    max-width: 980px;
    margin: var(--space-6) auto 0;
    border: 1px solid var(--border-subtle);
    animation: asy-rise 0.4s var(--ease-out) both;
  }

  &__result-meta {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    margin-bottom: var(--space-3);
    font-family: var(--font-mono);
    font-size: 12px;
  }

  &__meta-dot {
    width: 7px;
    height: 7px;
    border-radius: var(--radius-full);
    background: var(--success-500);
    box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
  }

  &__meta-text {
    color: var(--text-secondary);

    &--dim {
      color: var(--text-quaternary);
    }
  }

  &__meta-scope {
    margin-left: auto;
    padding: 2px 10px;
    border: 1px solid var(--border-default);
    border-radius: var(--radius-full);
    font-size: 11px;
    color: var(--text-tertiary);
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

  &__example-tag {
    cursor: pointer;

    &:hover {
      color: var(--brand-700);
      border-color: var(--brand-600);
    }
  }

  /* ===== 历史侧栏（样式保持原样） ===== */
  &__history {
    width: 304px;
    flex-shrink: 0;
    padding: var(--content-pad) var(--space-6);
    background: var(--bg-elevated);
    border-left: 1px solid var(--border-subtle);
    overflow-y: auto;
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

/* ===== 动效 ===== */
@keyframes asy-breathe {
  0%, 100% { opacity: 1; box-shadow: 0 0 8px rgba(110, 231, 160, 0.8); }
  50% { opacity: 0.55; box-shadow: 0 0 3px rgba(110, 231, 160, 0.4); }
}

@keyframes asy-scan {
  0% { background-position: 0 120%; }
  100% { background-position: 0 -120%; }
}

@keyframes asy-caret {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0; }
}

@keyframes asy-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
