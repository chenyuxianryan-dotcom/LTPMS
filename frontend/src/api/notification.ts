/** 通知模块 /notifications */
import { get, put } from '@/utils/request';
import type { INotification, IPageResult } from '@/types/api';

export function apiListNotifications(params: { page: number; size: number; isRead?: boolean }): Promise<IPageResult<INotification>> {
  return get<IPageResult<INotification>>('/notifications', params);
}

export function apiGetUnreadCount(): Promise<number> {
  return get<number>('/notifications/unread-count');
}

export function apiMarkNotificationRead(id: number): Promise<void> {
  return put(`/notifications/${id}/read`);
}

export function apiMarkAllNotificationsRead(): Promise<void> {
  return put('/notifications/read-all');
}
