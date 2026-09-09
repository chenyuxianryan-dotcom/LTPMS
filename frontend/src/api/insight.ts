/** 数据智能模块 /insight（就绪度体检 / 利用率热力图 / 异常预警 / 冲突检测） */
import { get } from '@/utils/request';

export interface IReadinessItem {
  courseId: number;
  courseCode: string;
  courseName: string;
  teacherName: string | null;
  experimentCount: number;
  status: 'GREEN' | 'RED';
  advice: string;
}

export interface IUtilizationItem {
  labId: number;
  labCode: string;
  labName: string;
  scheduleCount: number;
  timeSlotCount: number;
}

export interface IAnomalyItem {
  type: 'OVERLOAD' | 'IDLE' | 'HOURS';
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  target: string;
  detail: string;
}

export interface IConflictItem {
  resourceType: 'LAB' | 'TEACHER';
  resourceName: string;
  dayOfWeek: number;
  startSection: number;
  endSection: number;
  overlapWeeks: string;
  courseCodes: string[];
}

export function apiGetReadiness(): Promise<IReadinessItem[]> {
  return get<IReadinessItem[]>('/insight/readiness');
}

export function apiGetUtilization(semesterId?: number): Promise<IUtilizationItem[]> {
  return get<IUtilizationItem[]>('/insight/utilization', semesterId ? { semesterId } : undefined);
}

export function apiGetAnomalies(semesterId?: number): Promise<IAnomalyItem[]> {
  return get<IAnomalyItem[]>('/insight/anomalies', semesterId ? { semesterId } : undefined);
}

export function apiGetConflicts(semesterId?: number): Promise<IConflictItem[]> {
  return get<IConflictItem[]>('/insight/conflicts', semesterId ? { semesterId } : undefined);
}
