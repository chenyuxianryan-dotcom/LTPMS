/**
 * 附图客户端预检（智能查询终端与课表拍照导入共用）：
 * 只做格式与体积的即时拦截，真实类型以服务端魔数校验为准（扩展名不可信）。
 */
export const IMAGE_ACCEPT = '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp';
export const IMAGE_MAX_BYTES = 10 * 1024 * 1024;

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp'];

export interface IAttachedImage {
  name: string;
  size: number;
  /** 不含 data: 前缀的 base64 */
  base64: string;
  /** 本地预览地址（objectURL）；未开启预览时为空串 */
  previewUrl: string;
}

/** 读取图片文件 → base64；格式/体积不符抛出可直接上屏的中文原因 */
export async function readImageFile(file: File, options?: { preview?: boolean }): Promise<IAttachedImage> {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  if (!ALLOWED_TYPES.includes(file.type) && !ALLOWED_EXT.includes(ext)) {
    throw new Error(`仅支持 jpg/png/webp，当前文件 ${file.name}`);
  }
  if (file.size > IMAGE_MAX_BYTES) {
    throw new Error(`图片不能超过 10MB（当前 ${(file.size / 1024 / 1024).toFixed(1)}MB），请压缩后重试`);
  }
  const base64 = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result ?? '');
      resolve(result.split(',')[1] ?? result);
    };
    reader.onerror = () => reject(new Error('图片无法读取，请重新拍摄或导出'));
    reader.readAsDataURL(file);
  });
  return {
    name: file.name,
    size: file.size,
    base64,
    previewUrl: options?.preview ? URL.createObjectURL(file) : '',
  };
}

/** 释放预览地址（移除/替换图片时调用，避免 objectURL 泄漏） */
export function releaseImage(image: IAttachedImage | null): void {
  if (image?.previewUrl) {
    URL.revokeObjectURL(image.previewUrl);
  }
}

/** 附件体积上屏文案 */
export function formatImageSize(size: number): string {
  return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)}MB` : `${Math.max(1, Math.round(size / 1024))}KB`;
}
