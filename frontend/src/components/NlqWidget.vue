<template>
  <div class="nlq-widget">
    <!-- 迷你面板：v-show 折叠不销毁，对话历史自然保留 -->
    <transition name="nlq-widget-pop">
      <div
        v-show="open"
        class="nlq-widget__panel"
        :class="{ 'nlq-widget__panel--free': !!fabPos }"
        :style="panelStyle"
      >
        <div class="nlq-widget__panel-head">
          <span class="nlq-widget__panel-title">阿实</span>
          <span class="nlq-widget__panel-sub">复古 × AI 实验助教 · 多轮对话查数据</span>
          <el-button text :icon="Close" aria-label="收起" class="nlq-widget__panel-close" @click="open = false" />
        </div>

        <!-- 对话区（老式终端风） -->
        <div ref="convoRef" class="nlq-widget__convo">
          <!-- 空态欢迎 + 示例标签 -->
          <div v-if="messages.length === 0 && !thinking" class="nlq-widget__welcome">
            <div class="nlq-widget__welcome-line">> 我是阿实，你的实验助教。</div>
            <div class="nlq-widget__welcome-line">> 可以直接问我，比如：</div>
            <el-tag
              v-for="example in examples"
              :key="example"
              class="nlq-widget__example-tag"
              effect="plain"
              @click="draft = example"
            >
              {{ example }}
            </el-tag>
          </div>

          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="nlq-widget__msg"
            :class="[`is-${msg.role}`, { 'has-dossier': dossierSteps(msg.steps).length > 0 }]"
          >
            <div class="nlq-widget__msg-bubble">{{ msg.content }}</div>

            <!-- 人员档案：分区卡原始行直接上屏（决策 D7） -->
            <PersonDossier
              v-if="dossierSteps(msg.steps).length > 0"
              :steps="msg.steps ?? []"
              class="nlq-widget__dossier"
            />

            <!-- 其余工具过程折叠展示 -->
            <div v-if="plainSteps(msg.steps).length > 0" class="nlq-widget__steps">
              <div v-for="(step, si) in plainSteps(msg.steps)" :key="si" class="nlq-widget__step">
                <span class="nlq-widget__step-dot" />
                调用了 <code>{{ step.tool }}</code> 工具
              </div>
            </div>
          </div>

          <div v-if="thinking" class="nlq-widget__msg is-assistant">
            <div class="nlq-widget__msg-bubble nlq-widget__msg-bubble--typing">
              阿实正在思考<span class="nlq-widget__cursor">▌</span>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="nlq-widget__input-row">
          <el-input
            v-model="draft"
            placeholder="问问阿实…（Enter 发送）"
            :disabled="thinking"
            class="nlq-widget__input"
            @keyup.enter="send"
          />
          <el-button
            type="primary"
            size="small"
            :loading="thinking"
            class="nlq-widget__send"
            @click="send"
          >
            发送
          </el-button>
        </div>
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
      aria-label="阿实智能助手"
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { Close } from '@element-plus/icons-vue';
import PersonDossier from '@/components/PersonDossier.vue';
import { apiNlqAgent } from '@/api/nlq';
import { ERROR_CODE, NLQ_EXAMPLES } from '@/constants';
import type { IAgentStep } from '@/types/api';
import type { IBizError } from '@/utils/request';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  steps?: IAgentStep[];
}

/**
 * 全局悬浮「阿实」智能助手：右下角悬浮球 + 多轮对话式复古终端迷你面板。
 * 直连 /nlq/agent；收起只折叠不销毁（对话历史保留）。
 * 交互：全视口拖拽、松手贴左右边缘吸附、位置 localStorage 记忆，
 * 面板弹出方向自动跟随机器人位置。失败复用错误码文案提示。
 */
const open = ref(false);
const draft = ref('');
const thinking = ref(false);
const messages = ref<ChatMessage[]>([]);
const convoRef = ref<HTMLElement | null>(null);

const examples = NLQ_EXAMPLES.slice(0, 3);

async function send(): Promise<void> {
  const q = draft.value.trim();
  if (!q || thinking.value) return;
  draft.value = '';

  // 当前发送前的对话作为多轮历史传给后端（只传 role/content，不传工具轨迹）
  const history = messages.value.map((m) => ({ role: m.role, content: m.content }));
  messages.value.push({ role: 'user', content: q });
  thinking.value = true;
  scrollToBottom();

  try {
    const res = await apiNlqAgent(q, history);
    messages.value.push({
      role: 'assistant',
      content: res.answer || '（未获得回复）',
      steps: res.steps ?? [],
    });
  } catch (err) {
    const bizError = err as IBizError;
    messages.value.push({
      role: 'assistant',
      content: resolveErrorText(bizError),
      steps: [],
    });
  } finally {
    thinking.value = false;
    scrollToBottom();
  }
}

