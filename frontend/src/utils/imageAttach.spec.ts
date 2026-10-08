import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  IMAGE_ACCEPT,
  IMAGE_MAX_BYTES,
  formatImageSize,
  readImageFile,
  releaseImage,
  type IAttachedImage,
} from './imageAttach';

/** size 需要精确控制的场景直接改写描述符，避免真造 10MB 内容 */
function makeFile(name: string, type: string, size?: number): File {
  const file = new File(['abc'], name, { type });
  if (size !== undefined) {
    Object.defineProperty(file, 'size', { value: size });
  }
  return file;
}

describe('readImageFile —— 附图客户端预检', () => {
  it('合法 jpg 返回不含 data: 前缀的 base64', async () => {
    const image = await readImageFile(makeFile('a.jpg', 'image/jpeg'));

    expect(image.name).toBe('a.jpg');
    expect(image.base64).toBe(btoa('abc'));
    expect(image.base64).not.toMatch(/^data:/);
    expect(image.previewUrl).toBe('');
  });

  it('开启预览时回填 objectURL', async () => {
    const image = await readImageFile(makeFile('a.png', 'image/png'), { preview: true });

    expect(image.previewUrl).not.toBe('');
  });

  it('MIME 不被识别但扩展名合规时仍放行（真实类型由服务端魔数兜底）', async () => {
    const image = await readImageFile(makeFile('shot.webp', 'application/octet-stream'));

    expect(image.name).toBe('shot.webp');
  });

  it('类型与扩展名都不合规时抛可直接上屏的中文原因', async () => {
    await expect(readImageFile(makeFile('notes.txt', 'text/plain'))).rejects.toThrow(
      '仅支持 jpg/png/webp，当前文件 notes.txt',
    );
  });

  it('超过 10MB 拦截并回显当前体积', async () => {
    await expect(
      readImageFile(makeFile('big.jpg', 'image/jpeg', IMAGE_MAX_BYTES + 1)),
    ).rejects.toThrow(/图片不能超过 10MB（当前 \d+\.\dMB）/);
  });

  it('恰好 10MB 属于允许范围（边界）', async () => {
    const image = await readImageFile(makeFile('edge.jpg', 'image/jpeg', IMAGE_MAX_BYTES));

    expect(image.size).toBe(IMAGE_MAX_BYTES);
  });

  it('FileReader 失败时给出重拍建议而非静默', async () => {
    class FailingReader {
      onload: (() => void) | null = null;

      onerror: (() => void) | null = null;

      readAsDataURL() {
        setTimeout(() => this.onerror?.(), 0);
      }
    }
    vi.stubGlobal('FileReader', FailingReader);

    await expect(readImageFile(makeFile('a.jpg', 'image/jpeg'))).rejects.toThrow(
      '图片无法读取，请重新拍摄或导出',
    );

    vi.unstubAllGlobals();
  });
});

describe('releaseImage —— objectURL 泄漏防护', () => {
  const revoke = (url: string) => URL.revokeObjectURL(url);
  let spy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    spy = vi.spyOn(URL, 'revokeObjectURL');
  });

  afterEach(() => {
    spy.mockRestore();
  });

  it('null 附件不调用 revoke', () => {
    releaseImage(null);

    expect(spy).not.toHaveBeenCalled();
  });

  it('未开启预览（previewUrl 空串）不调用 revoke', () => {
    const image: IAttachedImage = { name: 'a.jpg', size: 1, base64: 'x', previewUrl: '' };

    releaseImage(image);

    expect(spy).not.toHaveBeenCalled();
  });

  it('有预览地址时精确 revoke 该地址', () => {
    const image: IAttachedImage = { name: 'a.jpg', size: 1, base64: 'x', previewUrl: 'blob:mock-7' };

    releaseImage(image);

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith('blob:mock-7');
    revoke('blob:mock-7');
  });
});

describe('formatImageSize —— 附件体积文案', () => {
  it('≥1MB 用 MB 保留一位小数', () => {
    expect(formatImageSize(2 * 1024 * 1024)).toBe('2.0MB');
    expect(formatImageSize(1024 * 1024)).toBe('1.0MB');
    expect(formatImageSize(1024 * 1024 - 1)).toBe('1024KB');
  });

  it('<1MB 用 KB，且零字节也至少显示 1KB', () => {
    expect(formatImageSize(100 * 1024)).toBe('100KB');
    expect(formatImageSize(500)).toBe('1KB');
    expect(formatImageSize(0)).toBe('1KB');
  });
});

describe('IMAGE_ACCEPT —— 上传入口约束', () => {
  it('三种格式同时出现在扩展名与 MIME 两种写法里', () => {
    expect(IMAGE_ACCEPT).toContain('.jpg');
    expect(IMAGE_ACCEPT).toContain('.png');
    expect(IMAGE_ACCEPT).toContain('.webp');
    expect(IMAGE_ACCEPT).toContain('image/jpeg');
  });
});
