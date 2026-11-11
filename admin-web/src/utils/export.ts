import { ElMessage } from 'element-plus'
import type { DownloadResult } from '@/api/request'

/**
 * 导出文件名：统一带时间戳，同一天导两次不会互相覆盖，也方便按导出时间对账。
 */
export function exportFilename(base: string, ext = 'csv'): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
    `-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return `${base}-${stamp}.${ext}`
}

/**
 * 触发浏览器下载 Blob。
 *
 * 用「临时 a 标签 + objectURL」而不是直接开新窗口：后者会被浏览器拦截，
 * 且导出接口需要鉴权，新窗口不会带上 Authorization 头。
 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  // 立刻 revoke 会让部分浏览器的下载中断，放到下一轮事件循环再释放
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

/**
 * 导出收尾：落盘并提示导了多少条 / 是否被上限截断。
 *
 * 截断必须明确告知：全平台流水很容易超过 5 万条，静悄悄少一半数据比导出失败更危险。
 */
export function finishExport(result: DownloadResult, base: string): void {
  // 0 条时不落一个只有表头的空文件，直接告诉管理员换个条件
  if (result.rows === 0) {
    ElMessage.info('当前筛选条件没有可导出的数据')
    return
  }
  saveBlob(result.blob, exportFilename(base))
  if (result.truncated) {
    ElMessage.warning(`已导出 ${result.rows ?? 0} 条，达到单次上限；剩余数据请缩小筛选范围后再导`)
  } else {
    ElMessage.success(`已导出 ${result.rows ?? 0} 条`)
  }
}

/**
 * 导出异常的提示。
 *
 * 网络 / 鉴权错误已经由请求拦截器统一弹过，这里只补提示接口自身返回的业务原因，避免弹两次。
 */
export function warnExportError(error: unknown): void {
  const message = error instanceof Error ? error.message : ''
  if (!message || /status code|Network Error|timeout/i.test(message)) {
    return
  }
  ElMessage.error(message)
}