function resolveErrorText(bizError: IBizError): string {
  if (bizError.code === ERROR_CODE.NLQ_SERVICE_UNAVAILABLE) return '智能查询服务未启动或连接失败';
  if (bizError.code === ERROR_CODE.NLQ_MODEL_TIMEOUT) return 'AI 服务暂时不可用，请稍后重试';
  if (bizError.code === 429) return '查询过于频繁（每分钟最多 10 次），请稍后再试';
  return bizError.message || '查询失败，请稍后重试';
}

/** 档案工具带 display.blocks（原始行要上屏）；其余工具只在轨迹里留一行 */
function isDossierStep(step: IAgentStep): boolean {
  const result = step.result as { display?: { blocks?: unknown } } | undefined;
  return Array.isArray(result?.display?.blocks);
}

function dossierSteps(steps?: IAgentStep[]): IAgentStep[] {
  return (steps ?? []).filter(isDossierStep);
}

function plainSteps(steps?: IAgentStep[]): IAgentStep[] {
  return (steps ?? []).filter((step) => !isDossierStep(step));
}

async function scrollToBottom(): Promise<void> {
  await nextTick();
  if (convoRef.value) {
    convoRef.value.scrollTop = convoRef.value.scrollHeight;
  }
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
    overflow: hidden;
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
    flex-shrink: 0;
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

  // 对话区：老式终端风（磷光绿）
  &__convo {
    display: flex;
    flex-direction: column;
    gap: var(--space-2, 8px);
    min-height: 200px;
    max-height: 320px;
    padding: var(--space-3, 10px);
    overflow-y: auto;
    background: #0c1f16;
    border: 1px solid #1e3a2b;
    border-radius: 10px;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background: #2c4a38;
      border-radius: 3px;
    }
  }

  &__welcome {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 4px;
  }

  &__welcome-line {
    font-family: var(--el-font-family-mono, 'Courier New', monospace);
    font-size: 13px;
    color: #6ee7a0;
  }

  &__example-tag {
    align-self: flex-start;
    max-width: 100%;
    overflow: hidden;
    cursor: pointer;
    color: #6ee7a0;
    background: rgb(110 231 160 / 8%);
    border-color: #2c4a38;
    text-overflow: ellipsis;
    white-space: nowrap;

    &:hover {
      color: #a7f3c9;
      border-color: #4ade80;
    }
  }

  &__msg {
    display: flex;
    flex-direction: column;
    max-width: 88%;

    &.is-user {
      align-self: flex-end;
      align-items: flex-end;
    }

    &.is-assistant {
      align-self: flex-start;
      align-items: flex-start;
    }

    /* 带档案卡的消息放开宽度限制，让原始行有横向空间 */
    &.has-dossier {
      width: 100%;
      max-width: 100%;
    }
  }

  &__dossier {
    align-self: stretch;
    margin-top: 6px;
  }

  &__msg-bubble {
    padding: 8px 10px;
    font-size: 13px;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;

    .is-user & {
      color: #0c1f16;
      background: #6ee7a0;
      border-radius: 10px 10px 2px 10px;
    }

    .is-assistant & {
      color: #d1fae5;
      background: #153126;
      border: 1px solid #2c4a38;
      border-radius: 10px 10px 10px 2px;
    }

    &--typing {
      color: #6ee7a0 !important;
    }
  }

  &__cursor {
    display: inline-block;
    animation: nlq-widget-blink 1.1s step-end infinite;
    color: #4ade80;
  }

  &__steps {
    display: flex;
    flex-direction: column;
    gap: 3px;
    margin-top: 4px;
  }

  &__step {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 11px;
    color: #86efac;

    code {
      font-family: var(--el-font-family-mono, 'Courier New', monospace);
      color: #a7f3c9;
    }
  }

  &__step-dot {
    width: 4px;
    height: 4px;
    background: #4ade80;
    border-radius: 50%;
  }

  &__input-row {
    display: flex;
    gap: var(--space-2, 8px);
    flex-shrink: 0;
  }

  &__input {
    flex: 1;
  }

  &__send {
    flex-shrink: 0;
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
  .nlq-widget__cursor,
  .nlq-widget__fab--active,
  .nlq-widget-pop-enter-active,
  .nlq-widget-pop-leave-active {
    transition: none;
    animation: none;
  }
}
</style>
