<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchBooks } from '@/api/admin'
import { BOOK_TYPES } from '@/types'
import { formatDateTime } from '@/utils/format'
import TableEmpty from './TableEmpty.vue'
import type { BookInfo, UserInfo } from '@/types'

const props = defineProps<{
  modelValue: boolean
  user: UserInfo | null
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const loading = ref(false)
const error = ref(false)
const list = ref<BookInfo[]>([])
const total = ref(0)

async function load() {
  if (!props.user) {
    return
  }
  loading.value = true
  error.value = false
  try {
    const result = await fetchBooks({ page: 1, size: 100, ownerId: props.user.id })
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
  return BOOK_TYPES.find((item) => item.value === type)?.label ?? '未知'
}

/** 下钻：带着用户与账本去流水页，省掉让客服再问一遍 ID */
function openTransactions(book: BookInfo) {
  if (!props.user) {
    return
  }
  close()
  void router.push({
    path: '/transactions',
    query: {
      userId: props.user.id,
      userLabel: props.user.username,
      bookId: book.id,
      bookLabel: book.name
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
        <span class="drawer-title">名下账本</span>
        <span class="muted">{{ user?.username }} · {{ user?.nickname || '未设置昵称' }}</span>
      </div>
    </template>

    <div class="drawer-summary">
      <span>手机号：{{ user?.phone || '-' }}</span>
      <span>注册时间：{{ formatDateTime(user?.createdAt) }}</span>
      <span>账本数：{{ total }}（仅统计其为所有者的账本）</span>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="name" label="账本名称" min-width="160" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column label="成员数" width="90" align="center">
        <template #default="{ row }">{{ row.memberCount ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="账户数" width="90" align="center">
        <template #default="{ row }">{{ row.accountCount ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
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
