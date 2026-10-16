import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types'

export const TOKEN_KEY = 'jizhang_admin_token'
export const USER_KEY = 'jizhang_admin_user'

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let redirecting = false

function redirectToLogin() {
  if (redirecting) {
    return
  }
  redirecting = true
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  ElMessage.error('登录已过期，请重新登录')
  setTimeout(() => {
    redirecting = false
    window.location.href = '/login'
  }, 300)
}

service.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const result = response.data
    if (result.code === 0) {
      return response
    }
    // 401 未登录 / 1004 Token 失效
    if (result.code === 401 || result.code === 1004) {
      redirectToLogin()
    } else {
      ElMessage.error(result.message || '请求失败')
    }
    return Promise.reject(new Error(result.message || '请求失败'))
  },
  (error) => {
    // 后端对「未认证 / 令牌过期」统一返回标准 HTTP 401（便于移动端自动续期）
    if (error?.response?.status === 401) {
      redirectToLogin()
    } else {
      ElMessage.error(error?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

/** 提取 data 字段，失败时抛出异常 */
async function unwrap<T>(promise: Promise<AxiosResponse<ApiResult<T>>>): Promise<T> {
  const response = await promise
  return response.data.data
}

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return unwrap(service.get<ApiResult<T>>(url, { params }))
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return unwrap(service.post<ApiResult<T>>(url, data))
}

export function put<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  return unwrap(service.put<ApiResult<T>>(url, data, { params }))
}

export function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return unwrap(service.delete<ApiResult<T>>(url, { params }))
}

export default service
