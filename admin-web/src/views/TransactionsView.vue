<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchTransactions, fetchTransactionSummary } from '@/api/admin'
import type { TransactionQuery } from '@/api/admin'
import { loadAccountOptions, loadBookOptions, loadUserOptions } from '@/api/options'
import { usePagedTable } from '@/composables/usePagedTable'
import { formatAmount, TRANSACTION_TYPES } from '@/types'
import { formatDateTime, toIsoString } from '@/utils/format'
import CopyText from '@/components/CopyText.vue'
import EntitySelect from '@/components/EntitySelect.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import TableEmpty from '@/components/TableEmpty.vue'
import type { TransactionInfo, TransactionSummary } from '@/types'

const route = useRoute()

const query = reactive({
  page: 1,
  size: 20,
  bookId: '',
  accountId: '',
  userId: '',
  type: undefined as number | undefined,
  keyword: ''
})

/** 日期筛选保存 Date 对象，提交时转 UTC ISO；直接拼 "...Z" 会让查询窗口偏移 8 小时 */
const timeRange = ref<[Date, Date] | null>(null)

/** 从别的页面下钻过来时，选项还没加载也能显示名称 */
const bookLabel = ref('')
const accountLabel = ref('')
const userLabel = ref('')

function buildParams(): Record<string, unknown> {
  return {
    bookId: query.bookId || undefined,
    accountId: query.accountId || undefined,
    userId: query.userId || undefined,
    type: query.type,
    keyword: query.keyword || undefined,
    startTime: toIsoString(timeRange.value?.[0]),
    endTime: toIsoString(timeRange.value?.[1])
  }
}

const { loading, error, list, total, load, search, onPageChange, onSizeChange } =
  usePagedTable<TransactionInfo>(
    (params) => fetchTransactions(params as TransactionQuery),
    query,
    buildParams
  )

const hasFilter = computed(
  () =>
    !!query.bookId ||
    !!query.accountId ||
    !!query.userId ||
    query.type !== undefined ||
    !!query.keyword ||
    !!timeRange.value
)

/* ---------------- 当前筛选条件下的合计 ---------------- */

const summary = ref<TransactionSummary | null>(null)
const summaryLoading = ref(false)

async function loadSummary() {
  summaryLoading.value = true
  try {
    summary.value = await fetchTransactionSummary(buildParams() as TransactionQuery)
  } catch {
    // 合计失败不影响列表本身，留空即可
    summary.value = null
  } finally {
    summaryLoading.value = false
  }
}

function handleSearch() {
  search()
  loadSummary()
}

function handleReset() {
  query.bookId = ''
  query.accountId = ''
  query.userId = ''
  query.type = undefined
  query.keyword = ''
  timeRange.value = null
  bookLabel.value = ''
  accountLabel.value = ''
  userLabel.value = ''
  handleSearch()
}

/* ---------------- 从账本 / 账户 / 用户下钻过来 ---------------- */

function readRouteQuery() {
  const params = route.query
  if (typeof params.bookId === 'string' && params.bookId) {
    query.bookId = params.bookId
    bookLabel.value = typeof params.bookLabel === 'string' ? params.bookLabel : ''
  }
  if (typeof params.accountId === 'string' && params.accountId) {
    query.accountId = params.accountId
    accountLabel.value = typeof params.accountLabel === 'string' ? params.accountLabel : ''
  }
  if (typeof params.userId === 'string' && params.userId) {
    query.userId = params.userId
    userLabel.value = typeof params.userLabel === 'string' ? params.userLabel : ''
  }
  if (typeof params.type === 'string') {
    const type = Number(params.type)
    if ([1, 2, 3].includes(type)) {
      query.type = type
    }
  }
}

function typeMeta(type: number) {
  return TRANSACTION_TYPES.find((item) => item.value === type) ?? { label: '未知', tag: 'info' as const }
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

onMounted(() => {
  readRouteQuery()
  load()
  loadSummary()
})
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <PageToolbar :total="total" unit="笔交易">
        <el-input v-model="query.keyword" placeholder="备注关键字" clearable @keyup.enter="handleSearch" />
        <EntitySelect v-model="query.bookId" :loader="loadBookOptions" placeholder="账本" width="190px" :selected-label="bookLabel" />
        <EntitySelect
          v-model="query.accountId"
          :loader="loadAccountOptions"
          placeholder="账户"
          width="190px"
          :selected-label="accountLabel"
        />
        <EntitySelect
          v-model="query.userId"
          :loader="loadUserOptions"
          placeholder="用户（账本所有者）"
          width="210px"
          :selected-label="userLabel"
        />
        <el-select v-model="query.type" placeholder="交易类型" clearable style="width: 130px">
          <el-option v-for="item in TRANSACTION_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 340px"
        />
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </PageToolbar>

      <div class="summary-bar" v-loading="summaryLoading">
        <span>
          支出 <strong class="amount-expense">{{ summary ? formatAmount(summary.expense) : '-' }}</strong>
        </span>
        <span>
          收入 <strong class="amount-income">{{ summary ? formatAmount(summary.income) : '-' }}</strong>
        </span>
        <span>
          转账 <strong>{{ summary ? formatAmount(summary.transfer) : '-' }}</strong>
        </span>
        <span class="muted">当前筛选的全部结果，不受分页影响</span>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="交易 ID" width="150">
          <template #default="{ row }">
            <CopyText :text="row.id" mono label="交易 ID" />
          </template>
        </el-table-column>
        <el-table-column label="账本" min-width="150">
          <template #default="{ row }">
            <div>{{ row.bookName || '（账本已删除）' }}</div>
            <CopyText :text="row.bookId" mono label="账本 ID" />
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="typeMeta(row.type).tag">{{ typeMeta(row.type).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额（元）" width="130" align="right">
          <template #default="{ row }">
            <span :class="amountClass(row.type)">{{ amountPrefix(row.type) }}{{ formatAmount(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="账户" min-width="170">
          <template #default="{ row }">
            <span v-if="row.type === 3">{{ row.accountName || row.accountId }} → {{ row.toAccountName || row.toAccountId }}</span>
            <span v-else>{{ row.accountName || row.accountId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="120">
          <template #default="{ row }">{{ row.categoryName || row.categoryId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="交易时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.tradeTime) }}</template>
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
  </div>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
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
