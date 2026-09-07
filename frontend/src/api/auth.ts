/** 认证模块 /auth */
import { get, post } from '@/utils/request';
import type { ILoginResult, IUserInfo } from '@/types/api';

export function apiLogin(data: { username: string; password: string }): Promise<ILoginResult> {
  return post<ILoginResult>('/auth/login', data);
}

export function apiChangePassword(data: { oldPassword: string; newPassword: string }): Promise<{ success: boolean }> {
  return post<{ success: boolean }>('/auth/change-password', data);
}

export function apiGetMe(): Promise<IUserInfo> {
  return get<IUserInfo>('/auth/me');
}
