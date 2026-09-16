/**
 * 书架 ↔ 教师课表页的「打开文件夹」过渡状态。
 * 轻量单例：书架点击方登记起始矩形与配色，FolderTransition 组件（挂在 BasicLayout）
 * 监听路由变化后演出动画；直接输入 URL 进入时无登记矩形，按普通切换处理。
 */
import { reactive } from 'vue';
import type { ICourseColor } from '@/utils/courseColor';

export interface IFolderRect {
  top: number;
  left: number;
  width: number;
  height: number;
}

export type TFolderMode = 'open' | 'close';

export interface IFolderContext {
  teacherId: number;
  rect: IFolderRect;
  color: ICourseColor;
  label: string;
}

export const folderTransition = reactive({
  active: false,
  mode: 'open' as TFolderMode,
  rect: null as IFolderRect | null,
  color: null as ICourseColor | null,
  label: '',
  /** 每次开播自增，组件据此重启动画计时 */
  runId: 0,
});

/** 待消费的「点击进入」上下文（区别于直接输入 URL） */
let pendingOpen: IFolderContext | null = null;

/** 最近一次打开的教师上下文，返回时用于反向收回目标书位置 */
const lastOpened = new Map<number, IFolderContext>();

/** 书架点击书本时调用（在 router.push 之前） */
export function markFolderOpen(ctx: IFolderContext): void {
  pendingOpen = ctx;
}

/** 取出并清空待播的打开上下文 */
export function consumeFolderOpen(teacherId: number): IFolderContext | null {
  const ctx = pendingOpen;
  pendingOpen = null;
  if (ctx && String(ctx.teacherId) === String(teacherId)) {
    return ctx;
  }
  return null;
}

/** 清除未消费的登记（导航到其它路由时） */
export function clearFolderOpen(): void {
  pendingOpen = null;
}

/** 返回书架时的收回上下文 */
export function findLastOpened(teacherId: number): IFolderContext | null {
  return lastOpened.get(Number(teacherId)) ?? null;
}

/** 播放一段过渡（open/close 共用） */
export function playFolderTransition(mode: TFolderMode, ctx: IFolderContext): void {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return;
  }
  if (mode === 'open') {
    lastOpened.set(Number(ctx.teacherId), ctx);
  }
  folderTransition.mode = mode;
  folderTransition.rect = ctx.rect;
  folderTransition.color = ctx.color;
  folderTransition.label = ctx.label;
  folderTransition.active = true;
  folderTransition.runId += 1;
}

export function finishFolderTransition(): void {
  folderTransition.active = false;
}
