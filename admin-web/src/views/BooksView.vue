<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { fetchBooks } from '@/api/admin'
import { BOOK_TYPES } from '@/types'
import type { BookInfo } from '@/types'

const loading = ref(false)
const list = ref<BookInfo[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  type: undefined as number | undefined
})

async function loadData() {
  loading.value = true
  try {
    const result = await fetchBooks({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      type: query.type
    })
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.keyword = ''
  query.type = undefined
  query.page = 1
  loadData()
}

function typeLabel(type: number) {
  return BOOK_TYPES.find((item) => item.value === type)?.label ?? '未知'
}

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(loadData)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="page-toolbar">
        <el-input v-model="query.keyword" placeholder="账本名称 / 备注" clearable @keyup.enter="handleSearch" />
        <el-select v-model="query.type" placeholder="账本类型" clearable>
          <el-option v-for="item in BOOK_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer"></div>
        <span class="total-text">共 {{ total }} 个账本</span>
      </div>

      <el-table :data="list" v-loading="loading" stripe border>
        <el-table-column prop="id" label="账本 ID" width="200" />
        <el-table-column prop="name" label="账本名称" min-width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column prop="ownerId" label="所有者 ID" width="200" />
        <el-table-column prop="remark" label="备注" min-width="180">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="handleSearch"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.total-text {
  color: #909399;
  font-size: 13px;
}
</style>
