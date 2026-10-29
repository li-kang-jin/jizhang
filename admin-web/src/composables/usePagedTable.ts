import { ref, shallowRef } from 'vue'

export interface PageQuery {
  page: number
  size: number
}

export interface PageResponse<T> {
  records: T[]
  total: number
}

/**
 * 列表页的公共状态：加载、失败、分页、删除后回退。
 * 五个页面原来各写一套，空状态和失败重试因此各不相同。
 */
export function usePagedTable<T>(
  fetcher: (params: Record<string, unknown>) => Promise<PageResponse<T>>,
  query: PageQuery,
  buildParams: () => Record<string, unknown>
) {
  const loading = ref(false)
  const error = ref(false)
  const list = shallowRef<T[]>([])
  const total = ref(0)
  let requestId = 0

  async function load() {
    const current = ++requestId
    loading.value = true
    error.value = false
    try {
      const result = await fetcher({ page: query.page, size: query.size, ...buildParams() })
      // 快速切换筛选条件时，丢弃先发后到的旧响应
      if (current !== requestId) {
        return
      }
      list.value = result.records
      total.value = result.total
    } catch {
      if (current !== requestId) {
        return
      }
      error.value = true
      list.value = []
      total.value = 0
    } finally {
      if (current === requestId) {
        loading.value = false
      }
    }
  }

  function search() {
    query.page = 1
    void load()
  }

  function onPageChange() {
    void load()
  }

  function onSizeChange() {
    query.page = 1
    void load()
  }

  /** 删掉最后一条时回退一页，避免停在空白页 */
  function reloadAfterDelete() {
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    void load()
  }

  return { loading, error, list, total, load, search, onPageChange, onSizeChange, reloadAfterDelete }
}
