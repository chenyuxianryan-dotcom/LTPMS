import { ElMessage, ElMessageBox } from 'element-plus';
import { apiImportScheduleRows, apiPreviewScheduleRows, type IScheduleDraftRow } from '@/api/schedule';
import type { IScheduleImportResult } from '@/types/api';

/**
 * 草稿确认写回（课表页拍照弹窗与智能查询附图草稿卡共用，文案与口径单点维护）：
 * 预览 → 修订覆盖确认 → 落库；用户取消覆盖确认时返回 null。
 * 行级校验与归属规则全在服务端，前端校验通过不代表写入成功（部分成功口径）。
 */
export async function confirmScheduleDraft(
  semesterId: number,
  rows: IScheduleDraftRow[],
): Promise<IScheduleImportResult | null> {
  const preview = await apiPreviewScheduleRows(semesterId, rows);
  let confirmOverwrite = false;
  if (preview.revisionConflicts > 0) {
    const shown = preview.conflictCourses.slice(0, 5).join('、');
    const suffix = preview.conflictCourses.length > 5 ? ` 等 ${preview.conflictCourses.length} 门课程` : '';
    try {
      await ElMessageBox.confirm(
        `本次导入将覆盖${shown}${suffix}的已有修订记录（系统会留痕并通知管理员）。确定继续导入吗？`,
        '覆盖修订记录确认',
        { type: 'warning', confirmButtonText: '继续导入', cancelButtonText: '取消' },
      );
      confirmOverwrite = true;
    } catch {
      return null;
    }
  }
  const result = await apiImportScheduleRows(semesterId, rows, confirmOverwrite);
  const failed = result.failedCount ?? 0;
  if (failed > 0) {
    const first = result.errors?.length ? `，如：${result.errors[0].message}` : '';
    ElMessage.warning(`导入完成：成功 ${result.successCount} 条，失败 ${failed} 条${first}`);
  } else {
    ElMessage.success(`导入成功 ${result.successCount} 条`);
  }
  return result;
}
