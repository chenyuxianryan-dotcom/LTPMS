/**
 * 无声 LED 走位：机台皮肤页面（课程管理 / 报表中心）的装饰灯带。
 * 与 drumKit 音效引擎无关 —— 这些页面不引入任何声音，只在交互时走灯。
 */
import { computed, ref, type ComputedRef } from 'vue';

export const unitLedTick = ref(0);

/** 推进一格灯带（搜索 / 切换学期 / 点击行 / 开弹窗等交互时调用） */
export function tickUnitLed(): void {
  unitLedTick.value += 1;
}

/** 当前灯位（0 起，尚未触发为 -1），供 16 步灯带点亮 */
export function unitLedStep(dots = 16): ComputedRef<number> {
  return computed(() => (unitLedTick.value > 0 ? (unitLedTick.value - 1) % dots : -1));
}
