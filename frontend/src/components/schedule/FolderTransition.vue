<template>
  <Teleport to="body">
    <div
      v-if="folderTransition.active && folderTransition.rect"
      class="folder-transition"
      :class="folderTransition.mode === 'open' ? 'is-open' : 'is-close'"
      :style="stageVars"
      aria-hidden="true"
    >
      <div class="folder-transition__folder">
        <div class="folder-transition__sheet">
          <div class="folder-transition__tab">{{ folderTransition.label }}</div>
          <div v-for="line in 4" :key="line" class="folder-transition__line" />
        </div>
        <div class="folder-transition__flap">
          <span class="folder-transition__cover-label">{{ folderTransition.label }}</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  clearFolderOpen,
  consumeFolderOpen,
  findLastOpened,
  finishFolderTransition,
  folderTransition,
  playFolderTransition,
} from '@/utils/folderTransition';

const OPEN_TOTAL_MS = 820;
const CLOSE_TOTAL_MS = 860;

const router = useRouter();
let timer: number | undefined;

const stageVars = computed<Record<string, string>>(() => {
  const rect = folderTransition.rect;
  if (!rect) return {} as Record<string, string>;
  return {
    '--ft': `${rect.top}px`,
    '--fl': `${rect.left}px`,
    '--fw': `${rect.width}px`,
    '--fh': `${rect.height}px`,
    '--fold-bg': folderTransition.color?.bg ?? 'var(--bg-elevated)',
    '--fold-border': folderTransition.color?.border ?? 'var(--border-default)',
    '--fold-fg': folderTransition.color?.fg ?? 'var(--text-primary)',
  };
});

router.afterEach((to, from) => {
  if (to.name === 'schedule-teacher') {
    // 仅「从书架点击」进入时播放；直接输入 URL 无登记矩形，按普通切换
    const ctx = consumeFolderOpen(Number(to.params.teacherId));
    if (ctx) {
      playFolderTransition('open', ctx);
    }
  } else if (from.name === 'schedule-teacher' && to.name === 'schedules') {
    const ctx = findLastOpened(Number(from.params.teacherId));
    if (ctx) {
      playFolderTransition('close', ctx);
    }
  } else {
    clearFolderOpen();
  }
});

watch(
  () => folderTransition.runId,
  () => {
    if (!folderTransition.active) return;
    window.clearTimeout(timer);
    timer = window.setTimeout(
      finishFolderTransition,
      folderTransition.mode === 'open' ? OPEN_TOTAL_MS : CLOSE_TOTAL_MS,
    );
  },
);

onBeforeUnmount(() => {
  window.clearTimeout(timer);
});
</script>

<style scoped lang="scss">
.folder-transition {
  position: fixed;
  top: var(--ft);
  left: var(--fl);
  width: var(--fw);
  height: var(--fh);
  z-index: 2800;
  perspective: 1400px;
  pointer-events: auto;

  &.is-open {
    animation: folder-expand 360ms cubic-bezier(0.4, 0, 0.2, 1) 300ms both,
      folder-fade-out 140ms ease 680ms both;
  }

  &.is-close {
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    animation: folder-fade-in 120ms ease both,
      folder-collapse 360ms cubic-bezier(0.4, 0, 0.2, 1) 140ms both,
      folder-fade-out 140ms ease 700ms both;
  }

  &__folder {
    position: absolute;
    inset: 0;
    background: var(--fold-bg);
    border: 1px solid var(--fold-border);
    border-radius: 10px;
    box-shadow: var(--shadow-lg);
    transform-style: preserve-3d;
    overflow: hidden;
  }

  &__sheet {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    gap: 18px;
    padding: 64px 48px;
    background: var(--bg-elevated);
    opacity: 0;

    .is-open & {
      animation: sheet-in 260ms ease 360ms both;
    }

    .is-close & {
      animation: sheet-out 120ms ease both;
    }
  }

  &__tab {
    align-self: flex-start;
    padding: 6px 18px;
    border-radius: 999px;
    font-size: 14px;
    font-weight: 600;
    color: var(--fold-fg);
    background: var(--fold-bg);
    border: 1px solid var(--fold-border);
  }

  &__line {
    height: 14px;
    border-radius: 7px;
    background: linear-gradient(
      90deg,
      var(--bg-subtle) 0%,
      var(--border-subtle) 40%,
      var(--bg-subtle) 80%
    );
    background-size: 220% 100%;

    &:nth-child(2) {
      width: 82%;
    }

    &:nth-child(3) {
      width: 64%;
    }

    &:nth-child(4) {
      width: 74%;
    }

    .is-open & {
      animation: sheet-shimmer 1200ms linear 420ms infinite;
    }
  }

  &__flap {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(150deg, var(--fold-bg) 0%, var(--fold-bg) 62%, var(--fold-border) 160%);
    border: 1px solid var(--fold-border);
    border-radius: 10px;
    box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.35);
    transform-origin: bottom center;
    backface-visibility: hidden;

    .is-open & {
      animation: flap-open 200ms ease 120ms both;
    }

    .is-close & {
      animation: flap-close 200ms ease 480ms both;
    }
  }

  &__cover-label {
    font-size: 13px;
    font-weight: 600;
    color: var(--fold-fg);
    letter-spacing: 1px;
    text-align: center;
    padding: 0 6px;
  }
}

@keyframes folder-expand {
  from {
    top: var(--ft);
    left: var(--fl);
    width: var(--fw);
    height: var(--fh);
  }
  to {
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
  }
}

@keyframes folder-collapse {
  from {
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
  }
  to {
    top: var(--ft);
    left: var(--fl);
    width: var(--fw);
    height: var(--fh);
  }
}

@keyframes flap-open {
  from {
    transform: rotateX(0deg);
  }
  to {
    transform: rotateX(105deg);
  }
}

@keyframes flap-close {
  from {
    transform: rotateX(105deg);
  }
  to {
    transform: rotateX(0deg);
  }
}

@keyframes sheet-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes sheet-out {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

@keyframes sheet-shimmer {
  from {
    background-position: 120% 0;
  }
  to {
    background-position: -120% 0;
  }
}

@keyframes folder-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes folder-fade-out {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}
</style>
