<template>
  <div ref="pageRef" class="landing-page">
    <!-- 下层：登录页（撕裂破口后可见，固定视口不随滚动） -->
    <div class="landing-page__under" aria-hidden="true">
      <LoginPage />
    </div>

    <!-- 上层：落地页覆盖层，撕成两半 -->
    <div
      id="hero"
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

    <!-- 功能描述页（每项功能独立一页，标签卡点击翻滚至此） -->
    <template v-for="f in FEATURES" :key="f.anchor">
      <section :id="f.anchor" class="feat-panel" :class="`feat-panel--${f.theme}`">
        <span class="feat-panel__ghost" aria-hidden="true">{{ f.serial }}</span>
        <div class="feat-sheet">
          <i class="feat-sheet__tape feat-sheet__tape--l" aria-hidden="true" />
          <i class="feat-sheet__tape feat-sheet__tape--r" aria-hidden="true" />
          <header class="feat-sheet__head">
            <span class="feat-sheet__form">{{ f.form }}</span>
            <span class="feat-sheet__en">{{ f.en }}</span>
          </header>
          <h3 class="feat-sheet__title">{{ f.serial }} · {{ f.title }}</h3>
          <p class="feat-sheet__desc">{{ f.desc }}</p>

          <!-- 终端主题：NL2SQL 会话演示 -->
          <div v-if="f.theme === 'terminal'" class="feat-term" aria-hidden="true">
            <div class="feat-term__line">&gt; {{ f.term }}</div>
            <div class="feat-term__line feat-term__line--ai">[AI] 生成 SQL · 注入数据权限 · 执行查询 (0.8s)</div>
            <div class="feat-term__line feat-term__line--ok">✓ 查询完成 · 返回 128 行</div>
            <div class="feat-term__line">&gt; <span class="feat-term__cursor">_</span></div>
          </div>

          <ul class="feat-sheet__list">
            <li v-for="point in f.points" :key="point">{{ point }}</li>
          </ul>

          <!-- 报表主题：迷你柱状图 -->
          <div v-if="f.theme === 'report'" class="feat-bars" aria-hidden="true">
            <i style="--h: 42%" /><i style="--h: 66%" /><i style="--h: 35%" /><i style="--h: 78%" /><i style="--h: 55%" />
          </div>

          <footer class="feat-sheet__foot">
            <span>LTPMS · 实验教学项目管理系统</span>
            <span class="feat-sheet__stamp">{{ f.stamp }}</span>
          </footer>
        </div>
      </section>
    </template>

    <!-- 右侧分页圆点导航 -->
    <div class="landing-page__dots" :class="{ 'is-hidden': tearing || leaving }">
      <button
        v-for="d in DOTS"
        :key="d.id"
        type="button"
        class="landing-page__dot"
        :class="{ 'is-active': activeSection === d.id }"
        :aria-label="d.label"
        @click="scrollToSection(d.id)"
      />
    </div>

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

// ---------- 功能描述页数据（每项功能独立一页，主题各异） ----------
const FEATURES = [
  {
    anchor: 'feature-laboratory',
    serial: '01',
    form: 'FORM LT-01 · 实验室台账',
    en: 'LABORATORY',
    title: '实验室管理',
    theme: 'paper-blue',
    stamp: 'IN USE · 使用中',
    desc: '全校实验室统一建档，一台一档，账实相符。从编号到负责人、从设备数到启用状态，每间实验室都有一页属于自己的档案。',
    points: [
      '实验室编号 / 名称 / 负责人 / 设备数统一维护',
      '启用 / 停用状态管理，编号唯一性校验',
      '与课表联动，本学期占用情况实时可见',
    ],
  },
  {
    anchor: 'feature-experiment',
    serial: '02',
    form: 'FORM LT-02 · 实验记录卡',
    en: 'EXPERIMENT',
    title: '实验项目',
    theme: 'paper-amber',
    stamp: '已归档 · INDEXED',
    desc: '实验项目按课程建卡，编号规则可追溯。类别、类型、学科全部字典化配置，像整理一叠实验记录卡一样井井有条。',
    points: [
      '实验编号自动生成（课程号-学期-流水号）',
      '类别 / 类型 / 学科 / 要求等字段字典化配置',
      '支持 Excel 批量导入与从上学期一键复制',
    ],
  },
  {
    anchor: 'feature-ai',
    serial: '03',
    form: 'TERMINAL LT-03 · NL2SQL',
    en: 'AI QUERY',
    title: '智能查询',
    theme: 'terminal',
    stamp: 'ONLINE · 在线',
    term: '本学期我开设了哪些实验课程？',
    desc: '自然语言直达数据，权限在语句生成之前注入。像操作一台老式终端一样，向系统提问。',
    points: [
      '一句话提问，自动生成 SQL 并返回结果表格',
      '数据权限自动注入，教师仅能查到本人任课范围',
      '登录后右下角悬浮机器人，随处快查',
    ],
  },
  {
    anchor: 'feature-report',
    serial: '04',
    form: 'FORM LT-04 · 统计报表',
    en: 'REPORT',
    title: '报表中心',
    theme: 'paper-grid',
    stamp: '季度归档 · Q3',
    desc: '把一学期的实验教学装订成一册报表。汇总、明细、人时数，按角色分层可见。',
    points: [
      '全校汇总与教师明细分层可见',
      '实验室使用情况 / 人时数多维统计',
      '结果支持导出与打印',
    ],
  },
];

