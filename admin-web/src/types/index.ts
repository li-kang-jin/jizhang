/** 后端统一响应体 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface LoginPayload {
  username: string
  password: string
}

export interface UserInfo {
  id: string
  username: string
  nickname?: string
  avatar?: string
  phone?: string
  email?: string
  role: string
  status: number
  createdAt?: string
  lastLoginAt?: string
  /** 1=密码已被管理员重置，用户还没自己改过 */
  mustChangePassword?: number
}

export interface TokenInfo {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserInfo
}

export interface PlatformOverview {
  userTotal: number
  userToday: number
  bookTotal: number
  /** 今日有记账行为的账本数（按交易时间去重） */
  activeBookToday: number
  transactionTotal: number
  transactionToday: number
  /** 累计支出（分），不含转账 */
  expenseTotal: number
  /** 累计收入（分），不含转账 */
  incomeTotal: number
  expenseToday: number
  incomeToday: number
}

export interface TrendPoint {
  date: string
  newUsers: number
  newTransactions: number
  /** 当日支出（分），不含转账 */
  expense: number
  /** 当日收入（分），不含转账 */
  income: number
}

export interface BookInfo {
  id: string
  name: string
  icon?: string
  currency: string
  type: number
  ownerId: string
  /** 所有者登录名 / 昵称（管理后台列表返回） */
  ownerUsername?: string
  /** 成员数（含所有者） */
  memberCount?: number
  /** 账户数 */
  accountCount?: number
  remark?: string
  createdAt?: string
}

export interface TransactionInfo {
  id: string
  bookId: string
  type: number
  amount: number
  accountId: string
  toAccountId?: string
  categoryId?: string
  tradeTime: string
  remark?: string
  source?: number
  updatedAt?: string
  /** 以下名称字段由后端管理端接口补齐，缺失时前端回落到展示 ID */
  bookName?: string
  accountName?: string
  toAccountName?: string
  categoryName?: string
}

/** 远程搜索下拉项 */
export interface EntityOption {
  value: string
  label: string
}

/** 当前筛选条件下的流水合计（分） */
export interface TransactionSummary {
  expense: number
  income: number
  transfer: number
  count: number
}

export interface AccountInfo {
  id: string
  bookId: string
  /** 所属账本名称（账本被删除时为空） */
  bookName?: string
  ownerId?: string
  ownerUsername?: string
  name: string
  type: number
  /** 当前余额（分），由交易流水维护 */
  balance: number
  /** 初始余额（分） */
  initialBalance: number
  currency: string
  icon?: string
  sort: number
  /** 是否计入总资产：0=否 1=是 */
  includeInTotal: number
  createdAt?: string
  updatedAt?: string
}

/** 交易类型 1=支出 2=收入 3=转账 */
export const TRANSACTION_TYPES = [
  { value: 1, label: '支出', tag: 'danger' },
  { value: 2, label: '收入', tag: 'success' },
  { value: 3, label: '转账', tag: 'info' }
] as const

export const BOOK_TYPES = [
  { value: 0, label: '个人' },
  { value: 1, label: '家庭' },
  { value: 2, label: '共享' }
] as const

export const ACCOUNT_TYPES = [
  { value: 1, label: '现金' },
  { value: 2, label: '储蓄卡' },
  { value: 3, label: '信用卡' },
  { value: 4, label: '支付宝' },
  { value: 5, label: '微信' },
  { value: 6, label: '投资' },
  { value: 7, label: '其他' }
] as const

/** 分转元，保留两位小数 */
export function fenToYuan(fen: number | undefined | null): string {
  if (fen === undefined || fen === null) {
    return '0.00'
  }
  return (fen / 100).toFixed(2)
}

/** 千分位金额（元） */
export function formatAmount(fen: number | undefined | null): string {
  const yuan = Number(fenToYuan(fen))
  return yuan.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
