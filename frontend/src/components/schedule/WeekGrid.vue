<template>
  <div v-loading="loading" class="week-grid">
    <div class="week-grid__head">
      <div class="week-grid__corner">节次</div>
      <div
        v-for="(label, index) in WEEKDAY_LABELS"
        :key="label"
        class="week-grid__head-cell"
        :class="{ 'is-weekend': index >= 5 }"
      >
        周{{ label }}
      </div>
    </div>

    <div class="week-grid__body">
      <div class="week-grid__sections">
        <div v-for="section in SECTION_OPTIONS" :key="section" class="week-grid__section-label">
          第{{ section }}节
        </div>
      </div>

      <div
        v-for="(label, index) in WEEKDAY_LABELS"
        :key="label"
        class="week-grid__day"
        :class="{ 'is-weekend': index >= 5 }"
      >
        <div v-for="section in SECTION_OPTIONS" :key="section" class="week-grid__cell-line" />
        <div v-if="cardsOfDay(index + 1).length === 0" class="week-grid__day-empty">本日无课</div>
        <button
          v-for="card in cardsOfDay(index + 1)"
          :key="card.key"
          type="button"
          class="week-card"
          :class="{ 'week-card--tall': card.slot.endSection - card.slot.startSection >= 1 }"
          :style="cardStyle(card)"
          @click="emit('select', card.item)"
        >
          <span class="week-card__name" :title="card.item.courseName">{{ card.item.courseName }}</span>
          <span class="week-card__meta">第{{ card.slot.startSection }}-{{ card.slot.endSection }}节</span>
          <span class="week-card__meta week-card__meta--lab" :title="labText(card)">
            {{ labText(card) }}
          </span>
          <span v-if="card.slot.endSection - card.slot.startSection >= 1" class="week-card__meta week-card__meta--class" :title="card.item.classNames">
            {{ card.item.classNames }}
          </span>
          <span v-if="card.slot.weekType !== 'ALL'" class="week-card__badge">{{ WEEK_TYPE_MAP[card.slot.weekType] }}</span>
          <span v-if="card.item.revisionCount > 0" class="week-card__revised" title="该课程有修订记录">
            <el-icon :size="11"><EditPen /></el-icon>
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { EditPen } from '@element-plus/icons-vue';
import { SECTION_OPTIONS, WEEKDAY_LABELS, WEEK_TYPE_MAP } from '@/constants';
import { courseColor } from '@/utils/courseColor';
import type { IMyScheduleItem, IScheduleSlot } from '@/types/api';

const props = defineProps<{
  items: IMyScheduleItem[];
  loading?: boolean;
}>();

const emit = defineEmits<{ (e: 'select', item: IMyScheduleItem): void }>();

const ROW_HEIGHT = 64;
const CARD_GAP = 4;

interface IWeekCard {
  key: string;
  item: IMyScheduleItem;
  slot: IScheduleSlot;
  top: number;
  height: number;
  left: string;
  width: string;
}

/** 逐日把时间槽展开成定位卡片：重叠时段按簇左右分栏，互不遮挡 */
const cardsByDay = computed<Map<number, IWeekCard[]>>(() => {
  const rawByDay = new Map<number, { item: IMyScheduleItem; slot: IScheduleSlot }[]>();
  for (const item of props.items) {
    for (const slot of item.slots) {
      const list = rawByDay.get(slot.dayOfWeek) ?? [];
      list.push({ item, slot });
      rawByDay.set(slot.dayOfWeek, list);
    }
  }

  const result = new Map<number, IWeekCard[]>();
  for (const [day, entries] of rawByDay) {
    entries.sort(
      (a, b) => a.slot.startSection - b.slot.startSection || a.slot.endSection - b.slot.endSection,
    );
    const clusters: { entries: typeof entries; end: number }[] = [];
    for (const entry of entries) {
      const last = clusters[clusters.length - 1];
      if (last && entry.slot.startSection <= last.end) {
        last.entries.push(entry);
        last.end = Math.max(last.end, entry.slot.endSection);
      } else {
        clusters.push({ entries: [entry], end: entry.slot.endSection });
      }
    }
    const cards: IWeekCard[] = [];
    for (const cluster of clusters) {
      const count = cluster.entries.length;
      cluster.entries.forEach((entry, index) => {
        const top = (entry.slot.startSection - 1) * ROW_HEIGHT;
        const height = (entry.slot.endSection - entry.slot.startSection + 1) * ROW_HEIGHT - CARD_GAP;
        const left = count === 1 ? '3px' : `calc(${(index * 100) / count}% + 2px)`;
        const width = count === 1 ? 'calc(100% - 6px)' : `calc(${100 / count}% - 4px)`;
        cards.push({
          key: `${entry.item.id}-${entry.slot.dayOfWeek}-${entry.slot.startSection}`,
          item: entry.item,
          slot: entry.slot,
          top,
          height,
          left,
          width,
        });
      });
    }
    result.set(day, cards);
  }
  return result;
});

