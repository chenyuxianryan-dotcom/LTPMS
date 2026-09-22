<template>
  <div v-if="mode !== 'none'" class="kb-src">
    <header class="kb-src__head">
      <span class="kb-src__lamp" aria-hidden="true" />
      <span class="kb-src__title">{{ mode === 'sources' ? '本校资料出处' : '非课程指定' }}</span>
      <span v-if="mode === 'sources'" class="kb-src__count">{{ blocks.length }} 段</span>
    </header>

    <p v-if="mode === 'generic'" class="kb-src__note">
      校内知识库里没查到相关段落，以下为通用知识，未引用本校资料。
    </p>

    <article v-for="(block, index) in blocks" :key="index" class="kb-src__item">
      <div class="kb-src__item-head">
        <span class="kb-src__badge" :class="`is-${block.source === '制度与流程' ? 'policy' : 'course'}`">
          {{ block.source }}
        </span>
        <span class="kb-src__doc">{{ block.title }}</span>
        <span v-if="block.heading" class="kb-src__heading">· {{ block.heading }}</span>
        <span v-if="block.courseName" class="kb-src__course">{{ block.courseName }}</span>
      </div>
      <p class="kb-src__content">{{ block.content }}</p>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { IAgentStep, IKnowledgeBlock, IKnowledgeToolResult } from '@/types/api';

/**
 * 知识库出处卡：把 knowledge_search 的 display.blocks 原文直接上屏（决策：出处可回溯、内容不转写）。
 * 三种形态——命中（列出段落与出处）/ 检索无果（挂「非课程指定」标签）/ 未涉及知识库（不渲染）。
 */
const props = defineProps<{ steps: IAgentStep[] }>();

const results = computed<IKnowledgeToolResult[]>(() =>
  props.steps
    .filter((step) => step.tool === 'knowledge_search')
    .map((step) => step.result as IKnowledgeToolResult | undefined)
    .filter((r): r is IKnowledgeToolResult => Boolean(r)),
);

const blocks = computed<IKnowledgeBlock[]>(() =>
  results.value.flatMap((r) => r.display?.blocks ?? []),
);

/** sources=有出处 / generic=查过但没命中（通用知识）/ none=本轮不涉及知识库 */
const mode = computed<'sources' | 'generic' | 'none'>(() => {
  if (blocks.value.length > 0) return 'sources';
  if (results.value.length > 0) return 'generic';
  return 'none';
});
</script>

<style scoped lang="scss">
/** 磷光绿出处卡：与 PersonDossier / ASY-01 终端同源色板 */
$phosphor-bg: #0c1f16;
$phosphor-border: #1e3a2b;
$phosphor-line: #2c4a38;
$phosphor-text: #6ee7a0;
$phosphor-dim: #3f8f66;
$phosphor-data: #d1fae5;

.kb-src {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: $phosphor-bg;
  border: 1px solid $phosphor-border;
  border-radius: var(--radius-xl);
}

.kb-src__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-2);
  font-family: var(--font-mono);
  font-size: 12px;
  color: $phosphor-text;
  border-bottom: 1px dashed $phosphor-border;
}

.kb-src__lamp {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  background: $phosphor-text;
  border-radius: var(--radius-full);
  box-shadow: 0 0 8px rgba(110, 231, 160, 0.8);
}

.kb-src__title {
  font-weight: 600;
  letter-spacing: 0.04em;
}

.kb-src__count {
  color: $phosphor-dim;
}

.kb-src__note {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: $phosphor-dim;
}

.kb-src__item {
  padding: var(--space-2) var(--space-3);
  background: rgba(110, 231, 160, 0.04);
  border: 1px solid $phosphor-border;
  border-radius: var(--radius-lg);
}

.kb-src__item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
  font-size: 12px;
}

.kb-src__badge {
  flex-shrink: 0;
  padding: 1px 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  border-radius: var(--radius-sm);

  &.is-policy {
    color: #fde68a;
    background: rgba(253, 230, 138, 0.12);
    border: 1px solid rgba(253, 230, 138, 0.4);
  }

  &.is-course {
    color: #93c5fd;
    background: rgba(147, 197, 253, 0.12);
    border: 1px solid rgba(147, 197, 253, 0.4);
  }
}

.kb-src__doc {
  color: $phosphor-data;
  font-weight: 600;
}

.kb-src__heading {
  color: $phosphor-text;
}

.kb-src__course {
  margin-left: auto;
  color: $phosphor-dim;
}

.kb-src__content {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: $phosphor-data;
  white-space: pre-wrap;
}
</style>
