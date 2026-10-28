<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchUsers, resetUserPassword, updateUserStatus } from '@/api/admin'
import type { UserInfo } from '@/types'

const loading = ref(false)
const list = ref<UserInfo[]>([])
const total = ref(0)

/* 重置密码：临时密码只在这里显示一次，关掉弹窗就没了 */
const resetTarget = ref<UserInfo | null>(null)
const tempPassword = ref('')
const showTemp = ref(false)
const resetting = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: undefined as number | undefined,
  role: undefined as string | undefined
})

async function loadData() {
  loading.value = true
  try {
    const result = await fetchUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status,
      role: query.role
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
  query.status = undefined
  query.role = undefined
  query.page = 1
  loadData()
}

/**
 * 重置密码。
 * <p>
 * 确认框里必须先说清「旧密码立刻失效」：管理员点下去不会感受到代价，用户那头却会被踢下线。
 */
async function resetPassword(row: UserInfo) {
  try {
    await ElMessageBox.confirm(
      `将重置「${row.username}」的密码：旧密码立即失效，用户需要用临时密码重新登录。`,
      '重置密码',
      { type: 'warning', confirmButtonText: '确认重置' }
    )
  } catch {
    return
  }
  resetting.value = true
  try {
    tempPassword.value = await resetUserPassword(row.id)
    resetTarget.value = row
    showTemp.value = true
    // 列表里「待改密」标记跟着变，重新拉一遍
    loadData()
  } finally {
    resetting.value = false
  }
}

async function copyPassword() {
  try {
    await navigator.clipboard.writeText(tempPassword.value)
    ElMessage.success('已复制')
  } catch {
    // 非 https / 浏览器限制下 clipboard 不可用，提示用户手动选
    ElMessage.warning('复制失败，请手动选中后复制')
  }
}

function closeTemp() {
  showTemp.value = false
  tempPassword.value = ''
  resetTarget.value = null
}

async function toggleStatus(row: UserInfo) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户「${row.username}」吗？`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  await updateUserStatus(row.id, next)
  ElMessage.success(`${action}成功`)
  loadData()
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
        <el-input v-model="query.keyword" placeholder="用户名 / 昵称 / 手机号" clearable @keyup.enter="handleSearch" />
        <el-select v-model="query.status" placeholder="账号状态" clearable>
          <el-option label="正常" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
        <el-select v-model="query.role" placeholder="角色" clearable>
          <el-option label="普通用户" value="USER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer"></div>
        <span class="total-text">共 {{ total }} 位用户</span>
      </div>

      <el-table :data="list" v-loading="loading" stripe border>
        <el-table-column prop="id" label="用户 ID" width="200" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
            <el-tag
              v-if="row.mustChangePassword === 1"
              type="warning"
              size="small"
              style="margin-left: 6px"
              title="密码已被管理员重置，用户还没自己改过"
            >
              待改密
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="最后登录" width="180">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.role !== 'ADMIN'">
              <el-button
                link
                :type="row.status === 1 ? 'danger' : 'success'"
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button link type="primary" :loading="resetting" @click="resetPassword(row)">
                重置密码
              </el-button>
            </template>
            <span v-else class="muted">-</span>
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

    <el-dialog
      v-model="showTemp"
      title="临时密码"
      width="440px"
      :close-on-click-modal="false"
      @closed="closeTemp"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="这个密码只显示这一次，关闭后无法再次查看。请通过可靠渠道交给用户，并提醒他登录后立即修改。"
      />
      <div class="temp-password">
        <span class="pwd">{{ tempPassword }}</span>
        <el-button size="small" @click="copyPassword">复制</el-button>
      </div>
      <p class="muted">用户名：{{ resetTarget?.username || '-' }}</p>
      <template #footer>
        <el-button type="primary" @click="showTemp = false">我已记录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.total-text {
  color: #909399;
  font-size: 13px;
}

.muted {
  color: #c0c4cc;
}

.temp-password {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0 8px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f5f7fa;
}

.pwd {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 18px;
  letter-spacing: 1px;
  /* 双击整段选中，省得手动拖 */
  user-select: all;
}
</style>
