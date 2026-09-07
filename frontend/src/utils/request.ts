/**
 * axios 实例与拦截器
 * - 自动附带 Bearer Token（sessionStorage，会话级存储，避免敏感信息长期驻留）
 * - 统一解包 IResult；业务失败统一 ElMessage 提示
 * - 401 清除会话并跳转登录页
 */
import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';
import type { IResult } from '@/types/api';

export const TOKEN_KEY = 'ltpms_token';

export interface IBizError extends Error {
  code: number;
  message: string;
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 30000,
});

request.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const result = response.data as IResult<unknown>;
    // blob 响应（文件下载）直接透传
    if (response.config.responseType === 'blob') {
      return response;
    }
    if (result.code === 200) {
      return result.data as never;
    }
    showBizError(result.code, result.message);
    return Promise.reject(createBizError(result.code, result.message));
  },
  (error) => {
    const status = error.response?.status;
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试';
    if (status === 401) {
      sessionStorage.removeItem(TOKEN_KEY);
      ElMessage.error('登录已过期，请重新登录');
      window.location.href = '/login';
      return Promise.reject(createBizError(401, message));
    }
    showBizError(status ?? 500, message);
    return Promise.reject(createBizError(status ?? 500, message));
  },
);

function createBizError(code: number, message: string): IBizError {
  const err = new Error(message) as IBizError;
  err.code = code;
  err.message = message;
  return err;
}

function showBizError(code: number, message: string): void {
  ElMessage.error(`[${code}] ${message}`);
}

/** GET 请求 */
export function get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
  return request.get(url, { params, ...config }) as Promise<T>;
}

/** POST 请求 */
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request.post(url, data, config) as Promise<T>;
}

/** PUT 请求 */
export function put<T>(url: string, data?: unknown): Promise<T> {
  return request.put(url, data) as Promise<T>;
}

/** PATCH 请求 */
export function patch<T>(url: string, data?: unknown): Promise<T> {
  return request.patch(url, data) as Promise<T>;
}

/** DELETE 请求 */
export function del<T>(url: string, params?: object): Promise<T> {
  return request.delete(url, { params }) as Promise<T>;
}

/** 文件下载（返回 Blob，由调用方触发保存） */
export async function download(url: string, params?: object): Promise<Blob> {
  const response = await request.get(url, { params, responseType: 'blob' });
  return response.data as Blob;
}

/** 文件上传（multipart/form-data） */
export function upload<T>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> {
  return request.post(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    ...config,
  }) as Promise<T>;
}

/** 触发浏览器保存文件 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export default request;
