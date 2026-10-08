import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
// jsdom 的 Blob 没有 .text()，下载守卫会调用它；用 node 实现才能贴近真实浏览器行为
import { Blob as NodeBlob } from 'node:buffer';
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';

const mocks = vi.hoisted(() => ({
  message: vi.fn(),
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}));

vi.mock('element-plus', () => ({
  ElMessage: Object.assign((...args: unknown[]) => mocks.message(...args), {
    error: (...args: unknown[]) => mocks.error(...args),
    success: (...args: unknown[]) => mocks.success(...args),
    warning: (...args: unknown[]) => mocks.warning(...args),
  }),
}));

const { default: request, del, download, get, patch, saveBlob, TOKEN_KEY, upload } = await import('./request');

let lastConfig: InternalAxiosRequestConfig | null = null;

function installAdapter(handler: () => { status: number; data: unknown }) {
  request.defaults.adapter = async (config) => {
    lastConfig = config;
    const { status, data } = handler();
    const response: AxiosResponse = {
      data,
      status,
      statusText: '',
      headers: new AxiosHeaders(),
      config,
      request: {},
    };
    if (status >= 200 && status < 300) return response;
    throw new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, {}, response);
  };
}

function bizBody(code: number, message: string, data: unknown = null) {
  return { status: 200, data: { code, message, data } };
}

/**
 * 401 兜底写的是 window.location.href，jsdom 真实跳转只会刷 "Not implemented: navigation"，
 * 这里换成可记录的假 location，顺便让跳转地址可断言。
 */
const navigations: string[] = [];

beforeAll(() => {
  let href = 'http://localhost/';
  const fakeLocation = {
    get href() {
      return href;
    },
    set href(url: string) {
      navigations.push(url);
      href = url;
    },
    assign: (url: string) => {
      navigations.push(url);
      href = url;
    },
    reload: () => undefined,
  };
  Object.defineProperty(window, 'location', { configurable: true, get: () => fakeLocation });
});

const run = <T>(promise: Promise<T>) => promise.then((value) => ({ ok: true, value })).catch((err) => ({ ok: false, err }));

beforeEach(() => {
  vi.clearAllMocks();
  lastConfig = null;
  navigations.length = 0;
  sessionStorage.clear();
});

describe('请求拦截器', () => {
  it('有会话 token 时自动附带 Bearer', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'tok-123');
    installAdapter(() => bizBody(200, 'ok', {}));

    await get('/courses');

    expect(lastConfig?.headers.Authorization).toBe('Bearer tok-123');
  });

  it('无 token 时不附带 Authorization', async () => {
    installAdapter(() => bizBody(200, 'ok', {}));

    await get('/courses');

    expect(lastConfig?.headers.Authorization).toBeUndefined();
  });

  it('upload 走 multipart 内容类型', async () => {
    installAdapter(() => bizBody(200, 'ok', {}));

    await upload('/kb/documents', new FormData());

    expect(lastConfig?.headers['Content-Type']).toBe('multipart/form-data');
  });

  it('del 把参数放进 params', async () => {
    installAdapter(() => bizBody(200, 'ok', null));

    await del('/schedules', { semesterId: 1 });

    expect(lastConfig?.params).toEqual({ semesterId: 1 });
  });
});

describe('响应拦截器 —— 统一解包与业务错误', () => {
  it('业务码 200 时只把 data 交给调用方', async () => {
    installAdapter(() => bizBody(200, 'ok', { id: 7, courseName: '数据结构' }));

    await expect(get('/courses/7')).resolves.toEqual({ id: 7, courseName: '数据结构' });
    expect(mocks.error).not.toHaveBeenCalled();
  });

  it('业务失败时按 [code] message 统一提示并抛出带 code 的错误', async () => {
    installAdapter(() => bizBody(403, '无权访问该课程'));

    const out = await run(patch('/courses/1', {}));

    expect(out.ok).toBe(false);
    expect((out as { err: { code: number } }).err.code).toBe(403);
    expect(mocks.error).toHaveBeenCalledWith('[403] 无权访问该课程');
  });

  it('46001 同名数据集冲突留给调用方二选一，不做统一提示', async () => {
    installAdapter(() => bizBody(46001, '同名数据集已存在'));

    const out = await run(get('/datasets'));

    expect((out as { err: { code: number } }).err.code).toBe(46001);
    expect(mocks.error).not.toHaveBeenCalled();
  });

  it('业务码 401（HTTP 200）清会话并提示重新登录', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'tok-123');
    installAdapter(() => bizBody(401, 'token 已过期'));

    const out = await run(get('/auth/me'));

    expect((out as { err: { code: number } }).err.code).toBe(401);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(mocks.message).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'error', message: '登录已过期，请重新登录' }),
    );
    // 会话失效后必须把人送回落地页，否则页面停在原地只会反复撞 401
    expect(navigations).toEqual(['/login']);
  });

  it('blob 响应不被解包，整体透传给下载逻辑', async () => {
    const blob = new NodeBlob([new Uint8Array(5000)], { type: 'application/octet-stream' });
    installAdapter(() => ({ status: 200, data: blob }));

    const out = await download('/reports/export', { semesterId: 1 });

    expect(out).toBe(blob);
  });
});

describe('响应拦截器 —— HTTP 层错误', () => {
  it('HTTP 500 取 body message 提示', async () => {
    installAdapter(() => ({ status: 500, data: { message: '数据库连接失败' } }));

    const out = await run(get('/semesters'));

    expect((out as { err: { code: number } }).err.code).toBe(500);
    expect(mocks.error).toHaveBeenCalledWith('[500] 数据库连接失败');
  });

  it('网络层无响应时兜底 500 与 error.message', async () => {
    request.defaults.adapter = async (config) => {
      throw new AxiosError('Network Error', 'ERR_NETWORK', config, {});
    };

    const out = await run(get('/semesters'));

    expect((out as { err: { code: number; message: string } }).err.code).toBe(500);
    expect(mocks.error).toHaveBeenCalledWith('[500] Network Error');
  });
});

describe('guardDownloadBlob —— 坏文件防线', () => {
  it('后端把业务失败伪装成 200 + JSON body 时按业务错误抛出', async () => {
    const body = JSON.stringify({ code: 45001, message: '该导出功能未实现', data: null });
    installAdapter(() => ({
      status: 200,
      data: new NodeBlob([body], { type: 'application/json' }),
    }));

    const out = await run(download('/reports/pdf', { courseId: 1 }));

    expect((out as { err: { code: number } }).err.code).toBe(45001);
    expect(mocks.error).toHaveBeenCalledWith('[45001] 该导出功能未实现');
  });

  it('小体积但内容不是 JSON 的响应仍原样返回（避免误杀正常文件）', async () => {
    const blob = new NodeBlob(['%PDF-1.4 header'], { type: '' });
    installAdapter(() => ({ status: 200, data: blob }));

    await expect(download('/reports/pdf', { courseId: 1 })).resolves.toBe(blob);
    expect(mocks.error).not.toHaveBeenCalled();
  });
});

describe('saveBlob —— 触发浏览器保存', () => {
  it('创建隐藏 a 标签下载并回收 objectURL', () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const revoke = vi.spyOn(URL, 'revokeObjectURL');

    saveBlob(new Blob(['x'], { type: 'text/plain' }), '成绩册.xlsx');

    expect(click).toHaveBeenCalledTimes(1);
    expect(revoke).toHaveBeenCalledTimes(1);

    click.mockRestore();
    revoke.mockRestore();
  });
});
