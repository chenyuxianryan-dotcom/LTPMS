/** 课题项目模块 /projects /tasks /datasets /students */
import { del, download, get, post, put, upload } from '@/utils/request';
import type {
  IDataset,
  IDatasetPreview,
  IDatasetVersion,
  IExperiment,
  IMyTaskGroup,
  IProject,
  IProjectMember,
  IProjectStats,
  IProjectTask,
  IRevisionView,
  IStudent,
  IStudentImportResult,
  ITaskSubmission,
} from '@/types/api';

/* ------------------------------------------------------------------ 课题 */

/** source=SYSTEM 课程课题（容器）/ USER 独立课题；缺省返回全部可见 */
export function apiListProjects(source?: 'SYSTEM' | 'USER'): Promise<IProject[]> {
  return get<IProject[]>('/projects', source ? { source } : undefined);
}

export function apiCreateProject(body: { projectName: string; description?: string }): Promise<IProject> {
  return post<IProject>('/projects', body);
}

export function apiGetProject(id: number): Promise<IProject> {
  return get<IProject>(`/projects/${id}`);
}

export function apiUpdateProject(
  id: number,
  body: { projectName?: string; description?: string },
): Promise<IProject> {
  return put<IProject>(`/projects/${id}`, body);
}

/** 课程容器懒生成（R1：幂等 GET-or-create，命中已有直接返回；生成即快照课程名单） */
export function apiEnsureCourseContainer(courseId: number): Promise<IProject> {
  return post<IProject>('/projects/ensure-course-container', { courseId });
}

/** 批量 ensure 本人当前学期课程容器（ADMIN 返回空列表，不被动建容器） */
export function apiEnsureMyCourseContainers(): Promise<IProject[]> {
  return post<IProject[]>('/projects/ensure-my-course-containers');
}

/** 归档定格（S7）：SYSTEM 容器禁删但可归档，归档后全写接口 403 */
export function apiArchiveProject(id: number): Promise<IProject> {
  return post<IProject>(`/projects/${id}/archive`);
}

/** 删除课题：SYSTEM 容器 403；独立课题仅空壳可删 */
export function apiDeleteProject(id: number): Promise<void> {
  return del<void>(`/projects/${id}`);
}

/** 容器实验目录（学生可读：容器课程维度的 experiment 行，替代全局 /experiments） */
export function apiContainerExperiments(projectId: number): Promise<IExperiment[]> {
  return get<IExperiment[]>(`/projects/${projectId}/experiments`);
}

/* ------------------------------------------------------------------ 成员 */

export function apiListMembers(projectId: number): Promise<IProjectMember[]> {
  return get<IProjectMember[]>(`/projects/${projectId}/members`);
}

export function apiAddMembers(projectId: number, studentIds: number[]): Promise<number> {
  return post<number>(`/projects/${projectId}/members`, { studentIds });
}

/** 项目内导入名单：陌生学号自动建档并带入（source=CREATE） */
export function apiImportMembers(projectId: number, file: File): Promise<IStudentImportResult> {
  const fd = new FormData();
  fd.append('file', file);
  return upload<IStudentImportResult>(`/projects/${projectId}/members/import`, fd);
}

export function apiRemoveMember(projectId: number, studentId: number): Promise<void> {
  return del<void>(`/projects/${projectId}/members/${studentId}`);
}

/* ------------------------------------------------------------------ 任务 */

export function apiListTasks(projectId: number): Promise<IProjectTask[]> {
  return get<IProjectTask[]>(`/projects/${projectId}/tasks`);
}

/** 派发任务：assigneeStudentIds>1 时服务端复制多条 */
export function apiCreateTasks(
  projectId: number,
  body: {
    title: string;
    description?: string;
    assigneeStudentIds: number[];
    deadline?: string | null;
    datasetId?: number | null;
  },
): Promise<IProjectTask[]> {
  return post<IProjectTask[]>(`/projects/${projectId}/tasks`, body);
}

