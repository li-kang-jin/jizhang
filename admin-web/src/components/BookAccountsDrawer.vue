<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAccounts } from '@/api/admin'
import { ACCOUNT_TYPES, formatAmount } from '@/types'
import { formatDateTime } from '@/utils/format'
import TableEmpty from './TableEmpty.vue'
import type { AccountInfo, BookInfo } from '@/types'

const props = defineProps<{
  modelValue: boolean
  book: BookInfo | null
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const loading = ref(false)
const error = ref(false)
const list = ref<AccountInfo[]>([])
const total = ref(0)

async function load() {
  if (!props.book) {
    return
  }
  loading.value = true
  error.value = false
  try {
    const result = await fetchAccounts({ page: 1, size: 100, bookId: props.book.id })
    list.value = result.records
    total.value = result.total
  } catch {
    error.value = true
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      void load()
    }
  }
)

function close() {
  emit('update:modelValue', false)
}

function typeLabel(type: number) {
  return ACCOUNT_TYPES.find((item) => item.value === type)?.label ?? '未知'
}

function openTransactions(account: AccountInfo) {
  if (!props.book) {
    return
  }
  close()
  void router.push({
    path: '/transactions',
    query: {
      bookId: props.book.id,
      bookLabel: props.book.name,
      accountId: account.id,
      accountLabel: account.name
    }
  })
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="68%"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="drawer-head">
        <span class="drawer-title">账本账户</span>
        <span class="muted">{{ book?.name }}</span>
      </div>
    </template>

    <div class="drawer-summary">
      <span>所有者：{{ book?.ownerUsername || book?.ownerId || '-' }}</span>
      <span>成员数：{{ book?.memberCount ?? '-' }}</span>
      <span>账户数：{{ total }}</span>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="name" label="账户名称" min-width="160" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前余额（元）" width="140" align="right">
        <template #default="{ row }">{{ formatAmount(row.balance) }}</template>
      </el-table-column>
      <el-table-column label="计入总资产" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.includeInTotal === 1 ? 'success' : 'info'">
            {{ row.includeInTotal === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openTransactions(row)">查流水</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <TableEmpty :error="error" @retry="load" />
      </template>
    </el-table>
  </el-drawer>
</template>

<style scoped>
.drawer-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.drawer-title {
  font-size: 16px;
  font-weight: 600;
}

.drawer-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 28px;
  margin-bottom: 14px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
