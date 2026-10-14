import { get, post, put } from './request'
import type {
  BookInfo,
  LoginPayload,
  PageResult,
  PlatformOverview,
  TokenInfo,
  TransactionInfo,
  TrendPoint,
  UserInfo
} from '@/types'

/* ---------------- 认证 ---------------- */

export function adminLoginApi(payload: LoginPayload): Promise<TokenInfo> {
  return post<TokenInfo>('/admin/auth/login', payload)
}

/* ---------------- 统计 ---------------- */

export function fetchOverview(): Promise<PlatformOverview> {
  return get<PlatformOverview>('/admin/statistics/overview')
}

export function fetchTrend(days = 30): Promise<TrendPoint[]> {
  return get<TrendPoint[]>('/admin/statistics/trend', { days })
}

/* ---------------- 用户 ---------------- */

export interface UserQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number | undefined
  role?: string | undefined
}

export function fetchUsers(params: UserQuery): Promise<PageResult<UserInfo>> {
  return get<PageResult<UserInfo>>('/admin/users', params as Record<string, unknown>)
}

export function updateUserStatus(id: string, status: number): Promise<UserInfo> {
  return put<UserInfo>(`/admin/users/${id}/status`, null, { status })
}

/* ---------------- 账本 ---------------- */

export interface BookQuery {
  page?: number
  size?: number
  keyword?: string
  type?: number | undefined
  ownerId?: string
}

export function fetchBooks(params: BookQuery): Promise<PageResult<BookInfo>> {
  return get<PageResult<BookInfo>>('/admin/books', params as Record<string, unknown>)
}

/* ---------------- 交易 ---------------- */

export interface TransactionQuery {
  page?: number
  size?: number
  bookId?: string
  userId?: string
  type?: number | undefined
  keyword?: string
  startTime?: string
  endTime?: string
}

export function fetchTransactions(params: TransactionQuery): Promise<PageResult<TransactionInfo>> {
  return get<PageResult<TransactionInfo>>('/admin/transactions', params as Record<string, unknown>)
}