export function apiUpdateTask(
  taskId: number,
  body: { title?: string; description?: string; deadline?: string | null; datasetId?: number | null },
): Promise<IProjectTask> {
  return put<IProjectTask>(`/tasks/${taskId}`, body);
}

/** 规则①：已提交/已验收 400；被驳回 → 关闭；其余软删 */
export function apiDeleteTask(taskId: number): Promise<void> {
  return del<void>(`/tasks/${taskId}`);
}

export function apiStartTask(taskId: number): Promise<IProjectTask> {
  return post<IProjectTask>(`/tasks/${taskId}/start`);
}

export function apiSubmitTask(taskId: number, body: { content: string; datasetVersionId?: number | null }): Promise<ITaskSubmission> {
  return post<ITaskSubmission>(`/tasks/${taskId}/submit`, body);
}

export function apiReviewTask(taskId: number, body: { decision: 'ACCEPT' | 'REJECT'; comment?: string }): Promise<IProjectTask> {
  return post<IProjectTask>(`/tasks/${taskId}/review`, body);
}

/** 提交记录（半透明 Q6：仅本人学生/归属教师/ADMIN） */
export function apiListSubmissions(taskId: number): Promise<ITaskSubmission[]> {
  return get<ITaskSubmission[]>(`/tasks/${taskId}/submissions`);
}

export function apiListTaskRevisions(taskId: number): Promise<IRevisionView[]> {
  return get<IRevisionView[]>(`/tasks/${taskId}/revisions`);
}

/** 学生「我的任务」按课题分组一次拉全 */
export function apiMyTasks(status?: string): Promise<IMyTaskGroup[]> {
  return get<IMyTaskGroup[]>('/tasks/my', status ? { status } : undefined);
}

/* ------------------------------------------------------------------ 进度 */

export function apiProjectProgress(projectId: number): Promise<IProjectStats> {
  return get<IProjectStats>(`/projects/${projectId}/progress`);
}

/* ------------------------------------------------------------------ 数据集 */

export function apiListDatasets(projectId: number): Promise<IDataset[]> {
  return get<IDataset[]>(`/projects/${projectId}/datasets`);
}

/** multipart 新建 v1；同名冲突业务码 46001（IBizError.code 判定，r2-C4） */
export function apiCreateDataset(
  projectId: number,
  file: File,
  datasetName?: string,
  description?: string,
): Promise<IDataset> {
  const fd = new FormData();
  fd.append('file', file);
  if (datasetName) fd.append('datasetName', datasetName);
  if (description) fd.append('description', description);
  return upload<IDataset>(`/projects/${projectId}/datasets`, fd);
}

export function apiListVersions(datasetId: number): Promise<IDatasetVersion[]> {
  return get<IDatasetVersion[]>(`/datasets/${datasetId}/versions`);
}

export function apiAddVersion(datasetId: number, file: File, note?: string): Promise<IDatasetVersion> {
  const fd = new FormData();
  fd.append('file', file);
  if (note) fd.append('note', note);
  return upload<IDatasetVersion>(`/datasets/${datasetId}/versions`, fd);
}

export function apiUpdateDataset(id: number, body: { datasetName?: string; description?: string }): Promise<IDataset> {
  return put<IDataset>(`/datasets/${id}`, body);
}

export function apiDeleteDataset(id: number): Promise<void> {
  return del<void>(`/datasets/${id}`);
}

export function apiDeleteVersion(vid: number): Promise<void> {
  return del<void>(`/dataset-versions/${vid}`);
}

export function apiDownloadVersion(vid: number): Promise<Blob> {
  return download(`/dataset-versions/${vid}/download`);
}

export function apiPreviewVersion(vid: number): Promise<IDatasetPreview> {
  return get<IDatasetPreview>(`/dataset-versions/${vid}/preview`);
}

/* ------------------------------------------------------------------ 学生档案 */

export function apiSearchStudents(params?: { keyword?: string; className?: string }): Promise<IStudent[]> {
  return get<IStudent[]>('/students', params);
}

export function apiResetStudentPassword(studentId: number): Promise<void> {
  return post<void>(`/students/${studentId}/reset-password`);
}
