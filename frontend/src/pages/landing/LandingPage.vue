<template>
  <div ref="pageRef" class="landing-page">
    <!-- 下层：登录页（撕裂破口后可见，固定视口不随滚动） -->
    <div class="landing-page__under" aria-hidden="true">
      <LoginPage />
    </div>

    <!-- 上层：落地页覆盖层，撕成两半 -->
    <div
      class="landing-page__overlay"
      :class="{ 'is-shaking': shaking, 'is-leaving': leaving }"
    >
      <div
        v-for="side in (['left', 'right'] as const)"
        :key="side"
        class="landing-page__half"
        :class="[`landing-page__half--${side}`, { 'is-torn': tearing }]"
        :style="{ clipPath: side === 'left' ? leftClip : rightClip }"
      >
        <div class="landing-hero" :class="{ 'is-powered': powered }">
          <img class="landing-hero__img" :src="heroImg" alt="" />
          <div class="landing-hero__vignette" aria-hidden="true" />
          <!-- 遮住原图右下角 AI 生成标识的贴片（原图文件零修改） -->
          <div class="landing-hero__patch" aria-hidden="true" />

          <!-- 主标语（打字机） -->
          <div class="landing-hero__slogan">
            <div class="landing-hero__brand">LTPMS · LABORATORY TEACHING PRACTICE MANAGEMENT SYSTEM</div>
            <h1 class="landing-hero__title">
              {{ typed }}<span class="landing-hero__caret" aria-hidden="true" />
            </h1>
            <div class="landing-hero__sub">基于 AI 大模型 · 让每一间实验室尽在掌握</div>
          </div>

          <!-- 功能标签卡（点击下滚到功能描述） -->
          <div
            v-for="tag in TAGS"
            :key="tag.title"
            class="landing-hero__tag"
            :style="{ ...tag.pos, '--rot': `${tag.rot}deg` }"
            role="button"
            tabindex="0"
            @click="scrollToFeature(tag.anchor)"
            @keydown.enter="scrollToFeature(tag.anchor)"
          >
            <span class="landing-hero__tag-title">{{ tag.title }}</span>
            <span class="landing-hero__tag-en">{{ tag.en }}</span>
            <span class="landing-hero__tag-more">了解详情 ↓</span>
          </div>
        </div>
      </div>

      <!-- 裂缝白边高光 -->
      <svg
        v-if="tearing"
        class="landing-page__seam"
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <polyline :points="seamPoints" fill="none" stroke="rgb(255 255 255 / 90%)" stroke-width="2" vector-effect="non-scaling-stroke" />
      </svg>

      <!-- 纸屑 -->
      <div v-if="tearing" class="landing-page__scraps" aria-hidden="true">
        <i v-for="(s, i) in SCRAPS" :key="i" :style="s" />
      </div>
    </div>

    <!-- 功能描述区（标签卡点击后下滚至此） -->
    <section class="landing-features">
      <div class="landing-features__head">
        <span class="landing-features__eyebrow">SYSTEM FEATURES</span>
        <h2 class="landing-features__title">四大核心功能</h2>
      </div>
      <div class="landing-features__grid">
        <article
          v-for="f in FEATURES"
          :id="f.anchor"
          :key="f.anchor"
          class="feature-card"
          :style="{ '--rot': f.rot }"
        >
          <span class="feature-card__en">{{ f.en }}</span>
          <h3 class="feature-card__title">{{ f.title }}</h3>
          <p class="feature-card__desc">{{ f.desc }}</p>
          <ul class="feature-card__list">
            <li v-for="point in f.points" :key="point">{{ point }}</li>
          </ul>
        </article>
      </div>
      <button class="landing-features__top" type="button" @click="scrollTop">回到顶部 ↑</button>
    </section>

    <!-- 电源开关（悬浮，任意位置可进入系统） -->
    <div class="landing-page__power" :class="{ 'is-hidden': tearing || leaving }">
      <button class="power-btn" :class="{ 'is-on': powered }" type="button" @click="powerOn">
        <span class="power-btn__led" aria-hidden="true" />
        <svg class="power-btn__icon" viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M12 3v8"
            fill="none"
            stroke="currentColor"
            stroke-width="2.2"
            stroke-linecap="round"
          />
          <path
            d="M7.2 6.2a7 7 0 1 0 9.6 0"
            fill="none"
            stroke="currentColor"
            stroke-width="2.2"
            stroke-linecap="round"
          />
        </svg>
        <span class="power-btn__label">进入系统</span>
      </button>
      <div class="landing-page__power-hint">POWER ON · 按下开机</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import LoginPage from '@/pages/login/LoginPage.vue';
