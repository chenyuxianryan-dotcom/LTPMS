/** 统计概览模块 /dashboard */
import { get } from '@/utils/request';
import type { IAdminOverview, ITeacherOverview } from '@/types/api';

export function apiGetAdminOverview(): Promise<IAdminOverview> {
  return get<IAdminOverview>('/dashboard/admin');
}

export function apiGetTeacherOverview(): Promise<ITeacherOverview> {
  return get<ITeacherOverview>('/dashboard/teacher');
}
