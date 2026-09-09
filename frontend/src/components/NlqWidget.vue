<template>
  <div class="nlq-widget">
    <!-- 迷你面板：v-show 折叠不销毁，输入与结果自然保留 -->
    <transition name="nlq-widget-pop">
      <div
        v-show="open"
        class="nlq-widget__panel"
        :class="{ 'nlq-widget__panel--free': !!fabPos }"
        :style="panelStyle"
      >
        <div class="nlq-widget__panel-head">
          <span class="nlq-widget__panel-title">智能查询</span>
          <span class="nlq-widget__panel-sub">自然语言查数据 · 自动注入数据权限</span>
          <el-button text :icon="Close" aria-label="收起" class="nlq-widget__panel-close" @click="open = false" />
        </div>

        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="例如：本学期我开设的实验项目有多少学时？"
          :disabled="querying"
          @keydown.ctrl.enter="handleQuery"
          @keydown.meta.enter="handleQuery"
        />

        <!-- 示例标签区在上、查询键独立一行居右：避免长标签把按钮挤出面板（横向溢出 Bug 根治） -->
        <div class="nlq-widget__actions">
          <div class="nlq-widget__examples">
            <el-tag
              v-for="example in examples"
              :key="example"
              class="nlq-widget__example-tag"
              effect="plain"
              @click="question = example"
            >
              {{ example }}
            </el-tag>
          </div>
          <div class="nlq-widget__actions-row">
            <el-button type="primary" size="small" :loading="querying" @click="handleQuery">查询</el-button>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="querying" class="nlq-widget__state nlq-widget__state--loading">
          <span class="nlq-widget__dot" /><span class="nlq-widget__dot" /><span class="nlq-widget__dot" />
          <span class="nlq-widget__state-text">AI 正在解析问题，约需数秒…</span>
        </div>

        <!-- 失败 / 无法执行 -->
        <div v-else-if="errorText" class="nlq-widget__state nlq-widget__state--error">
          <el-icon><WarningFilled /></el-icon>
          <span class="nlq-widget__state-text">{{ errorText }}</span>
        </div>

        <!-- 成功结果 -->
        <template v-else-if="result">
          <el-alert
            v-if="!result.success"
            type="warning"
            :closable="false"
            show-icon
            :title="result.error ?? '该问题无法安全执行'"
            class="nlq-widget__alert"
          />
          <template v-else>
            <div class="nlq-widget__meta">
              <el-tag effect="light" size="small">返回 {{ result.rowCount ?? previewRows.length }} 条</el-tag>
              <el-tag v-if="result.executionTime" type="info" effect="light" size="small">
                耗时 {{ (result.executionTime / 1000).toFixed(1) }}s
              </el-tag>
            </div>
            <el-table :data="previewRows" border stripe size="small" max-height="200" class="nlq-widget__table">
              <el-table-column
                v-for="(col, index) in result.columns ?? []"
                :key="String(col)"
                :prop="`c${index}`"
                :label="String(col)"
                min-width="110"
                show-overflow-tooltip
              >
                <template #default="{ row }">{{ row[index] }}</template>
              </el-table-column>
            </el-table>
            <div class="nlq-widget__panel-foot">
              <el-link type="primary" :underline="false" @click="goFullPage">
                查看完整结果<el-icon class="nlq-widget__foot-icon"><ArrowRight /></el-icon>
              </el-link>
            </div>
          </template>
        </template>
      </div>
    </transition>

    <!-- 悬浮球：可拖拽 + 贴边吸附 + 位置记忆 -->
    <button
      class="nlq-widget__fab"
      :class="{
        'nlq-widget__fab--active': open,
        'nlq-widget__fab--dragging': dragging,
        'nlq-widget__fab--free': !!fabPos,
      }"
      :style="fabStyle"
      type="button"
      aria-label="智能查询"
      @pointerdown="onFabPointerDown"
      @pointermove="onFabPointerMove"
      @pointerup="onFabPointerUp"
      @pointercancel="onFabPointerUp"
    >
      <svg viewBox="0 0 48 48" class="nlq-widget__bot" aria-hidden="true">
        <defs>
          <linearGradient id="nlq-bot-body" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#ffffff" />
            <stop offset="70%" stop-color="#f1f6fd" />
            <stop offset="100%" stop-color="#d5e5f7" />
          </linearGradient>
          <linearGradient id="nlq-bot-face" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#2a6fb9" />
            <stop offset="100%" stop-color="#1a4f8f" />
          </linearGradient>
        </defs>
        <!-- 天线（闲置摇摆） -->
        <g class="nlq-widget__antenna">
          <line x1="24" y1="10.5" x2="24" y2="4.6" stroke="#fff" stroke-width="2.6" stroke-linecap="round" />
          <circle cx="24" cy="3.6" r="2.7" fill="#fff" />
        </g>
        <!-- 侧耳 -->
        <rect x="3.2" y="17.6" width="5.2" height="10" rx="2.6" fill="#e6effb" />
        <rect x="39.6" y="17.6" width="5.2" height="10" rx="2.6" fill="#e6effb" />
        <!-- 搪胶身体：顶部高光 + 渐变主体 + 底部暗面 -->
        <rect x="7.5" y="9.5" width="33" height="27" rx="12.5" fill="url(#nlq-bot-body)" />
        <ellipse cx="19" cy="14.2" rx="10.5" ry="3.4" fill="#fff" opacity="0.9" />
        <rect x="7.5" y="27.5" width="33" height="9" rx="4.5" fill="#bcd4ef" opacity="0.45" />
        <!-- 面屏 + 发光眼睛（闲置眨眼，hover 变亮） -->
        <rect x="12" y="14.8" width="24" height="14.6" rx="7.3" fill="url(#nlq-bot-face)" />
        <ellipse class="nlq-widget__eye" cx="18.6" cy="21.8" r="3.1" fill="#8fd8ff" />
        <ellipse class="nlq-widget__eye" cx="29.4" cy="21.8" r="3.1" fill="#8fd8ff" />
        <rect x="20" y="26.4" width="8" height="1.8" rx="0.9" fill="#7fb4e6" opacity="0.85" />
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowRight, Close, WarningFilled } from '@element-plus/icons-vue';
import { apiNlqQuery } from '@/api/nlq';
import { ERROR_CODE, NLQ_EXAMPLES } from '@/constants';
import type { INlqResult } from '@/types/api';
import type { IBizError } from '@/utils/request';

