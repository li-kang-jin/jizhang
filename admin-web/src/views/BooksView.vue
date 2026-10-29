<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchBooks } from '@/api/admin'
import type { BookQuery } from '@/api/admin'
import { loadUserOptions } from '@/api/options'
import { usePagedTable } from '@/composables/usePagedTable'
import { BOOK_TYPES } from '@/types'
import { formatDateTime } from '@/utils/format'
import BookAccountsDrawer from '@/components/BookAccountsDrawer.vue'
import CopyText from '@/components/CopyText.vue'
import EntitySelect from '@/components/EntitySelect.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import TableEmpty from '@/components/TableEmpty.vue'
import type { BookInfo } from '@/types'

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  type: undefined as number | undefined,
  ownerId: ''
})

const { loading, error, list, total, load, search, onPageChange, onSizeChange } = usePagedTable<BookInfo>(
  (params) => fetchBooks(params as BookQuery),
  query,
  () => ({
    keyword: query.keyword || undefined,
    type: query.type,
    ownerId: query.ownerId || undefined
  })
)

const hasFilter = computed(() => !!query.keyword || query.type !== undefined || !!query.ownerId)

function handleReset() {
  query.keyword = ''
  query.type = undefined
  query.ownerId = ''
  search()
}

function typeLabel(type: number) {
  return BOOK_TYPES.find((item) => item.value === type)?.label ?? '未知'
}

/* 下钻：查看该账本的账户 */
const accountsVisible = ref(false)
const accountsBook = ref<BookInfo | null>(null)

function openAccounts(row: BookInfo) {
  accountsBook.value = row
  accountsVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <PageToolbar :total="total" unit="个账本">
        <el-input v-model="query.keyword" placeholder="账本名称 / 备注" clearable @keyup.enter="search" />
        <el-select v-model="query.type" placeholder="账本类型" clearable>
          <el-option v-for="item in BOOK_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <EntitySelect
          v-model="query.ownerId"
          :loader="loadUserOptions"
          placeholder="所有者（用户名 / 昵称）"
          width="220px"
        />
        <el-button type="primary" @click="search">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </PageToolbar>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="账本 ID" width="150">
          <template #default="{ row }">
            <CopyText :text="row.id" mono label="账本 ID" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="账本名称" min-width="160" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="所有者" min-width="170">
          <template #default="{ row }">
            <div>{{ row.ownerUsername || '-' }}</div>
            <CopyText :text="row.ownerId" mono label="所有者用户 ID" />
          </template>
        </el-table-column>
        <el-table-column label="成员数" width="90" align="center">
          <template #default="{ row }">{{ row.memberCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="账户数" width="90" align="center">
          <template #default="{ row }">{{ row.accountCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAccounts(row)">账户</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <TableEmpty :error="error" :filtered="hasFilter" @retry="load" @clear="handleReset" />
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <BookAccountsDrawer v-model="accountsVisible" :book="accountsBook" />
  </div>
</template>
