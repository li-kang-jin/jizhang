<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormItemRule, FormRules } from 'element-plus'
import { adminChangePasswordApi } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirm: FormItemRule['validator'] = (_rule, value, callback) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 32, message: '新密码长度需在 8-32 位之间', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '新密码至少包含字母和数字', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value === form.oldPassword) {
          callback(new Error('新密码不能与当前密码相同'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function handleSubmit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await adminChangePasswordApi({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    ElMessage.success('密码修改成功，请用新密码重新登录')
    auth.logout()
    router.replace({ name: 'login' })
  } catch {
    // 错误提示已在 axios 拦截器中统一处理
  } finally {
    loading.value = false
  }
}

function handleLogout() {
  auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="pwd-page">
    <div class="pwd-card">
      <div class="brand">
        <el-icon :size="28" color="#e6a23c"><Lock /></el-icon>
        <h2>请修改初始密码</h2>
        <p>{{ auth.user?.username || '当前账号' }}</p>
      </div>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="当前账号仍在使用初始密码，修改后才能继续使用后台。"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        class="pwd-form"
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" placeholder="请输入当前密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" placeholder="至少 8 位，含字母和数字" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>

        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleSubmit">
          修改并重新登录
        </el-button>
      </el-form>

      <div class="pwd-footer">
        <span>修改成功后，其他已登录设备也会同时退出。</span>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pwd-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: linear-gradient(135deg, #1f3a5f 0%, #2c5282 45%, #409eff 100%);
}

.pwd-card {
  width: 440px;
  padding: 32px 36px 24px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.18);
}

.brand {
  margin-bottom: 18px;
  text-align: center;
}

.brand h2 {
  margin: 10px 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
}

.brand p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.pwd-form {
  margin-top: 18px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.pwd-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  color: #909399;
  font-size: 12px;
}
</style>