import heroImg from '@/assets/landing-lab.jpg';

/**
 * 复古开机叙事落地页：
 * 图1 仪器全景作 hero（右下角 AI 标识用 CSS 贴片遮蔽，原图零修改），
 * 功能标签卡 + 打字机标语 + 电源开关按钮；
 * 「进入系统」后从页面中心锯齿撕裂露出下层登录页（~1.0s），
 * prefers-reduced-motion 用户降级为 0.3s 淡出。
 */
const router = useRouter();
const route = useRoute();

// ---------- 锯齿缝（固定种子，两半共用同一路径） ----------
const JAG_X = [0.8, -1.6, 1.4, -0.9, 2.2, -2.4, 0.9, 1.8, -1.2, 2.6, -0.7, 1.5, -2, 0.6];
const jagYs = JAG_X.map((_, i) => +((i / (JAG_X.length - 1)) * 100).toFixed(2));
const jagPts = JAG_X.map((dx, i) => `${(50 + dx).toFixed(2)}% ${jagYs[i]}%`);
const leftClip = `polygon(0% 0%, ${jagPts.join(', ')}, 0% 100%)`;
const rightClip = `polygon(100% 0%, ${jagPts.join(', ')}, 100% 100%)`;
const seamPoints = JAG_X.map((dx, i) => `${(50 + dx).toFixed(2)},${jagYs[i]}`).join(' ');

// ---------- 功能标签卡（点击下滚到对应功能描述） ----------
const TAGS = [
  { title: '实验室管理', en: 'LABORATORY', anchor: 'feature-laboratory', pos: { left: '6%', top: '34%' }, rot: -2 },
  { title: '智能查询', en: 'AI QUERY', anchor: 'feature-ai', pos: { right: '4%', top: '18%' }, rot: 1.8 },
  { title: '实验项目', en: 'EXPERIMENT', anchor: 'feature-experiment', pos: { left: '38%', top: '33%' }, rot: 1.2 },
  { title: '报表中心', en: 'REPORT', anchor: 'feature-report', pos: { left: '10%', bottom: '9%' }, rot: -1.5 },
] as const;

// ---------- 功能描述数据（与标签卡一一对应） ----------
const FEATURES = [
  {
    anchor: 'feature-laboratory',
    en: 'LABORATORY',
    title: '实验室管理',
    rot: '-0.6deg',
    desc: '全校实验室台账，使用状态一目了然。',
    points: ['实验室编号 / 名称 / 负责人 / 设备数统一维护', '启用 / 停用状态管理', '与课表联动，本学期占用情况实时可见'],
  },
  {
    anchor: 'feature-experiment',
    en: 'EXPERIMENT',
    title: '实验项目',
    rot: '0.5deg',
    desc: '教师按课程维护自己的实验项目库。',
    points: ['实验编号自动生成（课程号-学期-流水号）', '类别 / 类型 / 学科等字段字典化配置', '支持 Excel 批量导入与从上学期一键复制'],
  },
  {
    anchor: 'feature-ai',
    en: 'AI QUERY',
    title: '智能查询',
    rot: '-0.4deg',
    desc: '用一句话查询实验教学数据（NL2SQL）。',
    points: ['自然语言提问，自动生成 SQL 并返回结果', '数据权限自动注入，教师仅能查到本人任课范围', '登录后右下角悬浮机器人，随处快查'],
  },
  {
    anchor: 'feature-report',
    en: 'REPORT',
    title: '报表中心',
    rot: '0.6deg',
    desc: '实验教学数据统计与报表输出。',
    points: ['全校汇总与教师明细分层可见', '实验室使用情况 / 人时数多维统计', '结果支持导出与打印'],
  },
];

// ---------- 纸屑（撕裂时飘落） ----------
const SCRAPS = [
  { left: '46%', top: '30%', width: '14px', height: '18px', background: '#e8e2d0', animationDelay: '0s', '--dx': '-46px' },
  { left: '52%', top: '46%', width: '10px', height: '14px', background: '#4a95e6', animationDelay: '0.08s', '--dx': '38px' },
  { left: '49%', top: '60%', width: '16px', height: '12px', background: '#f4f1e6', animationDelay: '0.16s', '--dx': '-30px' },
  { left: '47%', top: '18%', width: '9px', height: '13px', background: '#d8d3c2', animationDelay: '0.05s', '--dx': '52px' },
  { left: '51%', top: '72%', width: '12px', height: '16px', background: '#378add', animationDelay: '0.22s', '--dx': '-24px' },
  { left: '50%', top: '8%', width: '11px', height: '11px', background: '#efe9d8', animationDelay: '0.12s', '--dx': '20px' },
].map((s) => ({ ...s, clipPath: 'polygon(20% 0%, 100% 12%, 78% 100%, 0% 82%)' }) as unknown as Record<string, string>);

