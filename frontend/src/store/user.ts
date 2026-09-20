/**
 * 用户状态管理
 * Token 存 sessionStorage（会话级，关闭浏览器即清除），避免敏感信息长期驻留本地存储。
 */
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { apiChangePassword, apiGetMe, apiLogin } from '@/api/auth';
import { TOKEN_KEY } from '@/utils/request';
import type { IUserInfo, TRole } from '@/types/api';

export const useUserStore = defineStore('user', () => {
  /** 解析 JWT exp（纯前端解码，不做签名校验，服务端仍是最终兜底） */
  function isTokenExpired(t: string): boolean {
    try {
      const payload = t.split('.')[1];
      if (!payload) return true;
      const b64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
      const data = JSON.parse(new TextDecoder().decode(bytes)) as { exp?: number };
      if (typeof data.exp !== 'number') return false;
      return Date.now() / 1000 >= data.exp;
    } catch {
      return false; // 解析失败不误杀，交给服务端 401 兜底
    }
  }

  /** 读取 token；已过期的直接清除（避免过期会话被守卫当成已登录） */
  function readToken(): string {
    const t = sessionStorage.getItem(TOKEN_KEY) ?? '';
    if (t && isTokenExpired(t)) {
      sessionStorage.removeItem(TOKEN_KEY);
      return '';
    }
    return t;
  }

  const token = ref<string>(readToken());
  const user = ref<IUserInfo | null>(null);

  const isLoggedIn = computed(() => Boolean(token.value) && !isTokenExpired(token.value));
  const role = computed<TRole | ''>(() => user.value?.role ?? '');
  const isAdmin = computed(() => role.value === 'ADMIN');
  const isStudent = computed(() => role.value === 'STUDENT');
  /** 角色首页：学生没有 dashboard 等旧业务权限，落地「我的任务」（r2-C1） */
  const homePath = computed(() => (role.value === 'STUDENT' ? '/my-tasks' : '/dashboard'));
  /** 首次登录（默认密码）强制改密 */
  const needChangePassword = computed(() => Boolean(user.value?.isDefaultPwd));

  async function login(username: string, password: string): Promise<IUserInfo> {
    const result = await apiLogin({ username, password });
    token.value = result.token;
    user.value = result.user;
    sessionStorage.setItem(TOKEN_KEY, result.token);
    return result.user;
  }

  async function fetchMe(): Promise<IUserInfo> {
    user.value = await apiGetMe();
    return user.value;
  }

  async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await apiChangePassword({ oldPassword, newPassword });
    if (user.value) {
      user.value.isDefaultPwd = false;
    }
  }

  function logout(): void {
    token.value = '';
    user.value = null;
    sessionStorage.removeItem(TOKEN_KEY);
  }

  return {
    token,
    user,
    isLoggedIn,
    role,
    isAdmin,
    isStudent,
    homePath,
    needChangePassword,
    login,
    fetchMe,
    changePassword,
    logout,
  };
});
