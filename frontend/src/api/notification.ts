/** 通知模块 /notifications + /notification-preferences */
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

/** 获取当前用户的通知偏好 */
export function apiGetNotificationPreferences(): Promise<Record<string, boolean>> {
  return get<Record<string, boolean>>('/notification-preferences');
}

/** 更新当前用户的通知偏好 */
export function apiUpdateNotificationPreferences(prefs: Record<string, boolean>): Promise<void> {
  return put('/notification-preferences', prefs);
}
