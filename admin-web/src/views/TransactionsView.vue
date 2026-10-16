<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { fetchTransactions } from '@/api/admin'
import { formatAmount, TRANSACTION_TYPES } from '@/types'
import type { TransactionInfo } from '@/types'

const loading = ref(false)
const list = ref<TransactionInfo[]>([])
const total = ref(0)
const timeRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1,
  size: 20,
  bookId: '',
  accountId: '',
  type: undefined as number | undefined,
  keyword: ''
})

async function loadData() {
  loading.value = true
  try {
    const result = await fetchTransactions({
      page: query.page,
      size: query.size,
      bookId: query.bookId || undefined,
      accountId: query.accountId || undefined,
      type: query.type,
      keyword: query.keyword || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1]
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
  query.bookId = ''
  query.accountId = ''
  query.type = undefined
  query.keyword = ''
  timeRange.value = null
  query.page = 1
  loadData()
}

function typeMeta(type: number) {
  return TRANSACTION_TYPES.find((item) => item.value === type) ?? { label: '未知', tag: 'info' as const }
}

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function amountClass(type: number) {
  if (type === 1) {
    return 'amount-expense'
  }
  if (type === 2) {
    return 'amount-income'
  }
  return ''
}

function amountPrefix(type: number) {
  if (type === 1) {
    return '-'
  }
  if (type === 2) {
    return '+'
  }
  return ''
}

onMounted(loadData)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="page-toolbar">
        <el-input v-model="query.bookId" placeholder="账本 ID" clearable @keyup.enter="handleSearch" />
        <el-input v-model="query.accountId" placeholder="账户 ID" clearable @keyup.enter="handleSearch" />
        <el-select v-model="query.type" placeholder="交易类型" clearable style="width: 150px">
          <el-option v-for="item in TRANSACTION_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="备注关键字" clearable @keyup.enter="handleSearch" />
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss[Z]"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 360px"
        />
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer"></div>
        <span class="total-text">共 {{ total }} 笔交易</span>
      </div>

      <el-table :data="list" v-loading="loading" stripe border>
        <el-table-column prop="id" label="交易 ID" width="200" />
        <el-table-column label="账本" min-width="160">
          <template #default="{ row }">
            <div>{{ row.bookName || '（账本已删除）' }}</div>
            <div class="muted">{{ row.bookId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="typeMeta(row.type).tag">{{ typeMeta(row.type).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额（元）" width="140" align="right">
          <template #default="{ row }">
            <span :class="amountClass(row.type)">{{ amountPrefix(row.type) }}{{ formatAmount(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="账户" min-width="160">
          <template #default="{ row }">
            <span v-if="row.type === 3">{{ row.accountName || row.accountId }} → {{ row.toAccountName || row.toAccountId }}</span>
            <span v-else>{{ row.accountName || row.accountId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="130">
          <template #default="{ row }">{{ row.categoryName || row.categoryId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="交易时间" width="180">
          <template #default="{ row }">{{ formatTime(row.tradeTime) }}</template>
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

.muted {
  color: #909399;
  font-size: 12px;
}

.amount-expense {
  color: #f56c6c;
  font-weight: 600;
}

.amount-income {
  color: #67c23a;
  font-weight: 600;
}
</style>