// ---------- 状态与开机时序 ----------
const powered = ref(false);
const shaking = ref(false);
const tearing = ref(false);
const leaving = ref(false);
const pageRef = ref<HTMLElement | null>(null);
let busy = false;

/** 标签卡点击：下滚到对应功能描述卡 */
function scrollToFeature(anchor: string): void {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  document.getElementById(anchor)?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' });
}

/** 回到顶部 */
function scrollTop(): void {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  pageRef.value?.scrollTo({ top: 0, behavior: reduced ? 'auto' : 'smooth' });
}

function goLogin(): void {
  const redirect = route.query.redirect;
  router.push({ path: '/login', query: redirect ? { redirect: String(redirect) } : undefined });
}

function powerOn(): void {
  if (busy) return;
  busy = true;
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduced) {
    leaving.value = true;
    window.setTimeout(goLogin, 300);
    return;
  }
  // 若停留在功能描述区，先平滑滚回顶部再撕裂（撕裂作用于 hero 视口）
  const needsScroll = (pageRef.value?.scrollTop ?? 0) > 10;
  const preDelay = needsScroll ? 450 : 0;
  if (needsScroll) {
    pageRef.value?.scrollTo({ top: 0, behavior: 'smooth' });
  }
  powered.value = true;
  window.setTimeout(() => {
    shaking.value = true;
  }, preDelay);
  window.setTimeout(() => {
    shaking.value = false;
  }, preDelay + 420);
  window.setTimeout(() => {
    tearing.value = true;
  }, preDelay + 480);
  window.setTimeout(goLogin, preDelay + 1520);
}

// ---------- 打字机标语 ----------
const TITLE_FULL = '实验教学项目管理系统';
const typed = ref('');
let typeTimer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  let i = 0;
  typeTimer = setInterval(() => {
    i += 1;
    typed.value = TITLE_FULL.slice(0, i);
    if (i >= TITLE_FULL.length && typeTimer) {
      clearInterval(typeTimer);
      typeTimer = null;
    }
  }, 130);
});

onBeforeUnmount(() => {
  if (typeTimer) {
    clearInterval(typeTimer);
  }
});
</script>

<style scoped lang="scss">
.landing-page {
  position: fixed;
  inset: 0;
  z-index: 50;
  overflow-y: auto;
  overflow-x: hidden;
  background: #10122a;

  &::-webkit-scrollbar {
    width: 10px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgb(255 255 255 / 18%);
    border-radius: 5px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }
}

.landing-page__under {
  position: fixed;
  inset: 0;
  z-index: 0;
}

.landing-page__overlay {
  position: relative;
  z-index: 10;
  height: 100vh;

  &.is-shaking {
    animation: landing-shake 0.4s linear;
  }

  &.is-leaving {
    animation: landing-fade 0.3s forwards;
  }
}

.landing-page__half {
  position: absolute;
  inset: 0;
  transition: transform 1s cubic-bezier(0.55, 0, 0.85, 0.45);

  &.is-torn.landing-page__half--left {
    transform: translateX(-7%) translateY(2%) rotate(-1.5deg);
  }

  &.is-torn.landing-page__half--right {
    transform: translateX(7%) translateY(2.5%) rotate(1.5deg);
  }
}

