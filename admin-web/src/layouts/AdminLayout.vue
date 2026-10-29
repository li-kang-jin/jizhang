<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { adminChangePasswordApi } from '@/api/admin'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const collapsed = ref(false)
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')

const menus = [
  { path: '/dashboard', title: '数据看板', icon: 'DataLine' },
  { path: '/users', title: '用户管理', icon: 'User' },
  { path: '/books', title: '账本管理', icon: 'Notebook' },
  { path: '/accounts', title: '账户管理', icon: 'CreditCard' },
  { path: '/transactions', title: '交易流水', icon: 'Tickets' }
]

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    auth.logout()
    router.push({ name: 'login' })
  } catch {
    // 用户取消，无需处理
  }
}

/* ---------------- 修改密码 ---------------- */

const pwdDialogVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref<FormInstance>()

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度需在 6-32 之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      trigger: 'blur',
      validator: (_rule: unknown, value: string, callback: (error?: string | Error) => void) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      }
    }
  ]
}

function openPwdDialog() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdDialogVisible.value = true
}

async function submitPwdChange() {
  if (!pwdFormRef.value) {
    return
  }
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  pwdLoading.value = true
  try {
    await adminChangePasswordApi({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    pwdDialogVisible.value = false
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    auth.logout()
    router.push({ name: 'login' })
  } catch {
    // 错误提示已在 axios 拦截器中统一处理
  } finally {
    pwdLoading.value = false
  }
}
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '210px'" class="sidebar">
      <div class="logo" :class="{ collapsed }">
        <el-icon :size="22"><Wallet /></el-icon>
        <span v-show="!collapsed">记账系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#001529"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        class="menu"
      >
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
          <el-icon><component :is="menu.icon" /></el-icon>
          <template #title>{{ menu.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button text class="collapse-btn" @click="collapsed = !collapsed">
            <el-icon :size="18"><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          </el-button>
          <div class="header-title">{{ currentTitle }}</div>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="user-trigger">
              <el-icon><UserFilled /></el-icon>
              <span>{{ auth.user?.nickname || auth.user?.username || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ auth.user?.username }}（{{ auth.user?.role }}）
                </el-dropdown-item>
                <el-dropdown-item divided @click="openPwdDialog">修改密码</el-dropdown-item>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog
      v-model="pwdDialogVisible"
      title="修改密码"
      width="420px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="88px"
        @keyup.enter="submitPwdChange"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" placeholder="请输入原密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" placeholder="6-32 位字符" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitPwdChange">确 定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  background-color: #001529;
  transition: width 0.2s ease;
  overflow: hidden;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 60px;
  padding: 0 20px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.logo.collapsed {
  justify-content: center;
  padding: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.collapse-btn {
  color: #606266;
}

.menu {
  flex: 1;
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px;
  background-color: #fff;
  border-bottom: 1px solid #e6e8eb;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
  outline: none;
}

.main {
  padding: 0;
  overflow-y: auto;
  background-color: var(--page-bg);
}
</style>
