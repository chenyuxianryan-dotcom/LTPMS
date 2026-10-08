/**
 * Vitest 全局环境垫片：jsdom 未实现、但 Element Plus 与浏览器 API 会用到的成员。
 */
/**
 * Element Plus 的表格布局依赖尺寸回调：observe 时立即回报一个非零视口，
 * 否则 el-table 会因列宽全 0 而一行都不渲染。
 */
class ObserverStub {
  private readonly callback: ResizeObserverCallback;

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
  }

  observe(target: Element): void {
    const entry = {
      target,
      contentRect: { width: 1280, height: 640, top: 0, left: 0, bottom: 640, right: 1280 },
      borderBoxSize: [{ blockSize: 640, inlineSize: 1280 }],
      contentBoxSize: [{ blockSize: 640, inlineSize: 1280 }],
      devicePixelContentBoxSize: [],
    } as unknown as ResizeObserverEntry;
    this.callback([entry], this);
  }

  unobserve(): void {}

  disconnect(): void {}

  takeRecords(): [] {
    return [];
  }
}

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = ObserverStub as unknown as typeof ResizeObserver;
}

class NoopObserver {
  observe(): void {}

  unobserve(): void {}

  disconnect(): void {}

  takeRecords(): [] {
    return [];
  }
}

if (typeof globalThis.IntersectionObserver === 'undefined') {
  globalThis.IntersectionObserver = NoopObserver as unknown as typeof IntersectionObserver;
}

if (typeof window.matchMedia !== 'function') {
  window.matchMedia = ((query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    })) as unknown as typeof window.matchMedia;
}

if (typeof URL.createObjectURL !== 'function') {
  let seq = 0;
  URL.createObjectURL = (() => `blob:mock-${(seq += 1)}`) as typeof URL.createObjectURL;
  URL.revokeObjectURL = (() => {}) as typeof URL.revokeObjectURL;
}