.landing-hero {
  position: absolute;
  inset: 0;
  overflow: hidden;

  &__img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.6s ease;
  }

  // 悬停标签卡时图片轻微推近
  &:has(.landing-hero__tag:hover) &__img {
    transform: scale(1.03);
  }

  &__vignette {
    position: absolute;
    inset: 0;
    background:
      linear-gradient(180deg, rgb(8 10 30 / 58%) 0%, rgb(8 10 30 / 12%) 30%, rgb(8 10 30 / 8%) 55%, rgb(8 10 30 / 60%) 100%);
  }

  // 遮蔽右下角「AI生成」标识：模糊贴片（不改原图）
  &__patch {
    position: absolute;
    right: 0;
    bottom: 0;
    width: 150px;
    height: 62px;
    background: rgb(30 32 72 / 42%);
    backdrop-filter: blur(12px) saturate(1.1);
    border-top-left-radius: 16px;
  }

  &__slogan {
    position: absolute;
    top: 7%;
    left: 50%;
    transform: translateX(-50%);
    width: max-content;
    max-width: 92vw;
    color: #fff;
    text-align: center;
    text-shadow: 0 2px 14px rgb(0 0 0 / 45%);
  }

  &__brand {
    font-family: 'Courier New', monospace;
    font-size: 12px;
    letter-spacing: 0.24em;
    opacity: 0.82;
  }

  &__title {
    margin-top: 10px;
    font-size: clamp(30px, 4.6vw, 54px);
    font-weight: 800;
    letter-spacing: 0.06em;
    line-height: 1.2;
  }

  &__caret {
    display: inline-block;
    width: 3px;
    height: 0.92em;
    margin-left: 6px;
    vertical-align: -0.08em;
    animation: landing-caret 0.9s steps(1) infinite;
    background: #ffd98a;
  }

  &__sub {
    margin-top: 10px;
    font-size: clamp(13px, 1.3vw, 16px);
    letter-spacing: 0.3em;
    opacity: 0.9;
  }

  &__tag {
    position: absolute;
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 10px 16px;
    cursor: pointer;
    background: linear-gradient(165deg, rgb(250 247 238 / 96%) 0%, rgb(235 230 214 / 94%) 100%);
    border: 1px solid rgb(120 110 84 / 55%);
    border-radius: 6px;
    box-shadow:
      inset 0 1px 0 rgb(255 255 255 / 70%),
      0 6px 18px rgb(0 0 0 / 30%);
    transform: rotate(var(--rot, 0deg));
    transition:
      transform 0.3s ease,
      box-shadow 0.3s ease,
      filter 0.3s ease;

    &:hover {
      filter: brightness(1.06);
      transform: rotate(var(--rot, 0deg)) translateY(-5px);
      box-shadow:
        inset 0 1px 0 rgb(255 255 255 / 70%),
        0 14px 28px rgb(0 0 0 / 38%),
        0 0 0 2px rgb(255 217 138 / 55%);

      .landing-hero__tag-more {
        color: #a05a1e;
      }
    }

    &:focus-visible {
      outline: 2px solid rgb(255 217 138 / 75%);
      outline-offset: 2px;
    }
  }

  &__tag-more {
    margin-top: 3px;
    font-family: 'Courier New', monospace;
    font-size: 10px;
    letter-spacing: 0.08em;
    color: rgb(76 68 51 / 55%);
    transition: color 0.3s ease;
  }

  &__tag-title {
    font-size: 15px;
    font-weight: 700;
    color: #4c4433;
  }

  &__tag-en {
    font-family: 'Courier New', monospace;
    font-size: 10px;
    letter-spacing: 0.18em;
    color: rgb(76 68 51 / 62%);
  }
}

.landing-page__seam {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 0 6px rgb(255 255 255 / 70%));
  animation: landing-seam-fade 1s 0.35s forwards;
}

.landing-page__scraps {
  position: absolute;
  inset: 0;
  pointer-events: none;

  i {
    position: absolute;
    display: block;
    opacity: 0;
    animation: landing-scrap 1.15s ease-in forwards;
  }
}

.landing-page__power {
  position: fixed;
  bottom: 6.5%;
  left: 50%;
  z-index: 30;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  transform: translateX(-50%);
  transition: opacity 0.25s ease;

  &.is-hidden {
    opacity: 0;
    pointer-events: none;
  }
}

.power-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 30px;
  font-size: 16px;
  font-weight: 600;
  color: #f3f0e8;
  letter-spacing: 0.2em;
  cursor: pointer;
  background: linear-gradient(180deg, #3a3d55 0%, #23253c 60%, #191b2e 100%);
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 999px;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 18%),
    inset 0 -3px 0 rgb(0 0 0 / 35%),
    0 10px 26px rgb(0 0 0 / 45%);
  transition:
    transform 0.15s ease,
    box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow:
      inset 0 1px 0 rgb(255 255 255 / 18%),
      inset 0 -3px 0 rgb(0 0 0 / 35%),
      0 14px 32px rgb(0 0 0 / 55%),
      0 0 0 2px rgb(255 217 138 / 35%);
  }

  &:active {
    transform: translateY(1px) scale(0.98);
  }

  &__led {
    position: absolute;
    top: 8px;
    right: 14px;
    width: 8px;
    height: 8px;
    background: #e05656;
    border-radius: 50%;
    box-shadow: 0 0 8px rgb(224 86 86 / 90%);

    .is-on & {
      background: #3ecf6f;
      box-shadow: 0 0 10px rgb(62 207 111 / 95%);
    }
  }

  &__icon {
    width: 20px;
    height: 20px;
    color: #ffd98a;
  }

  &__label {
    text-shadow: 0 1px 0 rgb(0 0 0 / 40%);
  }
}

