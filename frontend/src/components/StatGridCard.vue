<template>
  <div class="stat-grid-card">
    <div class="stat-grid-card__header">
      <span class="stat-grid-card__title">{{ title }}</span>
      <span class="stat-grid-card__total">{{ totalLabel }}</span>
    </div>

    <div class="stat-grid-card__subtitle">
      <slot name="subtitle">
        <span class="stat-grid-card__subtitle-used">{{ usedCount }}</span>
        {{ usedLabel }} ·
        <span class="stat-grid-card__subtitle-free">{{ freeCount }}</span>
        {{ freeLabel }} ·
        <strong class="stat-grid-card__subtitle-percent">{{ percent }}%</strong>
      </slot>
    </div>

    <!-- 空状态 -->
    <div v-if="total === 0" class="stat-grid-card__empty">
      <span class="stat-grid-card__empty-text">
        暂无数据<template v-if="emptyHint"> · {{ emptyHint }}</template>
      </span>
      <el-button
        v-if="emptyActionTo && emptyActionText"
        size="small"
        type="primary"
        plain
        @click="router.push(emptyActionTo)"
      >
        {{ emptyActionText }}
      </el-button>
    </div>

    <!-- 方格图 -->
    <template v-else>
      <div class="stat-grid-card__grid" :style="gridStyle">
        <div
          v-for="(cell, index) in visibleCells"
          :key="cell.id ?? index"
          class="stat-grid-card__cell"
          :class="[
            cell.used ? 'stat-grid-card__cell--used' : 'stat-grid-card__cell--free',
            { 'stat-grid-card__cell--clickable': clickable },
          ]"
          :style="{ animationDelay: `${Math.min(index, 18) * 40}ms` }"
          @click="emit('cellClick', cell)"
        >
          {{ cell.code }}
          <span class="stat-grid-card__tip">
            {{ cell.name }}<template v-if="cell.extra"> · {{ cell.extra }}</template>
          </span>
        </div>
      </div>

      <div class="stat-grid-card__footer">
        <span v-if="usedCount > 0" class="stat-grid-card__legend">
          <i class="stat-grid-card__dot stat-grid-card__dot--used" />{{ usedLabel }}
        </span>
        <span v-if="freeCount > 0" class="stat-grid-card__legend">
          <i class="stat-grid-card__dot stat-grid-card__dot--free" />{{ freeLabel }}
        </span>
        <el-link
          v-if="linkTo && linkText"
          type="primary"
          :underline="false"
          class="stat-grid-card__link"
          @click="router.push(linkTo)"
        >
          <template v-if="hiddenCount > 0">还有 {{ hiddenCount }} 个，</template>{{ linkText }}
        </el-link>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import type { IStatCell } from '@/types/api';

/**
 * 概览页方格图统计卡片：立体渐变方格、错峰淡入、hover 上浮 + tooltip、
 * 超上限截断（"还有 N 个"）、空状态（提示文案 / 去创建按钮）。
 */
const props = withDefaults(
  defineProps<{
    /** 卡片标题 */
    title: string;
    /** 方格数据（一个实体一格） */
    cells: IStatCell[];
    /** 图例：使用中含义（如"使用中"） */
    usedLabel?: string;
    /** 图例：未使用含义（如"空闲"） */
    freeLabel?: string;
    /** 右上角总数标签（如"7 间"） */
    totalLabel?: string;
    /** 底部链接文字（如"查看详情"） */
    linkText?: string;
    /** 底部链接跳转路由 */
    linkTo?: string;
    /** 空状态提示文案（如"实验项目由教师创建"） */
    emptyHint?: string;
    /** 空状态按钮文字（如"去创建"） */
    emptyActionText?: string;
    /** 空状态按钮跳转路由 */
    emptyActionTo?: string;
    /** 方格数量上限，超出截断 */
    maxCells?: number;
    /** 固定列数（周历等场景），缺省自适应 */
    columns?: number;
    /** 格子是否可点击（点击时触发 cellClick 事件） */
    clickable?: boolean;
  }>(),
  {
    usedLabel: '使用中',
    freeLabel: '未使用',
    totalLabel: '',
    linkText: '',
    linkTo: '',
    emptyHint: '',
    emptyActionText: '',
    emptyActionTo: '',
    maxCells: 36,
    columns: 0,
    clickable: false,
  },
);

const emit = defineEmits<{
  /** 点击某个方格 */
  (e: 'cellClick', cell: IStatCell): void;
}>();

const router = useRouter();

const total = computed(() => props.cells.length);
const usedCount = computed(() => props.cells.filter((c) => c.used).length);
const freeCount = computed(() => total.value - usedCount.value);
const percent = computed(() =>
  total.value === 0 ? 0 : Math.round((usedCount.value / total.value) * 100),
);
const visibleCells = computed(() => props.cells.slice(0, props.maxCells));
const hiddenCount = computed(() => total.value - visibleCells.value.length);

const gridStyle = computed(() =>
  props.columns > 0
    ? { gridTemplateColumns: `repeat(${props.columns}, 1fr)` }
    : { gridTemplateColumns: 'repeat(auto-fill, minmax(30px, 1fr))' },
);
</script>

