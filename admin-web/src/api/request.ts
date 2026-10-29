import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult, TokenInfo } from '@/types'

export const TOKEN_KEY = 'jizhang_admin_token'
export const REFRESH_KEY = 'jizhang_admin_refresh'
export const USER_KEY = 'jizhang_admin_user'

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

interface RetriableConfig extends InternalAxiosRequestConfig {
  /** 每个请求只允许刷新令牌重试一次，避免无限递归 */
  _retried?: boolean
}

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
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
  ElMessage.error('登录已过期，请重新登录')
  // 带上当前地址，重新登录后回到原来那一页，而不是永远回看板
  const current = `${window.location.pathname}${window.location.search}`
  const redirect = current.startsWith('/login') ? '/dashboard' : current
  setTimeout(() => {
    redirecting = false
    window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
  }, 300)
}

let refreshPromise: Promise<string> | null = null

/** 用 refreshToken 换新的令牌对；并发请求共享同一次刷新 */
function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    const pending = (async () => {
      const refreshToken = localStorage.getItem(REFRESH_KEY)
      if (!refreshToken) {
        throw new Error('缺少刷新令牌')
      }
      // 用裸 axios，避免再次进入拦截器造成递归
      const response = await axios.post<ApiResult<TokenInfo>>('/api/v1/auth/refresh', { refreshToken })
      const result = response.data
      if (result.code !== 0 || !result.data?.accessToken) {
        throw new Error(result.message || '刷新令牌失败')
      }
      localStorage.setItem(TOKEN_KEY, result.data.accessToken)
      localStorage.setItem(REFRESH_KEY, result.data.refreshToken)
      localStorage.setItem(USER_KEY, JSON.stringify(result.data.user))
      return result.data.accessToken
    })()
    refreshPromise = pending.finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function retryAfterRefresh(config: InternalAxiosRequestConfig | undefined, message: string) {
  const retriable = config as RetriableConfig | undefined
  const isRefreshRequest = String(config?.url || '').includes('/auth/refresh')
  if (config && !retriable?._retried && !isRefreshRequest) {
    try {
      const token = await refreshAccessToken()
      if (retriable) {
        retriable._retried = true
      }
      config.headers.Authorization = `Bearer ${token}`
      return service.request(config)
    } catch {
      // 刷新失败，落到下面的重新登录
    }
  }
  redirectToLogin()
  return Promise.reject(new Error(message || '登录已过期'))
}

/** 标记本地用户需要改密，并跳到强制改密页；刷新后路由守卫才能读到最新状态 */
function requirePasswordChange() {
  const raw = localStorage.getItem(USER_KEY)
  if (raw) {
    try {
      const user = JSON.parse(raw) as { mustChangePassword?: number }
      user.mustChangePassword = 1
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } catch {
      // 本地缓存损坏时忽略，下一次登录会重新写入
    }
  }
  if (!window.location.pathname.startsWith('/change-password')) {
    ElMessage.warning('请先修改初始密码后再使用后台')
    setTimeout(() => {
      window.location.href = '/change-password'
    }, 300)
  }
}

service.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const result = response.data
    if (result.code === 0) {
      return response
    }
    // 1006 仍在使用初始密码：除改密接口外一律拦到强制改密页
    if (result.code === 1006) {
      requirePasswordChange()
      return Promise.reject(new Error(result.message || '请先修改初始密码'))
    }
    // 401 未登录 / 1004 Token 失效
    if (result.code === 401 || result.code === 1004) {
      return retryAfterRefresh(response.config, result.message)
    }
    ElMessage.error(result.message || '请求失败')
    return Promise.reject(new Error(result.message || '请求失败'))
  },
  (error) => {
    // 后端对「未认证 / 令牌过期」统一返回标准 HTTP 401
    if (error?.response?.status === 401) {
      return retryAfterRefresh(error.config, '登录已过期')
    }
    ElMessage.error(error?.message || '网络异常，请稍后重试')
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
