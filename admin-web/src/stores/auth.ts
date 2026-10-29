import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { adminLoginApi } from '@/api/admin'
import { REFRESH_KEY, TOKEN_KEY, USER_KEY } from '@/api/request'
import type { LoginPayload, UserInfo } from '@/types'

function readUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserInfo | null>(readUser())

  const isLogin = computed(() => !!token.value)

  async function login(payload: LoginPayload) {
    const data = await adminLoginApi(payload)
    token.value = data.accessToken
    user.value = data.user
    localStorage.setItem(TOKEN_KEY, data.accessToken)
    localStorage.setItem(REFRESH_KEY, data.refreshToken)
    localStorage.setItem(USER_KEY, JSON.stringify(data.user))
    return data
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, isLogin, login, logout }
})
