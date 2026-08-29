<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="brand">
        <div class="brand-mark"><el-icon :size="26"><OfficeBuilding /></el-icon></div>
        <div class="brand-text">
          <h1>智慧园区管理系统</h1>
          <p>Smart Park Management</p>
        </div>
      </div>

      <el-form :model="form" ref="formRef" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="submit">登 录</el-button>
      </el-form>

      <div class="hint">演示账号:admin / zhyq@2026</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await request.post('/auth/login', form)
    localStorage.setItem('zhyq_token', data.token)
    localStorage.setItem('zhyq_user', data.nickname || data.username)
    // 审批待办按 wf_task.assignee(用户名)过滤,需要原始登录名而非昵称
    localStorage.setItem('zhyq_username', data.username || '')
    ElMessage.success('欢迎回来,' + (data.nickname || data.username))
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background:
    radial-gradient(ellipse 60% 50% at 20% 20%, rgba(99,102,241,0.18), transparent),
    radial-gradient(ellipse 50% 40% at 85% 75%, rgba(34,211,238,0.14), transparent),
    #0f1222;
}
.login-panel {
  width: 400px; background: rgba(255,255,255,0.98); border-radius: 18px;
  padding: 44px 40px 32px; box-shadow: 0 24px 60px rgba(0,0,0,0.35);
}
.brand { display: flex; align-items: center; gap: 14px; margin-bottom: 34px; }
.brand-mark {
  width: 52px; height: 52px; border-radius: 14px; flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  display: flex; align-items: center; justify-content: center; color: #fff;
  box-shadow: 0 6px 16px rgba(79,70,229,0.4);
}
.brand-text h1 { margin: 0; font-size: 20px; color: #111827; letter-spacing: 1px; }
.brand-text p { margin: 2px 0 0; font-size: 12px; color: #9aa1ac; letter-spacing: 2px; }
.login-btn { width: 100%; height: 44px; font-size: 15px; letter-spacing: 6px; margin-top: 4px; }
.hint { margin-top: 18px; text-align: center; font-size: 12px; color: #9aa1ac; }
</style>