<style scoped lang="scss">
.stat-grid-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: var(--space-4, 14px) var(--space-5, 16px);
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--border-subtle, #e4e7ed);
  border-radius: var(--border-radius-lg, 12px);
  transition:
    transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
    box-shadow 0.3s,
    border-color 0.3s;

  // 悬停：整卡上浮 + 顶部彩色描边
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    border-radius: 3px 3px 0 0;
    background: linear-gradient(90deg, var(--el-color-primary, #378add), #534ab7);
    opacity: 0;
    transition: opacity 0.3s;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgb(0 0 0 / 6%);
    border-color: var(--el-color-primary-light-7, #b5d4f4);

    &::before {
      opacity: 1;
    }
  }

  &__header {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: 4px;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  &__total {
    font-size: 12px;
    color: var(--el-text-color-secondary, #909399);
  }

  &__subtitle {
    margin-bottom: 12px;
    font-size: 12px;
    color: var(--el-text-color-secondary, #909399);
  }

  &__subtitle-used,
  &__subtitle-percent {
    color: var(--el-color-primary, #378add);
  }

  &__subtitle-free {
    color: var(--el-text-color-placeholder, #a8abb2);
  }

  &__grid {
    display: grid;
    gap: 5px;
    margin-bottom: 12px;
  }

  &__cell {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    aspect-ratio: 1;
    overflow: visible;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 0.02em;
    cursor: default;
    border-radius: 10px;
    opacity: 0;
    animation: stat-grid-card-rise 0.5s forwards;
    transition:
      transform 0.25s cubic-bezier(0.4, 0, 0.2, 1),
      box-shadow 0.25s;

    &--clickable {
      cursor: pointer;
    }

    &--used {
      color: #fff;
      // 键帽凸起：顶部高光带 + 主色 + 底部暗面，底部挤出实体侧面 + 投影
      background: linear-gradient(180deg, #63a8ee 0%, #4393e2 20%, #378add 58%, #2270bd 100%);
      border: 0.5px solid #1b5f9e;
      box-shadow:
        inset 0 2px 0 rgb(255 255 255 / 38%),
        inset 0 -3px 0 rgb(0 0 0 / 16%),
        0 3px 0 #1a5c9c,
        0 5px 8px rgb(26 92 156 / 30%);
      text-shadow: 0 1px 1px rgb(0 0 0 / 18%);
    }

    &--free {
      color: #888780;
      // 凹陷键帽：顶暗底亮（光源反转）+ 内阴影
      background: linear-gradient(180deg, #eceadf 0%, #f4f2ea 100%);
      border: 0.5px solid #d3d1c7;
      box-shadow:
        inset 0 2px 3px rgb(0 0 0 / 10%),
        inset 0 -1px 0 rgb(255 255 255 / 75%);
    }

    &:hover {
      z-index: 2;

      &.stat-grid-card__cell--used {
        // 凸起键帽被"抬起"：侧面拉长 + 投影加深
        transform: translateY(-4px) scale(1.08);
        box-shadow:
          inset 0 2px 0 rgb(255 255 255 / 38%),
          inset 0 -3px 0 rgb(0 0 0 / 16%),
          0 6px 0 #1a5c9c,
          0 11px 16px rgb(26 92 156 / 38%);
      }

      &.stat-grid-card__cell--free {
        transform: translateY(-3px) scale(1.08);
        box-shadow:
          inset 0 2px 3px rgb(0 0 0 / 8%),
          inset 0 -1px 0 rgb(255 255 255 / 75%),
          0 4px 8px rgb(0 0 0 / 10%);
      }

      .stat-grid-card__tip {
        opacity: 1;
        transform: translateX(-50%) translateY(0);
      }
    }
  }

  // 悬停 tooltip（向下弹出，避免被卡片裁剪）
  &__tip {
    position: absolute;
    top: calc(100% + 6px);
    left: 50%;
    z-index: 10;
    padding: 4px 8px;
    font-size: 12px;
    font-weight: 400;
    color: #fff;
    white-space: nowrap;
    pointer-events: none;
    background: #2c2c2a;
    border-radius: 4px;
    opacity: 0;
    transform: translateX(-50%) translateY(4px);
    transition: all 0.2s;

    &::after {
      content: '';
      position: absolute;
      bottom: 100%;
      left: 50%;
      border: 4px solid transparent;
      border-bottom-color: #2c2c2a;
      transform: translateX(-50%);
    }
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: auto;
    font-size: 12px;
    color: var(--el-text-color-secondary, #909399);
  }

  &__legend {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 2px;

    &--used {
      background: linear-gradient(180deg, #63a8ee, #2270bd);
      border: 0.5px solid #1b5f9e;
      box-shadow: 0 1.5px 0 #1a5c9c;
    }

    &--free {
      background: linear-gradient(180deg, #eceadf, #f4f2ea);
      border: 0.5px solid #d3d1c7;
      box-shadow: inset 0 1px 2px rgb(0 0 0 / 10%);
    }
  }

  &__link {
    margin-left: auto;
  }

  &__empty {
    display: flex;
    flex-direction: column;
    gap: 10px;
    align-items: center;
    justify-content: center;
    padding: 18px 0;
    margin-bottom: 12px;
    border: 1px dashed var(--border-subtle, #e4e7ed);
    border-radius: 8px;
    text-align: center;
  }

  &__empty-text {
    font-size: 12px;
    color: var(--el-text-color-placeholder, #a8abb2);
  }
}

@keyframes stat-grid-card-rise {
  from {
    opacity: 0;
    transform: translateY(6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// 无障碍：用户偏好减少动效时关闭入场动画与悬停位移
@media (prefers-reduced-motion: reduce) {
  .stat-grid-card__cell {
    animation: none;
    opacity: 1;
  }

  .stat-grid-card,
  .stat-grid-card__cell {
    transition: none;
  }

  .stat-grid-card:hover,
  .stat-grid-card__cell:hover {
    transform: none;
  }
}
</style>