/**
 * 全局悬浮智能查询机器人：右下角悬浮球 + 迷你面板，
 * 直连 /nlq/query；收起只折叠不销毁（输入/结果保留）。
 * 交互：全视口拖拽、松手贴左右边缘吸附、位置 localStorage 记忆，
 * 面板弹出方向（水平左/右 + 垂直上/下）自动跟随机器人位置。
 * 降级策略：不做服务在线探测，失败即复用错误码文案提示。
 */
const router = useRouter();

const open = ref(false);
const question = ref('');
const querying = ref(false);
const result = ref<INlqResult | null>(null);
const errorText = ref('');

const examples = NLQ_EXAMPLES.slice(0, 3);

/** 后端 rows 是二维数组，转成 el-table 需要的对象行（与 NlqPage 同构） */
const previewRows = computed<Record<string, unknown>[]>(() => {
  const rows = (result.value?.rows ?? []).slice(0, 5);
  return rows.map((row) => {
    const rowObject: Record<string, unknown> = {};
    row.forEach((cell, index) => {
      rowObject[`c${index}`] = cell;
    });
    return rowObject;
  });
});

async function handleQuery(): Promise<void> {
  const q = question.value.trim();
  if (!q || querying.value) return;
  querying.value = true;
  result.value = null;
  errorText.value = '';
  try {
    result.value = await apiNlqQuery(q);
  } catch (err) {
    const bizError = err as IBizError;
    if (bizError.code === ERROR_CODE.NLQ_UNSAFE) {
      errorText.value = '该问题涉及写操作或危险语句，仅支持查询';
    } else if (bizError.code === ERROR_CODE.NLQ_MODEL_TIMEOUT) {
      errorText.value = 'AI 服务暂时不可用，请稍后重试';
    } else if (bizError.code === 429) {
      errorText.value = '查询过于频繁（每分钟最多 10 次），请稍后再试';
    } else {
      errorText.value = bizError.message || '查询失败，请稍后重试';
    }
  } finally {
    querying.value = false;
  }
}

function goFullPage(): void {
  router.push('/nlq');
}

// ---------- 拖拽 + 贴边吸附 + 位置记忆 ----------
const FAB_SIZE = 52;
const EDGE_GAP = 10;
const POS_KEY = 'ltpms-nlq-fab-pos';
const DRAG_THRESHOLD = 5;

const fabPos = ref<{ x: number; y: number } | null>(null);
const dragging = ref(false);
let pressed = false;
let moved = false;
let dragStart = { px: 0, py: 0, x: 0, y: 0 };

function clampPos(pos: { x: number; y: number }): { x: number; y: number } {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  return {
    x: Math.min(Math.max(pos.x, EDGE_GAP), vw - FAB_SIZE - EDGE_GAP),
    y: Math.min(Math.max(pos.y, EDGE_GAP), vh - FAB_SIZE - EDGE_GAP),
  };
}

const fabStyle = computed(() => {
  if (!fabPos.value) return undefined;
  return { left: `${fabPos.value.x}px`, top: `${fabPos.value.y}px` };
});

