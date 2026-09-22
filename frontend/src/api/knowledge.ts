/** 知识库模块 /knowledge：制度与流程（管理员维护）+ 课程资料（教师自助上传） */
import { del, download, get, upload } from '@/utils/request';

/** 文档分类：POLICY=制度与流程 / COURSE=课程资料 */
export type KnowledgeCategory = 'POLICY' | 'COURSE';

export interface IKnowledgeDocument {
  id: number;
  title: string;
  category: KnowledgeCategory;
  courseId: number | null;
  courseName: string | null;
  originalFilename: string;
  fileType: string;
  fileSize: number;
  /** 切分段数（= 已向量化的段落数） */
  chunkCount: number;
  uploaderId: number;
  uploaderName: string | null;
  createdAt: string | null;
}

export function apiListKnowledgeDocuments(
  params: { category?: KnowledgeCategory; courseId?: number } = {},
): Promise<IKnowledgeDocument[]> {
  return get<IKnowledgeDocument[]>('/knowledge/documents', params);
}

/** 入库（后端同步解析 + 切分 + 向量化，大文档耗时较长，单独放宽超时） */
export function apiUploadKnowledgeDocument(
  file: File,
  category: KnowledgeCategory,
  options: { title?: string; courseId?: number } = {},
): Promise<IKnowledgeDocument> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('category', category);
  if (options.title) {
    formData.append('title', options.title);
  }
  if (options.courseId) {
    formData.append('courseId', String(options.courseId));
  }
  return upload<IKnowledgeDocument>('/knowledge/documents', formData, { timeout: 300000 });
}

export function apiDeleteKnowledgeDocument(id: number): Promise<void> {
  return del<void>(`/knowledge/documents/${id}`);
}

export function apiDownloadKnowledgeDocument(id: number): Promise<Blob> {
  return download(`/knowledge/documents/${id}/download`);
}