// ---------- 右侧分页导航 ----------
const DOTS = [
  { id: 'hero', label: '首页' },
  { id: 'feature-laboratory', label: '实验室管理' },
  { id: 'feature-experiment', label: '实验项目' },
  { id: 'feature-ai', label: '智能查询' },
  { id: 'feature-report', label: '报表中心' },
] as const;

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
const activeSection = ref('hero');
let busy = false;
let sectionObserver: IntersectionObserver | null = null;

/** 平滑滚动到指定分页（标签卡 / 圆点导航共用） */
function scrollToSection(id: string): void {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  document.getElementById(id)?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' });
}

/** 标签卡点击：翻滚到对应功能描述页 */
function scrollToFeature(anchor: string): void {
  scrollToSection(anchor);
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

  // 分页圆点高亮：观察 hero 与四个功能页
  sectionObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          activeSection.value = entry.target.id;
        }
      }
    },
    { root: pageRef.value, threshold: 0.55 },
  );
  for (const d of DOTS) {
    const el = document.getElementById(d.id);
    if (el) {
      sectionObserver.observe(el);
    }
  }
});

onBeforeUnmount(() => {
  if (typeTimer) {
    clearInterval(typeTimer);
  }
  sectionObserver?.disconnect();
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
  // 分页吸附：翻滚到各功能页时有"翻页"的驻停感
  scroll-snap-type: y proximity;

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

// ---------- 功能描述页（每项功能独立一页） ----------
.feat-panel {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 92vh;
  padding: 96px 6vw;
  overflow: hidden;
  scroll-snap-align: start;

  &--paper-blue {
    background: linear-gradient(180deg, #10122a 0%, #1a2340 55%, #10122a 100%);
  }

  &--paper-amber {
    background: linear-gradient(180deg, #10122a 0%, #2a2338 55%, #10122a 100%);
  }

  &--terminal {
    background: linear-gradient(180deg, #0a0f1e 0%, #0d1a24 55%, #0a0f1e 100%);
  }

  &--paper-grid {
    background: linear-gradient(180deg, #10122a 0%, #141c34 55%, #10122a 100%);
  }

  &__ghost {
    position: absolute;
    top: 4%;
    right: 6%;
    font-size: clamp(120px, 18vw, 220px);
    font-weight: 800;
    line-height: 1;
    color: transparent;
    pointer-events: none;
    user-select: none;
    -webkit-text-stroke: 1px rgb(255 217 138 / 20%);
  }

  &--terminal &__ghost {
    -webkit-text-stroke: 1px rgb(126 226 168 / 22%);
  }
}

.feat-sheet {
  position: relative;
  width: min(880px, 100%);
  padding: 46px 52px 34px;
  transform: rotate(-0.4deg);

  @media (max-width: 768px) {
    padding: 32px 24px 26px;
  }

  // 纸卡主题共用
  .feat-panel--paper-blue &,
  .feat-panel--paper-amber &,
  .feat-panel--paper-grid & {
    background: linear-gradient(165deg, rgb(250 247 238 / 98%) 0%, rgb(236 231 215 / 96%) 100%);
    border: 1px solid rgb(120 110 84 / 55%);
    border-radius: 8px;
    box-shadow:
      inset 0 1px 0 rgb(255 255 255 / 70%),
      0 24px 60px rgb(0 0 0 / 45%);
  }

  // 网格纹（报表）
  .feat-panel--paper-grid & {
    background-image:
      repeating-linear-gradient(0deg, transparent 0 27px, rgb(120 110 84 / 12%) 27px 28px),
      linear-gradient(165deg, rgb(250 247 238 / 97%) 0%, rgb(238 233 218 / 95%) 100%);
  }

  // 终端主题
  .feat-panel--terminal & {
    background:
      repeating-linear-gradient(0deg, rgb(255 255 255 / 3%) 0 2px, transparent 2px 4px),
      #0c1522;
    border: 1px solid #24483a;
    border-radius: 10px;
    box-shadow:
      inset 0 0 40px rgb(0 0 0 / 55%),
      0 0 32px rgb(126 226 168 / 12%),
      0 24px 60px rgb(0 0 0 / 45%);
    color: #7ee2a8;
  }

  &__tape {
    position: absolute;
    top: -12px;
    width: 110px;
    height: 26px;
    background: rgb(255 217 138 / 32%);
    border: 1px solid rgb(255 217 138 / 18%);

    &--l {
      left: -34px;
      transform: rotate(-5deg);
    }

    &--r {
      right: -34px;
      transform: rotate(4deg);
    }
  }

  &__head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
    font-family: 'Courier New', monospace;
    font-size: 11px;
    letter-spacing: 0.22em;
  }

  .feat-panel--terminal &__head {
    color: rgb(126 226 168 / 65%);
  }

  .feat-panel--paper-blue &__head,
  .feat-panel--paper-amber &__head,
  .feat-panel--paper-grid &__head {
    color: rgb(76 68 51 / 62%);
  }

  &__title {
    margin-top: 14px;
    font-size: clamp(26px, 3vw, 36px);
    font-weight: 800;
    letter-spacing: 0.06em;
    line-height: 1.2;
  }

  .feat-panel--terminal &__title {
    color: #b8f0cd;
    text-shadow: 0 0 18px rgb(126 226 168 / 35%);
  }

  .feat-panel--paper-blue &__title,
  .feat-panel--paper-amber &__title,
  .feat-panel--paper-grid &__title {
    color: #3f3826;
  }

  &__desc {
    margin-top: 12px;
    max-width: 640px;
    font-size: 15px;
    line-height: 1.8;
  }

  .feat-panel--terminal &__desc {
    color: rgb(184 240 205 / 82%);
  }

  .feat-panel--paper-blue &__desc,
  .feat-panel--paper-amber &__desc,
  .feat-panel--paper-grid &__desc {
    color: #6b6249;
  }

  &__list {
    margin: 20px 0 0;
    padding: 0;
    list-style: none;

    li {
      position: relative;
      padding: 7px 0 7px 26px;
      font-size: 14px;
      line-height: 1.6;
    }
  }

  .feat-panel--terminal &__list li {
    color: rgb(184 240 205 / 88%);

    &::before {
      content: '▸';
      position: absolute;
      left: 2px;
      color: #7ee2a8;
    }
  }

  .feat-panel--paper-blue &__list li,
  .feat-panel--paper-amber &__list li,
  .feat-panel--paper-grid &__list li {
    color: #57503c;

    &::before {
      content: '✓';
      position: absolute;
      left: 2px;
      font-weight: 700;
      color: #8a7b52;
    }
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 26px;
    padding-top: 14px;
    font-family: 'Courier New', monospace;
    font-size: 11px;
    letter-spacing: 0.14em;
  }

  .feat-panel--terminal &__foot {
    color: rgb(126 226 168 / 55%);
    border-top: 1px dashed rgb(126 226 168 / 30%);
  }

  .feat-panel--paper-blue &__foot,
  .feat-panel--paper-amber &__foot,
  .feat-panel--paper-grid &__foot {
    color: rgb(76 68 51 / 55%);
    border-top: 1px dashed rgb(120 110 84 / 45%);
  }

  &__stamp {
    padding: 2px 12px;
    font-weight: 700;
    letter-spacing: 0.14em;
    border: 1.5px solid currentColor;
    border-radius: 4px;
    opacity: 0.85;
    transform: rotate(2deg);
    white-space: nowrap;
  }
}

// 终端会话演示
.feat-term {
  margin: 20px 0 4px;
  padding: 16px 18px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.9;
  background: #081018;
  border: 1px solid #1e3a2c;
  border-radius: 6px;

  &__line {
    color: #7ee2a8;

    &--ai {
      color: #ffd98a;
    }

    &--ok {
      color: #9ad1ff;
    }
  }

  &__cursor {
    animation: landing-caret 0.9s steps(1) infinite;
  }
}

// 报表迷你柱状图
.feat-bars {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  height: 92px;
  margin: 22px 0 4px;

  i {
    flex: 1;
    height: var(--h);
    background: linear-gradient(180deg, #4a95e6 0%, #2a7bc8 100%);
    border-radius: 3px 3px 0 0;
    box-shadow: inset 0 1px 0 rgb(255 255 255 / 35%);
  }
}

// 右侧分页圆点导航
.landing-page__dots {
  position: fixed;
  right: 22px;
  top: 50%;
  z-index: 35;
  display: flex;
  flex-direction: column;
  gap: 14px;
  transform: translateY(-50%);
  transition: opacity 0.25s ease;

  &.is-hidden {
    opacity: 0;
    pointer-events: none;
  }

  @media (max-width: 768px) {
    display: none;
  }
}

.landing-page__dot {
  width: 10px;
  height: 10px;
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 1px solid rgb(255 255 255 / 55%);
  border-radius: 50%;
  transition:
    background 0.25s ease,
    border-color 0.25s ease,
    transform 0.25s ease;

  &:hover {
    border-color: #ffd98a;
  }

  &.is-active {
    background: #ffd98a;
    border-color: #ffd98a;
    transform: scale(1.2);
    box-shadow: 0 0 8px rgb(255 217 138 / 70%);
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