/** 面板水平方向：机器人在左半屏→面板往右弹，反之往左弹 */
const panelSide = computed<'left' | 'right'>(() => {
  if (!fabPos.value) return 'right';
  return fabPos.value.x + FAB_SIZE / 2 < window.innerWidth / 2 ? 'left' : 'right';
});

/** 面板垂直方向：机器人偏上→面板往下弹，避免向上溢出视口 */
const panelVertical = computed<'above' | 'below'>(() => {
  if (!fabPos.value) return 'above';
  return fabPos.value.y < window.innerHeight * 0.45 ? 'below' : 'above';
});

/** 拖拽后（自由模式）面板改为 fixed 并按球坐标锚定，四向避让视口边缘 */
const panelStyle = computed<Record<string, string> | undefined>(() => {
  if (!fabPos.value) return undefined;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const { x, y } = fabPos.value;
  const style: Record<string, string> = { position: 'fixed' };
  if (panelSide.value === 'left') {
    style.left = `${x + FAB_SIZE + 12}px`;
  } else {
    style.right = `${vw - x + 12}px`;
  }
  if (panelVertical.value === 'below') {
    style.top = `${y + FAB_SIZE + 12}px`;
  } else {
    style.bottom = `${vh - y + 12}px`;
  }
  return style;
});

function onFabPointerDown(e: PointerEvent): void {
  if (e.button !== 0) return;
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  fabPos.value = fabPos.value ?? { x: rect.left, y: rect.top };
  dragStart = { px: e.clientX, py: e.clientY, x: fabPos.value.x, y: fabPos.value.y };
  pressed = true;
  moved = false;
}

