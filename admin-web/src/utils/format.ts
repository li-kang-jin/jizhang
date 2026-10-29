const pad = (value: number) => String(value).padStart(2, '0')

/**
 * 后端时间统一以 UTC 序列化（Jackson time-zone=UTC，OffsetDateTime 带 Z），
 * 这里一律转成浏览器本地时间展示，避免同一个后台里出现两套时间。
 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes()
  )}:${pad(date.getSeconds())}`
}

/**
 * 本地时间 → UTC ISO 字符串。
 * 日期筛选必须走这一步：直接拼 "...Z" 会把本地时间当成 UTC 传给后端，查询窗口整体偏移 8 小时。
 */
export function toIsoString(value?: Date | null): string | undefined {
  if (!value) {
    return undefined
  }
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}
