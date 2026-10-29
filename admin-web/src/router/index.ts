import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    /** 无需登录即可访问 */
    public?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/change-password',
    name: 'changePassword',
    component: () => import('@/views/ForcePasswordView.vue'),
    meta: { title: '修改初始密码' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '数据看板', icon: 'DataLine' }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/UsersView.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'books',
        name: 'books',
        component: () => import('@/views/BooksView.vue'),
        meta: { title: '账本管理', icon: 'Notebook' }
      },
      {
        path: 'accounts',
        name: 'accounts',
        component: () => import('@/views/AccountsView.vue'),
        meta: { title: '账户管理', icon: 'CreditCard' }
      },
      {
        path: 'transactions',
        name: 'transactions',
        component: () => import('@/views/TransactionsView.vue'),
        meta: { title: '交易流水', icon: 'Tickets' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const mustChangePassword = auth.isLogin && auth.user?.mustChangePassword === 1
  if (mustChangePassword && to.name !== 'changePassword') {
    return { name: 'changePassword' }
  }
  if (!mustChangePassword && to.name === 'changePassword') {
    // 已经改过密的人不需要停在强制改密页，改密走顶部菜单里的常规入口
    return { path: '/dashboard' }
  }

  if (to.name === 'login' && auth.isLogin) {
    return { path: mustChangePassword ? '/change-password' : '/dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 记账系统管理后台` : '记账系统管理后台'
})

export default router
