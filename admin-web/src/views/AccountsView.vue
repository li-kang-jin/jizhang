<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createAccount,
  deleteAccount,
  fetchAccounts,
  fetchBooks,
  fetchTransactions,
  updateAccount
} from '@/api/admin'
import type { AccountPayload } from '@/api/admin'
import { ACCOUNT_TYPES, formatAmount, TRANSACTION_TYPES } from '@/types'
import type { AccountInfo, BookInfo, TransactionInfo } from '@/types'

const loading = ref(false)
const list = ref<AccountInfo[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  bookId: '',
  userId: '',
  type: undefined as number | undefined
})

/* ---------------- 列表 ---------------- */

async function loadData() {
  loading.value = true
  try {
    const result = await fetchAccounts({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      bookId: query.bookId || undefined,
      userId: query.userId || undefined,
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
  query.bookId = ''
  query.userId = ''
  query.type = undefined
  query.page = 1
  loadData()
}

function typeLabel(type: number) {
  return ACCOUNT_TYPES.find((item) => item.value === type)?.label ?? '未知'
}

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

/* ---------------- 新增 / 编辑 ---------------- */

const dialogVisible = ref(false)
const dialogTitle = computed(() => (editingId.value ? '编辑账户' : '新增账户'))
const saving = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  bookId: '',
  name: '',
  type: undefined as number | undefined,
  /** 表单里按「元」填写，提交时换算成分 */
  initialBalanceYuan: 0,
  currency: 'CNY',
  icon: '',
  sort: 0,
  includeInTotal: 1
})

const rules: FormRules = {
  bookId: [{ required: true, message: '请选择所属账本', trigger: 'change' }],
  name: [
    { required: true, message: '请输入账户名称', trigger: 'blur' },
    { max: 64, message: '账户名称最长 64 个字符', trigger: 'blur' }
  ],
  type: [{ required: true, message: '请选择账户类型', trigger: 'change' }]
}

/* 账本远程搜索：后台新建账户必须落在某个账本下，光靠手输 ID 容易填错 */
const bookOptions = ref<BookInfo[]>([])
const bookLoading = ref(false)

async function searchBooks(keyword: string) {
  bookLoading.value = true
  try {
    const result = await fetchBooks({ page: 1, size: 50, keyword: keyword || undefined })
    bookOptions.value = result.records
  } finally {
    bookLoading.value = false
  }
}

function resetForm() {
  form.bookId = ''
  form.name = ''
  form.type = undefined
  form.initialBalanceYuan = 0
  form.currency = 'CNY'
  form.icon = ''
  form.sort = 0
  form.includeInTotal = 1
}

function openCreate() {
  editingId.value = null
  resetForm()
  bookOptions.value = []
  searchBooks('')
  dialogVisible.value = true
}

function openEdit(row: AccountInfo) {
  editingId.value = row.id
  form.bookId = row.bookId
  form.name = row.name
  form.type = row.type
  form.initialBalanceYuan = Number(((row.initialBalance ?? 0) / 100).toFixed(2))
  form.currency = row.currency || 'CNY'
  form.icon = row.icon || ''
  form.sort = row.sort ?? 0
  form.includeInTotal = row.includeInTotal ?? 1
  // 编辑时账本不可选，但下拉里得有这一项，否则只显示一个 ID
  bookOptions.value = [{ id: row.bookId, name: row.bookName || `账本 ${row.bookId}` } as BookInfo]
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  const payload: AccountPayload = {
    bookId: form.bookId,
    name: form.name.trim(),
    type: form.type as number,
    initialBalance: Math.round(Number(form.initialBalanceYuan || 0) * 100),
    currency: form.currency || 'CNY',
    icon: form.icon || undefined,
    sort: form.sort ?? 0,
    includeInTotal: form.includeInTotal
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateAccount(editingId.value, payload)
      ElMessage.success('账户修改成功')
    } else {
      await createAccount(payload)
      ElMessage.success('账户创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
    // 错误提示已在 axios 拦截器中统一处理
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除 ---------------- */

async function handleDelete(row: AccountInfo) {
  try {
    await ElMessageBox.confirm(`确定要删除账户「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAccount(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    loadData()
  } catch {
    // 账户下还有流水时后端会拒绝，提示已由拦截器弹出
  }
}

/* ---------------- 账户流水 ---------------- */

const recordsVisible = ref(false)
const recordsLoading = ref(false)
const recordAccount = ref<AccountInfo | null>(null)
const recordList = ref<TransactionInfo[]>([])
const recordTotal = ref(0)
const recordQuery = reactive({ page: 1, size: 10 })

async function loadRecords() {
  if (!recordAccount.value) {
    return
  }
  recordsLoading.value = true
  try {
    const result = await fetchTransactions({
      page: recordQuery.page,
      size: recordQuery.size,
      accountId: recordAccount.value.id
    })
    recordList.value = result.records
    recordTotal.value = result.total
  } finally {
    recordsLoading.value = false
  }
}

function openRecords(row: AccountInfo) {
  recordAccount.value = row
  recordQuery.page = 1
  recordsVisible.value = true
  loadRecords()
}

function handleRecordsSearch() {
  recordQuery.page = 1
  loadRecords()
}

function transactionMeta(type: number) {
  return TRANSACTION_TYPES.find((item) => item.value === type) ?? { label: '未知', tag: 'info' as const }
}

function amountText(row: TransactionInfo) {
  const prefix = row.type === 1 ? '-' : row.type === 2 ? '+' : ''
  return `${prefix}${formatAmount(row.amount)}`
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

onMounted(loadData)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="page-toolbar">
        <el-input v-model="query.keyword" placeholder="账户名称关键字" clearable @keyup.enter="handleSearch" />
        <el-input v-model="query.bookId" placeholder="账本 ID" clearable @keyup.enter="handleSearch" />
        <el-input v-model="query.userId" placeholder="所有者用户 ID" clearable @keyup.enter="handleSearch" />
        <el-select v-model="query.type" placeholder="账户类型" clearable style="width: 150px">
          <el-option v-for="item in ACCOUNT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer"></div>
        <el-button type="primary" plain @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增账户</span>
        </el-button>
        <span class="total-text">共 {{ total }} 个账户</span>
      </div>

      <el-table :data="list" v-loading="loading" stripe border>
        <el-table-column prop="id" label="账户 ID" width="200" />
        <el-table-column prop="name" label="账户名称" min-width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属账本" min-width="180">
          <template #default="{ row }">
            <div>{{ row.bookName || '（账本已删除）' }}</div>
            <div class="muted">{{ row.bookId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="所有者" width="130">
          <template #default="{ row }">{{ row.ownerUsername || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前余额（元）" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="初始余额（元）" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.initialBalance) }}</template>
        </el-table-column>
        <el-table-column label="计入总资产" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.includeInTotal === 1 ? 'success' : 'info'">
              {{ row.includeInTotal === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRecords(row)">流水</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
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

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属账本" prop="bookId">
          <el-select
            v-model="form.bookId"
            filterable
            remote
            :remote-method="searchBooks"
            :loading="bookLoading"
            :disabled="!!editingId"
            placeholder="输入账本名称搜索"
            style="width: 100%"
            @focus="searchBooks('')"
          >
            <el-option v-for="book in bookOptions" :key="book.id" :label="book.name" :value="book.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账户名称" prop="name">
          <el-input v-model="form.name" placeholder="如：招商银行储蓄卡" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="账户类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in ACCOUNT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始余额">
          <el-input-number
            v-model="form.initialBalanceYuan"
            :precision="2"
            :step="100"
            :min="-99999999"
            :max="99999999"
            controls-position="right"
            style="width: 200px"
          />
          <span class="form-tip">元；修改时会同步调整当前余额，不动已有流水</span>
        </el-form-item>
        <el-form-item label="币种">
          <el-input v-model="form.currency" maxlength="8" placeholder="CNY" style="width: 120px" />
        </el-form-item>
        <el-form-item label="图标标识">
          <el-input v-model="form.icon" maxlength="64" placeholder="可选，如 bank" style="width: 200px" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" style="width: 140px" />
          <span class="form-tip">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="计入总资产">
          <el-switch v-model="form.includeInTotal" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 账户流水 -->
    <el-drawer v-model="recordsVisible" size="70%" append-to-body>
      <template #header>
        <span>账户流水{{ recordAccount ? ` · ${recordAccount.name}` : '' }}</span>
      </template>
      <div class="drawer-toolbar">
        <span class="muted">账户 ID：{{ recordAccount?.id }}</span>
        <div class="spacer"></div>
        <span class="muted">共 {{ recordTotal }} 笔</span>
      </div>
      <el-table :data="recordList" v-loading="recordsLoading" stripe border height="calc(100vh - 260px)">
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="transactionMeta(row.type).tag">{{ transactionMeta(row.type).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额（元）" width="130" align="right">
          <template #default="{ row }">
            <span :class="amountClass(row.type)">{{ amountText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="120">
          <template #default="{ row }">{{ row.categoryName || row.categoryId || '-' }}</template>
        </el-table-column>
        <el-table-column label="账户" min-width="140">
          <template #default="{ row }">
            <span v-if="row.type === 3">{{ row.accountName || row.accountId }} → {{ row.toAccountName || row.toAccountId }}</span>
            <span v-else>{{ row.accountName || row.accountId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="交易时间" width="180">
          <template #default="{ row }">{{ formatTime(row.tradeTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="recordQuery.page"
          v-model:page-size="recordQuery.size"
          :page-sizes="[10, 20, 50]"
          :total="recordTotal"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRecords"
          @size-change="handleRecordsSearch"
        />
      </div>
    </el-drawer>
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

.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.drawer-toolbar .spacer {
  flex: 1;
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