function cardsOfDay(day: number): IWeekCard[] {
  return cardsByDay.value.get(day) ?? [];
}

function cardStyle(card: IWeekCard): Record<string, string> {
  const color = courseColor(card.item.courseCode);
  return {
    top: `${card.top + 2}px`,
    height: `${card.height - 2}px`,
    left: card.left,
    width: card.width,
    background: color.bg,
    color: color.fg,
    borderColor: color.border,
  };
}

function labText(card: IWeekCard): string {
  return card.item.labName || card.item.labCode;
}
</script>

<style scoped lang="scss">
.week-grid {
  --week-row-height: 64px;
  width: 100%;
  min-width: 720px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--bg-elevated);

  &__head {
    display: grid;
    grid-template-columns: 64px repeat(7, minmax(90px, 1fr));
    border-bottom: 1px solid var(--border-default);
  }

  &__corner,
  &__head-cell {
    padding: var(--space-2) var(--space-2);
    text-align: center;
    font-size: 13px;
    font-weight: 600;
    color: var(--text-secondary);
    background: var(--bg-subtle);
  }

  &__corner {
    border-right: 1px solid var(--border-default);
    font-weight: 500;
    color: var(--text-tertiary);
  }

  &__head-cell + &__head-cell {
    border-left: 1px solid var(--border-subtle);
  }

  &__head-cell.is-weekend {
    color: var(--text-tertiary);
  }

  &__body {
    display: grid;
    grid-template-columns: 64px repeat(7, minmax(90px, 1fr));
  }

  &__sections {
    border-right: 1px solid var(--border-default);
  }

  &__section-label {
    height: var(--week-row-height);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: var(--text-tertiary);
    background: var(--bg-subtle);
    border-bottom: 1px solid var(--border-subtle);

    &:last-child {
      border-bottom: none;
    }
  }

  &__day {
    position: relative;
    height: calc(var(--week-row-height) * 12);

    & + & {
      border-left: 1px solid var(--border-subtle);
    }

    &.is-weekend {
      background: var(--bg-subtle);
    }
  }

  &__cell-line {
    height: var(--week-row-height);
    border-bottom: 1px solid var(--border-subtle);

    &:last-child {
      border-bottom: none;
    }
  }

  &__day-empty {
    position: absolute;
    top: 50%;
    left: 0;
    right: 0;
    transform: translateY(-50%);
    text-align: center;
    font-size: 12px;
    color: var(--text-quaternary);
    pointer-events: none;
    user-select: none;
  }
}

.week-card {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 5px 7px;
  border: 1px solid;
  border-radius: var(--radius-md);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  font-family: inherit;
  transition: box-shadow var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);

  &:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-1px);
  }

  &__name {
    font-size: 12px;
    font-weight: 600;
    line-height: 16px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__meta {
    font-size: 11px;
    line-height: 14px;
    opacity: 0.85;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    &--class {
      opacity: 0.72;
    }
  }

  &__badge {
    position: absolute;
    top: 3px;
    right: 3px;
    padding: 0 4px;
    font-size: 10px;
    line-height: 14px;
    border-radius: var(--radius-full);
    background: var(--bg-elevated);
    border: 1px solid currentcolor;
    opacity: 0.9;
  }

  &__revised {
    position: absolute;
    bottom: 3px;
    right: 4px;
    display: inline-flex;
    opacity: 0.75;
  }
}
</style>