function onFabPointerMove(e: PointerEvent): void {
  if (!pressed || !fabPos.value) return;
  const dx = e.clientX - dragStart.px;
  const dy = e.clientY - dragStart.py;
  if (!moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
  moved = true;
  dragging.value = true;
  fabPos.value = clampPos({ x: dragStart.x + dx, y: dragStart.y + dy });
}

function onFabPointerUp(e: PointerEvent): void {
  if (!pressed) return;
  pressed = false;
  dragging.value = false;
  (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId);
  if (!moved) {
    // 未产生位移视为点击：开合面板
    open.value = !open.value;
    return;
  }
  // 贴边吸附：吸到更近的左右边缘，垂直位置保留
  if (fabPos.value) {
    const vw = window.innerWidth;
    const targetX =
      fabPos.value.x + FAB_SIZE / 2 < vw / 2 ? EDGE_GAP : vw - FAB_SIZE - EDGE_GAP;
    fabPos.value = clampPos({ x: targetX, y: fabPos.value.y });
    try {
      localStorage.setItem(POS_KEY, JSON.stringify(fabPos.value));
    } catch {
      /* 存储不可用时静默降级为不记忆 */
    }
  }
}

function restorePos(): void {
  try {
    const raw = localStorage.getItem(POS_KEY);
    if (raw) {
      const saved = JSON.parse(raw) as { x: number; y: number };
      if (Number.isFinite(saved?.x) && Number.isFinite(saved?.y)) {
        fabPos.value = clampPos(saved);
      }
    }
  } catch {
    /* 解析失败按默认位置处理 */
  }
}

function onResize(): void {
  if (fabPos.value) fabPos.value = clampPos(fabPos.value);
}

onMounted(() => {
  restorePos();
  window.addEventListener('resize', onResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
});
</script>

<style scoped lang="scss">
.nlq-widget {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000;

  // 拖拽后悬浮球脱离右下角锚点，改为视口固定定位（left/top 由内联样式给出）
  &__panel {
    position: absolute;
    right: 0;
    bottom: 64px;
    display: flex;
    flex-direction: column;
    gap: var(--space-3, 10px);
    width: 380px;
    max-height: min(62vh, 560px);
    padding: var(--space-4, 14px);
    overflow-y: auto;
    background: var(--el-bg-color, #fff);
    border: 1px solid var(--border-subtle, #e4e7ed);
    border-radius: 14px;
    box-shadow: 0 12px 32px rgb(0 0 0 / 12%);

    &--free {
      max-height: min(70vh, 560px);
    }
  }

  &__panel-head {
    display: flex;
    align-items: baseline;
    gap: var(--space-2, 8px);
  }

  &__panel-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  &__panel-sub {
    flex: 1;
    overflow: hidden;
    font-size: 12px;
    color: var(--el-text-color-secondary, #909399);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__panel-close {
    flex-shrink: 0;
    height: 22px;
  }

  &__panel-foot {
    display: flex;
    justify-content: flex-end;
  }

  &__foot-icon {
    margin-left: 2px;
  }

  // 纵向布局：示例标签在上（自动换行），查询键独立一行居右，杜绝横向溢出
  &__actions {
    display: flex;
    flex-direction: column;
    gap: var(--space-2, 8px);
    min-width: 0;
  }

  &__examples {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-width: 0;
  }

  &__example-tag {
    max-width: 100%;
    overflow: hidden;
    cursor: pointer;
    text-overflow: ellipsis;
    white-space: nowrap;

    &:hover {
      color: var(--el-color-primary, #378add);
      border-color: var(--el-color-primary-light-5, #94c2ee);
    }
  }

  &__actions-row {
    display: flex;
    justify-content: flex-end;
  }

  &__state {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: var(--space-3, 10px) var(--space-4, 14px);
    border-radius: 8px;

    &--loading {
      justify-content: center;
      color: var(--el-color-primary, #378add);
      background: var(--el-color-primary-light-9, #ecf5fe);
    }

    &--error {
      color: var(--el-color-danger, #f56c6c);
      background: var(--el-color-danger-light-9, #fef0f0);
    }
  }

  &__state-text {
    font-size: 13px;
  }

  &__dot {
    width: 5px;
    height: 5px;
    margin-right: 2px;
    animation: nlq-widget-blink 1.2s infinite;
    background: currentColor;
    border-radius: 50%;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }

  &__meta {
    display: flex;
    gap: 6px;
  }

  &__table {
    width: 100%;
  }

  &__fab {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 52px;
    height: 52px;
    padding: 0;
    color: #fff;
    cursor: pointer;
    touch-action: none;
    user-select: none;
    background:
      radial-gradient(circle at 30% 22%, rgb(255 255 255 / 40%) 0%, rgb(255 255 255 / 0%) 42%),
      linear-gradient(160deg, #55a0ec 0%, #378add 52%, #2568b4 100%);
    border: 1px solid #185fa5;
    border-radius: 50%;
    box-shadow:
      inset 0 -5px 0 rgb(9 51 94 / 28%),
      inset 0 3px 4px rgb(255 255 255 / 30%),
      0 4px 0 #1b5f9e,
      0 9px 18px rgb(55 138 221 / 42%);
    transition:
      box-shadow 0.25s,
      filter 0.25s;

    &:hover {
      filter: brightness(1.05);
      box-shadow:
        inset 0 -5px 0 rgb(9 51 94 / 28%),
        inset 0 3px 4px rgb(255 255 255 / 30%),
        0 6px 0 #1b5f9e,
        0 14px 26px rgb(55 138 221 / 50%);

      .nlq-widget__eye {
        fill: #c2ecff;
        filter: drop-shadow(0 0 3px rgb(143 216 255 / 90%));
      }
    }

    &--dragging {
      cursor: grabbing;
      filter: brightness(1.08);

      .nlq-widget__bot-wrap,
      .nlq-widget__bot {
        animation: none;
      }
    }

    // 开合弹跳（class 挂上时播放一次）
    &--active {
      animation: nlq-widget-bounce 0.38s cubic-bezier(0.34, 1.56, 0.64, 1);
    }

    // 自由模式（拖拽过）：脱离容器改为视口定位
    &--free {
      position: fixed;
      z-index: 2000;
    }
  }

  &__bot {
    width: 32px;
    height: 32px;
    // 闲置悬浮（整体轻微上下漂浮）
    animation: nlq-widget-float 3.2s ease-in-out infinite;
  }

  &__antenna {
    transform-origin: 24px 10px;
    animation: nlq-widget-sway 3.2s ease-in-out infinite;
  }

  &__eye {
    transform-box: fill-box;
    transform-origin: center;
    animation: nlq-widget-eye-blink 4.4s infinite;
    transition: fill 0.25s, filter 0.25s;
  }
}

.nlq-widget-pop-enter-active,
.nlq-widget-pop-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.nlq-widget-pop-enter-from,
.nlq-widget-pop-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.96);
}

@keyframes nlq-widget-blink {
  0%,
  100% {
    opacity: 0.25;
  }

  50% {
    opacity: 1;
  }
}

@keyframes nlq-widget-float {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-3px);
  }
}

@keyframes nlq-widget-sway {
  0%,
  100% {
    transform: rotate(-7deg);
  }

  50% {
    transform: rotate(7deg);
  }
}

@keyframes nlq-widget-eye-blink {
  0%,
  91%,
  100% {
    transform: scaleY(1);
  }

  94% {
    transform: scaleY(0.08);
  }

  97% {
    transform: scaleY(1);
  }
}

@keyframes nlq-widget-bounce {
  0% {
    transform: scale(1);
  }

  40% {
    transform: scale(0.9);
  }

  70% {
    transform: scale(1.06);
  }

  100% {
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .nlq-widget__bot,
  .nlq-widget__antenna,
  .nlq-widget__eye,
  .nlq-widget__dot,
  .nlq-widget__fab--active,
  .nlq-widget-pop-enter-active,
  .nlq-widget-pop-leave-active {
    transition: none;
    animation: none;
  }
}
</style>
