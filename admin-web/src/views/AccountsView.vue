<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createAccount,
  deleteAccount,
  fetchAccounts,
  fetchTransactions,
  updateAccount
} from '@/api/admin'
import type { AccountQuery, AccountPayload, TransactionQuery } from '@/api/admin'
import { loadBookOptions, loadUserOptions } from '@/api/options'
import { usePagedTable } from '@/composables/usePagedTable'
import { ACCOUNT_TYPES, formatAmount, TRANSACTION_TYPES } from '@/types'
import { formatDateTime } from '@/utils/format'
import CopyText from '@/components/CopyText.vue'
import EntitySelect from '@/components/EntitySelect.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import TableEmpty from '@/components/TableEmpty.vue'
import type { AccountInfo, TransactionInfo } from '@/types'

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  bookId: '',
  userId: '',
  type: undefined as number | undefined
})

const { loading, error, list, total, load, search, onPageChange, onSizeChange, reloadAfterDelete } =
  usePagedTable<AccountInfo>((params) => fetchAccounts(params as AccountQuery), query, () => ({
    keyword: query.keyword || undefined,
    bookId: query.bookId || undefined,
    userId: query.userId || undefined,
    type: query.type
  }))

const hasFilter = computed(
  () => !!query.keyword || !!query.bookId || !!query.userId || query.type !== undefined
)

function handleReset() {
  query.keyword = ''
  query.bookId = ''
  query.userId = ''
  query.type = undefined
  search()
}

function typeLabel(type: number) {
  return ACCOUNT_TYPES.find((item) => item.value === type)?.label ?? '未知'
}

/* ---------------- 新增 / 编辑 ---------------- */

const dialogVisible = ref(false)
const dialogTitle = computed(() => (editingId.value ? '编辑账户' : '新增账户'))
const saving = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const formBookLabel = ref('')
const originalInitialBalance = ref(0)

const form = reactive({
  bookId: '',
  name: '',
  type: undefined as number | undefined,
  /** 表单里按「元」填写，提交时换算成分 */
  initialBalanceYuan: 0,
  currency: 'CNY',
  /** 图标是 App 侧字段，后台不展示，但编辑时必须原样带回，否则会被后端清空 */
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

function resetForm() {
  form.bookId = ''
  form.name = ''
  form.type = undefined
  form.initialBalanceYuan = 0
  form.currency = 'CNY'
  form.icon = ''
  form.sort = 0
  form.includeInTotal = 1
  formBookLabel.value = ''
  originalInitialBalance.value = 0
}

function openCreate() {
  editingId.value = null
  resetForm()
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
  formBookLabel.value = row.bookName || `账本 ${row.bookId}`
  originalInitialBalance.value = row.initialBalance ?? 0
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

  const initialBalance = Math.round(Number(form.initialBalanceYuan || 0) * 100)
  if (editingId.value && initialBalance !== originalInitialBalance.value) {
    try {
      await ElMessageBox.confirm(
        `初始余额将从 ${formatAmount(originalInitialBalance.value)} 元调整为 ${formatAmount(
          initialBalance
        )} 元，账户当前余额会同步变动。确认继续？`,
        '修改初始余额',
        { type: 'warning', confirmButtonText: '确认修改' }
      )
    } catch {
      return
    }
  }

  const payload: AccountPayload = {
    bookId: form.bookId,
    name: form.name.trim(),
    type: form.type as number,
    initialBalance,
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
    load()
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
    reloadAfterDelete()
  } catch {
    // 账户下还有流水时后端会拒绝，提示已由拦截器弹出
  }
}

/* ---------------- 账户流水 ---------------- */

const recordsVisible = ref(false)
const recordAccount = ref<AccountInfo | null>(null)
const recordQuery = reactive({ page: 1, size: 10 })

const {
  loading: recordsLoading,
  error: recordsError,
  list: recordList,
  total: recordTotal,
  load: loadRecords,
  search: searchRecords,
  onPageChange: onRecordPageChange,
  onSizeChange: onRecordSizeChange
} = usePagedTable<TransactionInfo>(
  (params) => fetchTransactions(params as TransactionQuery),
  recordQuery,
  () => ({ accountId: recordAccount.value?.id })
)

function openRecords(row: AccountInfo) {
  recordAccount.value = row
  recordsVisible.value = true
  searchRecords()
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

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <PageToolbar :total="total" unit="个账户">
        <el-input v-model="query.keyword" placeholder="账户名称关键字" clearable @keyup.enter="search" />
        <EntitySelect
          v-model="query.bookId"
          :loader="loadBookOptions"
          placeholder="所属账本"
          width="200px"
        />
        <EntitySelect
          v-model="query.userId"
          :loader="loadUserOptions"
          placeholder="所有者（用户名 / 昵称）"
          width="220px"
        />
        <el-select v-model="query.type" placeholder="账户类型" clearable style="width: 150px">
          <el-option v-for="item in ACCOUNT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="search">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <template #actions>
          <el-button type="primary" plain @click="openCreate">
            <el-icon><Plus /></el-icon>
            <span>新增账户</span>
          </el-button>
        </template>
      </PageToolbar>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="账户 ID" width="150">
          <template #default="{ row }">
            <CopyText :text="row.id" mono label="账户 ID" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="账户名称" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属账本" min-width="170">
          <template #default="{ row }">
            <div>{{ row.bookName || '（账本已删除）' }}</div>
            <CopyText :text="row.bookId" mono label="账本 ID" />
          </template>
        </el-table-column>
        <el-table-column label="所有者" width="120">
          <template #default="{ row }">{{ row.ownerUsername || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前余额（元）" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="初始余额（元）" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.initialBalance) }}</template>
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
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRecords(row)">流水</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="540px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属账本" prop="bookId">
          <EntitySelect
            v-model="form.bookId"
            :loader="loadBookOptions"
            :disabled="!!editingId"
            :selected-label="formBookLabel"
            placeholder="输入账本名称搜索"
            width="100%"
          />
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
          <span class="form-tip">元；修改会同步调整当前余额，不动已有流水</span>
        </el-form-item>
        <el-form-item label="计入总资产">
          <el-switch v-model="form.includeInTotal" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-collapse class="form-advanced">
          <el-collapse-item title="高级设置（币种 / 排序）" name="advanced">
            <el-form-item label="币种">
              <el-input v-model="form.currency" maxlength="8" placeholder="CNY" style="width: 120px" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number
                v-model="form.sort"
                :min="0"
                :max="9999"
                controls-position="right"
                style="width: 140px"
              />
              <span class="form-tip">数字越小越靠前</span>
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
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
      <el-table :data="recordList" v-loading="recordsLoading" stripe height="calc(100vh - 260px)">
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
        <el-table-column label="交易时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.tradeTime) }}</template>
        </el-table-column>
        <template #empty>
          <TableEmpty :error="recordsError" @retry="loadRecords" />
        </template>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="recordQuery.page"
          v-model:page-size="recordQuery.size"
          :page-sizes="[10, 20, 50]"
          :total="recordTotal"
          layout="total, sizes, prev, pager, next"
          @current-change="onRecordPageChange"
          @size-change="onRecordSizeChange"
        />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.form-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.form-advanced {
  margin-top: 4px;
  border-top: none;
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
