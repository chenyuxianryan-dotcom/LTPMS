/**
 * 头像工具：内置默认头像（preset）解析 + 头像 URL 解析
 * - avatar 字段三种取值：
 *   1. "preset:avatar-XX" → 前端内置图片（随前端打包，零后端流量）
 *   2. "/api/v1/files/avatar/xxx.png" → 后端上传文件
 *   3. null/未设置 → 按用户 ID 确定性分配一个内置默认头像（稳定、可辨别身份）
 */
import { fileURLToPath, URL } from 'node:url';

type IModuleMap = Record<string, string>;

const presetModules = import.meta.glob('../assets/avatars/avatar-*.png', {
  eager: true,
  import: 'default',
}) as IModuleMap;

/** 内置默认头像列表（按编号升序），id 与后端白名单一致 */
export const AVATAR_PRESETS: { id: string; url: string }[] = Object.entries(presetModules)
  .map(([path, url]) => {
    const match = path.match(/avatar-(\d+)\.png$/);
    const num = match ? Number(match[1]) : 0;
    const id = `preset:avatar-${String(num).padStart(2, '0')}`;
    return { id, num, url };
  })
  .sort((a, b) => a.num - b.num)
  .map(({ id, url }) => ({ id, url }));

const presetUrlMap = new Map(AVATAR_PRESETS.map((p) => [p.id, p.url]));

/**
 * 解析头像展示 URL。
 * @param userId 用户 ID（用于未设置头像时的确定性默认分配）
 * @param avatar 头像标识（preset:avatar-XX 或上传 URL），可为空
 */
export function resolveAvatarUrl(userId: number, avatar?: string | null): string {
  if (avatar && avatar.startsWith('preset:')) {
    return presetUrlMap.get(avatar) ?? defaultPresetUrl(userId);
  }
  if (avatar && avatar.startsWith('/api/v1/files/')) {
    return avatar;
  }
  return defaultPresetUrl(userId);
}

/** 未设置头像时：按用户 ID 取模，稳定分配一个内置默认头像（同名教师每次登录结果一致，便于辨别身份） */
function defaultPresetUrl(userId: number): string {
  const index = Math.abs(Number(userId) || 0) % AVATAR_PRESETS.length;
  return AVATAR_PRESETS[index]?.url ?? '';
}
