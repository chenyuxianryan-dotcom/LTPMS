/** 用户头像模块 /users（教师本人或管理员） */
import { put, upload } from '@/utils/request';

/** 设置内置默认头像（preset:avatar-XX） */
export function apiSetAvatar(id: number, avatar: string): Promise<{ avatar: string }> {
  return put<{ avatar: string }>(`/users/${id}/avatar`, { avatar });
}

/** 上传本地照片作为头像，返回头像 URL */
export function apiUploadAvatar(id: number, formData: FormData): Promise<{ avatar: string }> {
  return upload<{ avatar: string }>(`/users/${id}/avatar/upload`, formData);
}
