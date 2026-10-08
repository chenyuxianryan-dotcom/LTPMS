import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { IScheduleDraftRow } from '@/api/schedule';
import type { IImportPreview, IScheduleImportResult } from '@/types/api';
import { confirmScheduleDraft } from './scheduleDraft';

const mocks = vi.hoisted(() => ({
  preview: vi.fn(),
  importRows: vi.fn(),
  confirm: vi.fn(),
  message: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}));

vi.mock('@/api/schedule', () => ({
  apiPreviewScheduleRows: mocks.preview,
  apiImportScheduleRows: mocks.importRows,
}));

vi.mock('element-plus', () => ({
  ElMessage: Object.assign((...args: unknown[]) => mocks.message(...args), {
    success: (...args: unknown[]) => mocks.success(...args),
    warning: (...args: unknown[]) => mocks.warning(...args),
    error: (...args: unknown[]) => mocks.error(...args),
  }),
  ElMessageBox: { confirm: (...args: unknown[]) => mocks.confirm(...args) },
}));

function draftRow(courseCode: string): IScheduleDraftRow {
  return {
    courseCode,
    courseName: '数据结构',
    totalHours: 48,
    teacherNo: 'T900',
    labCode: '36-401',
    classNames: '计科2301',
    studentCount: 30,
    rawScheduleText: '周一第1-2节',
  };
}

function previewOf(partial: Partial<IImportPreview>): IImportPreview {
  return {
    totalCount: 3,
    validCount: 3,
    errors: [],
    revisionConflicts: 0,
    conflictCourses: [],
    ...partial,
  };
}

const SEMESTER = 1;
const rows = [draftRow('CS101'), draftRow('CS102')];

beforeEach(() => {
  vi.clearAllMocks();
  mocks.preview.mockResolvedValue(previewOf({}));
  mocks.importRows.mockResolvedValue({ successCount: 2, failedCount: 0, errors: [] });
});

describe('confirmScheduleDraft —— 草稿确认写回', () => {
  it('无修订冲突时不弹确认，按 confirmOverwrite=false 落库并提示成功', async () => {
    const result: IScheduleImportResult = { successCount: 2, failedCount: 0, errors: [] };
    mocks.importRows.mockResolvedValue(result);

    const outcome = await confirmScheduleDraft(SEMESTER, rows);

    expect(mocks.confirm).not.toHaveBeenCalled();
    expect(mocks.importRows).toHaveBeenCalledWith(SEMESTER, rows, false);
    expect(mocks.success).toHaveBeenCalledWith('导入成功 2 条');
    expect(outcome).toBe(result);
  });

  it('有冲突且用户确认继续时按 confirmOverwrite=true 落库', async () => {
    mocks.preview.mockResolvedValue(previewOf({ revisionConflicts: 2, conflictCourses: ['CS101', 'CS102'] }));
    mocks.confirm.mockResolvedValue('confirm');

    await confirmScheduleDraft(SEMESTER, rows);

    expect(mocks.confirm).toHaveBeenCalledTimes(1);
    expect(mocks.importRows).toHaveBeenCalledWith(SEMESTER, rows, true);
  });

  it('用户取消覆盖确认时返回 null 且不落库', async () => {
    mocks.preview.mockResolvedValue(previewOf({ revisionConflicts: 1, conflictCourses: ['CS101'] }));
    mocks.confirm.mockRejectedValue(new Error('cancel'));

    const outcome = await confirmScheduleDraft(SEMESTER, rows);

    expect(outcome).toBeNull();
    expect(mocks.importRows).not.toHaveBeenCalled();
  });

  it('冲突课程超过 5 门时文案只列前 5 门并补「等 N 门课程」', async () => {
    const courses = ['A', 'B', 'C', 'D', 'E', 'F', 'G'];
    mocks.preview.mockResolvedValue(previewOf({ revisionConflicts: 7, conflictCourses: courses }));
    mocks.confirm.mockResolvedValue('confirm');

    await confirmScheduleDraft(SEMESTER, rows);

    const text = String(mocks.confirm.mock.calls[0][0]);
    expect(text).toContain('A、B、C、D、E');
    expect(text).not.toContain('F');
    expect(text).toContain('等 7 门课程');
  });

  it('冲突课程不足 5 门时不加「等 N 门课程」后缀', async () => {
    mocks.preview.mockResolvedValue(previewOf({ revisionConflicts: 2, conflictCourses: ['A', 'B'] }));
    mocks.confirm.mockResolvedValue('confirm');

    await confirmScheduleDraft(SEMESTER, rows);

    expect(String(mocks.confirm.mock.calls[0][0])).not.toContain('等');
  });

  it('部分失败时提示成功/失败条数并带出首条原因', async () => {
    mocks.importRows.mockResolvedValue({
      successCount: 1,
      failedCount: 1,
      errors: [{ rowNum: 2, message: '课程号不存在' }],
    });

    await confirmScheduleDraft(SEMESTER, rows);

    expect(mocks.warning).toHaveBeenCalledWith('导入完成：成功 1 条，失败 1 条，如：课程号不存在');
    expect(mocks.success).not.toHaveBeenCalled();
  });

  it('失败条目缺失时按全成功口径提示（服务端字段缺省兜底）', async () => {
    mocks.importRows.mockResolvedValue({ successCount: 2, errors: [] } as unknown as IScheduleImportResult);

    await confirmScheduleDraft(SEMESTER, rows);

    expect(mocks.success).toHaveBeenCalledWith('导入成功 2 条');
    expect(mocks.warning).not.toHaveBeenCalled();
  });

  it('失败但无错误明细时不拼「如：」', async () => {
    mocks.importRows.mockResolvedValue({ successCount: 0, failedCount: 2, errors: [] });

    await confirmScheduleDraft(SEMESTER, rows);

    expect(mocks.warning).toHaveBeenCalledWith('导入完成：成功 0 条，失败 2 条');
  });
});
