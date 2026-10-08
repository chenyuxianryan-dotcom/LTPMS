import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { IUserInfo, TRole } from '@/types/api';

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  getMe: vi.fn(),
  changePassword: vi.fn(),
}));

vi.mock('@/api/auth', () => ({
  apiLogin: mocks.login,
  apiGetMe: mocks.getMe,
  apiChangePassword: mocks.changePassword,
}));

const { TOKEN_KEY } = await import('@/utils/request');
const { useUserStore } = await import('./user');

/** 纯前端解码路径只需 header.payload.sig 三段结构 */
function makeJwt(payload: Record<string, unknown>): string {
  const encode = (input: string) =>
    btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode('{"alg":"HS256"}')}.${encode(JSON.stringify(payload))}.sig`;
}

function userOf(partial: Partial<IUserInfo>): IUserInfo {
  return {
    id: 1,
    username: 'T900',
    role: 'TEACHER',
    realName: '张老师',
    isDefaultPwd: false,
    ...partial,
  } as IUserInfo;
}

const epochSeconds = () => Math.floor(Date.now() / 1000);

beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
  sessionStorage.clear();
});

describe('token 读取与过期判定', () => {
  it('未过期的 token 在初始化时被保留', () => {
    const token = makeJwt({ exp: epochSeconds() + 3600 });
    sessionStorage.setItem(TOKEN_KEY, token);

    const store = useUserStore();

    expect(store.token).toBe(token);
    expect(store.isLoggedIn).toBe(true);
  });

  it('已过期的 token 初始化即清除，避免会话守卫误判已登录', () => {
    sessionStorage.setItem(TOKEN_KEY, makeJwt({ exp: epochSeconds() - 10 }));

    const store = useUserStore();

    expect(store.token).toBe('');
    expect(store.isLoggedIn).toBe(false);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('无 exp 声明的 token 不误杀（交给服务端 401 兜底）', () => {
    const token = makeJwt({ sub: 'T900' });
    sessionStorage.setItem(TOKEN_KEY, token);

    expect(useUserStore().isLoggedIn).toBe(true);
  });

  it('payload 非法 Base64 时不误杀', () => {
    sessionStorage.setItem(TOKEN_KEY, 'header.@#$%.sig');

    expect(useUserStore().isLoggedIn).toBe(true);
  });

  it('payload 可解码但不是 JSON 时不误杀', () => {
    const encode = (input: string) =>
      btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    sessionStorage.setItem(TOKEN_KEY, `${encode('{}')}.${encode('not json')}.sig`);

    expect(useUserStore().isLoggedIn).toBe(true);
  });

  it('缺少 payload 段视为已过期', () => {
    sessionStorage.setItem(TOKEN_KEY, 'only-header');

    expect(useUserStore().isLoggedIn).toBe(false);
  });
});

describe('角色派生状态', () => {
  it.each([
    ['ADMIN', '/dashboard'],
    ['TEACHER', '/dashboard'],
    ['STUDENT', '/my-tasks'],
  ] as [TRole, string][])('%s 的角色首页是 %s', (role, expected) => {
    const store = useUserStore();
    store.user = userOf({ role });

    expect(store.role).toBe(role);
    expect(store.homePath).toBe(expected);
    expect(store.isAdmin).toBe(role === 'ADMIN');
    expect(store.isStudent).toBe(role === 'STUDENT');
  });

  it('未拉取用户信息时 role 为空串且不需要改密', () => {
    const store = useUserStore();

    expect(store.role).toBe('');
    expect(store.needChangePassword).toBe(false);
  });

  it('默认密码用户被要求强制改密', () => {
    const store = useUserStore();
    store.user = userOf({ isDefaultPwd: true });

    expect(store.needChangePassword).toBe(true);
  });
});

describe('登录 / 登出 / 改密', () => {
  it('登录成功后写入状态与 sessionStorage', async () => {
    const token = makeJwt({ exp: epochSeconds() + 3600 });
    mocks.login.mockResolvedValue({ token, user: userOf({}) });

    const store = useUserStore();
    const result = await store.login('T900', 'Ltpms@T900');

    expect(mocks.login).toHaveBeenCalledWith({ username: 'T900', password: 'Ltpms@T900' });
    expect(store.token).toBe(token);
    expect(store.user?.realName).toBe('张老师');
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe(token);
    expect(result.role).toBe('TEACHER');
  });

  it('fetchMe 回填用户信息', async () => {
    mocks.getMe.mockResolvedValue(userOf({ role: 'STUDENT' }));

    const store = useUserStore();
    const me = await store.fetchMe();

    expect(me.role).toBe('STUDENT');
    expect(store.isStudent).toBe(true);
  });

  it('改密成功后本地默认密码标记翻转', async () => {
    mocks.changePassword.mockResolvedValue(undefined);
    const store = useUserStore();
    store.user = userOf({ isDefaultPwd: true });

    await store.changePassword('old', 'Ltpms@New1');

    expect(mocks.changePassword).toHaveBeenCalledWith({ oldPassword: 'old', newPassword: 'Ltpms@New1' });
    expect(store.user?.isDefaultPwd).toBe(false);
    expect(store.needChangePassword).toBe(false);
  });

  it('未登录时改密不触碰用户对象', async () => {
    mocks.changePassword.mockResolvedValue(undefined);

    await expect(useUserStore().changePassword('old', 'new')).resolves.toBeUndefined();
  });

  it('登出清空状态与会话存储', () => {
    const store = useUserStore();
    store.token = makeJwt({ exp: epochSeconds() + 3600 });
    store.user = userOf({});

    store.logout();

    expect(store.token).toBe('');
    expect(store.user).toBeNull();
    expect(store.isLoggedIn).toBe(false);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
