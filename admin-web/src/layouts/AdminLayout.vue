<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')

const menus = [
  { path: '/dashboard', title: '数据看板', icon: 'DataLine' },
  { path: '/users', title: '用户管理', icon: 'User' },
  { path: '/books', title: '账本管理', icon: 'Notebook' },
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
</script>

<template>
  <el-container class="layout">
    <el-aside width="210px" class="sidebar">
      <div class="logo">
        <el-icon :size="22"><Wallet /></el-icon>
        <span>记账系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        class="menu"
      >
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
          <el-icon><component :is="menu.icon" /></el-icon>
          <span>{{ menu.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
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
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
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