.landing-page__power-hint {
  font-family: 'Courier New', monospace;
  font-size: 11px;
  letter-spacing: 0.32em;
  color: rgb(255 255 255 / 62%);
}

// ---------- 功能描述区 ----------
.landing-features {
  position: relative;
  z-index: 10;
  padding: 88px 6vw 96px;
  background: linear-gradient(180deg, #10122a 0%, #181b3a 50%, #10122a 100%);

  &__head {
    text-align: center;
  }

  &__eyebrow {
    font-family: 'Courier New', monospace;
    font-size: 12px;
    letter-spacing: 0.32em;
    color: rgb(255 217 138 / 72%);
  }

  &__title {
    margin-top: 12px;
    font-size: clamp(24px, 2.6vw, 32px);
    font-weight: 700;
    color: #fff;
    letter-spacing: 0.12em;
    text-shadow: 0 2px 12px rgb(0 0 0 / 40%);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 26px;
    max-width: 1080px;
    margin: 44px auto 0;

    @media (max-width: 900px) {
      grid-template-columns: 1fr;
    }
  }

  &__top {
    display: block;
    margin: 48px auto 0;
    padding: 10px 28px;
    font-family: 'Courier New', monospace;
    font-size: 13px;
    letter-spacing: 0.2em;
    color: #f3f0e8;
    cursor: pointer;
    background: transparent;
    border: 1px solid rgb(255 255 255 / 32%);
    border-radius: 999px;
    transition:
      border-color 0.3s ease,
      color 0.3s ease,
      transform 0.2s ease;

    &:hover {
      color: #ffd98a;
      border-color: rgb(255 217 138 / 60%);
      transform: translateY(-2px);
    }
  }
}

.feature-card {
  padding: 26px 28px;
  background: linear-gradient(165deg, rgb(250 247 238 / 97%) 0%, rgb(236 231 215 / 95%) 100%);
  border: 1px solid rgb(120 110 84 / 55%);
  border-radius: 10px;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 70%),
    0 10px 26px rgb(0 0 0 / 28%);
  scroll-margin-top: 28px;
  transform: rotate(var(--rot, 0deg));
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;

  &:hover {
    transform: rotate(var(--rot, 0deg)) translateY(-4px);
    box-shadow:
      inset 0 1px 0 rgb(255 255 255 / 70%),
      0 16px 32px rgb(0 0 0 / 36%),
      0 0 0 2px rgb(255 217 138 / 45%);
  }

  &__en {
    font-family: 'Courier New', monospace;
    font-size: 11px;
    letter-spacing: 0.22em;
    color: rgb(76 68 51 / 62%);
  }

  &__title {
    margin-top: 6px;
    font-size: 20px;
    font-weight: 700;
    color: #4c4433;
  }

  &__desc {
    margin-top: 8px;
    font-size: 14px;
    color: #6b6249;
  }

  &__list {
    margin: 14px 0 0;
    padding: 0;
    list-style: none;

    li {
      position: relative;
      padding: 5px 0 5px 18px;
      font-size: 13px;
      color: #57503c;

      &::before {
        content: '▪';
        position: absolute;
        left: 0;
        color: #8a7b52;
      }
    }
  }
}

// ---------- 动画 ----------
@keyframes landing-caret {
  0%,
  55% {
    opacity: 1;
  }

  56%,
  100% {
    opacity: 0;
  }
}

@keyframes landing-shake {
  0%,
  100% {
    transform: translate(0, 0);
  }

  20% {
    transform: translate(-2px, 1px);
  }

  40% {
    transform: translate(2px, -1px);
  }

  60% {
    transform: translate(-1.5px, -1px);
  }

  80% {
    transform: translate(1.5px, 1px);
  }
}

@keyframes landing-scrap {
  0% {
    opacity: 0;
    transform: translate(0, 0) rotate(0deg);
  }

  12% {
    opacity: 1;
  }

  100% {
    opacity: 0;
    transform: translate(var(--dx, 30px), 58vh) rotate(560deg);
  }
}

@keyframes landing-seam-fade {
  0% {
    opacity: 1;
  }

  100% {
    opacity: 0;
  }
}

@keyframes landing-fade {
  from {
    opacity: 1;
  }

  to {
    opacity: 0;
  }
}

@media (max-width: 768px) {
  .landing-hero__tag {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .landing-page__half,
  .landing-hero__img,
  .landing-hero__tag,
  .power-btn {
    transition: none;
    animation: none;
  }

  .landing-hero__caret {
    animation: none;
  }
}
</style>
