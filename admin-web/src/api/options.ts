import { fetchAccounts, fetchBooks, fetchUsers } from './admin'
import type { EntityOption } from '@/types'

/**
 * 远程下拉的数据源。
 * 后台的筛选条件原本是「手输 ID」，运营既记不住也容易填错，统一改成可搜索选择器。
 */

export async function loadBookOptions(keyword: string): Promise<EntityOption[]> {
  const result = await fetchBooks({ page: 1, size: 50, keyword: keyword || undefined })
  return result.records.map((book) => ({
    value: book.id,
    label: book.ownerUsername ? `${book.name} · ${book.ownerUsername}` : book.name
  }))
}

export async function loadAccountOptions(keyword: string): Promise<EntityOption[]> {
  const result = await fetchAccounts({ page: 1, size: 50, keyword: keyword || undefined })
  return result.records.map((account) => ({
    value: account.id,
    label: account.bookName ? `${account.name} · ${account.bookName}` : account.name
  }))
}

export async function loadUserOptions(keyword: string): Promise<EntityOption[]> {
  const result = await fetchUsers({ page: 1, size: 50, keyword: keyword || undefined })
  return result.records.map((user) => ({
    value: user.id,
    label: user.nickname ? `${user.username}（${user.nickname}）` : user.username
  }))
}
