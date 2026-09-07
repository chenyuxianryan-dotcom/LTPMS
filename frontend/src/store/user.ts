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
  const token = ref<string>(sessionStorage.getItem(TOKEN_KEY) ?? '');
  const user = ref<IUserInfo | null>(null);

  const isLoggedIn = computed(() => Boolean(token.value));
  const role = computed<TRole | ''>(() => user.value?.role ?? '');
  const isAdmin = computed(() => role.value === 'ADMIN');
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

  return { token, user, isLoggedIn, role, isAdmin, needChangePassword, login, fetchMe, changePassword, logout };
});
