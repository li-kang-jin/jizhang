import { del, get, post, put } from './request'
import type {
  AccountInfo,
  BookInfo,
  LoginPayload,
  PageResult,
  PlatformOverview,
  TokenInfo,
  TransactionInfo,
  TransactionSummary,
  TrendPoint,
  UserInfo
} from '@/types'

/* ---------------- 认证 ---------------- */

export function adminLoginApi(payload: LoginPayload): Promise<TokenInfo> {
  return post<TokenInfo>('/admin/auth/login', payload)
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export function adminChangePasswordApi(payload: ChangePasswordPayload): Promise<void> {
  return post<void>('/admin/auth/password', payload)
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

/** 重置用户密码：服务端只返回这一次明文临时密码，关掉弹窗就再也看不到了 */
export function resetUserPassword(id: string): Promise<string> {
  return post<string>(`/admin/users/${id}/reset-password`)
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
  /** 按账户过滤：转出或转入命中该账户的流水都会返回 */
  accountId?: string
  userId?: string
  type?: number | undefined
  keyword?: string
  startTime?: string
  endTime?: string
}

export function fetchTransactions(params: TransactionQuery): Promise<PageResult<TransactionInfo>> {
  return get<PageResult<TransactionInfo>>('/admin/transactions', params as Record<string, unknown>)
}

/** 与列表同一套筛选条件，统计整个结果集而不是当前页 */
export function fetchTransactionSummary(params: TransactionQuery): Promise<TransactionSummary> {
  return get<TransactionSummary>('/admin/transactions/summary', params as Record<string, unknown>)
}

/* ---------------- 账户 ---------------- */

export interface AccountQuery {
  page?: number
  size?: number
  keyword?: string
  bookId?: string
  userId?: string
  type?: number | undefined
}

/** 新增 / 修改账户的请求体；金额单位为「分」 */
export interface AccountPayload {
  bookId: string
  name: string
  type: number
  initialBalance?: number
  currency?: string
  icon?: string
  sort?: number
  includeInTotal?: number
}

export function fetchAccounts(params: AccountQuery): Promise<PageResult<AccountInfo>> {
  return get<PageResult<AccountInfo>>('/admin/accounts', params as Record<string, unknown>)
}

export function createAccount(payload: AccountPayload): Promise<AccountInfo> {
  return post<AccountInfo>('/admin/accounts', payload)
}

/** 修改账户；bookId 会被后端忽略（不允许跨账本搬家） */
export function updateAccount(id: string, payload: AccountPayload): Promise<AccountInfo> {
  return put<AccountInfo>(`/admin/accounts/${id}`, payload)
}

export function deleteAccount(id: string): Promise<void> {
  return del<void>(`/admin/accounts/${id}`)
}
