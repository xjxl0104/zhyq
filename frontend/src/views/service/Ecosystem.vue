<template>
  <div class="page-container">
    <el-alert
      title="第三方生态对接为配置占位,正式接入需按厂商文档配置回调与密钥"
      type="warning"
      show-icon
      :closable="false"
      class="tip-alert"
    />

    <el-row :gutter="16">
      <el-col :span="8" v-for="group in groups" :key="group.key">
        <el-card shadow="never" class="group-card">
          <template #header>{{ group.label }}</template>
          <el-form label-width="120px">
            <el-form-item v-for="item in group.items" :key="item.id" :label="labelOf(item.skey)">
              <el-input v-model="item.svalue" :placeholder="item.remark" />
            </el-form-item>
            <el-empty v-if="group.items.length === 0" description="暂无配置项" :image-size="60" />
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <div class="save-bar">
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { settingApi } from '@/api/finance'

const GROUP_DEFS = [
  { key: 'wecom', label: '企业微信', prefix: 'wecom_' },
  { key: 'dingtalk', label: '钉钉', prefix: 'dingtalk_' },
  { key: 'mp', label: '微信小程序', prefix: 'mp_' }
]

const KEY_LABELS = {
  wecom_corp_id: '企业微信 CorpID',
  dingtalk_app_key: '钉钉 AppKey',
  mp_app_id: '微信小程序 AppID'
}

function labelOf(skey) {
  return KEY_LABELS[skey] || skey
}

const groups = reactive(GROUP_DEFS.map(g => ({ ...g, items: [] })))
const saving = ref(false)

async function load() {
  const list = await settingApi.list('ecosystem')
  groups.forEach(group => {
    group.items = list.filter(item => item.skey && item.skey.startsWith(group.prefix))
  })
}

async function save() {
  saving.value = true
  try {
    const all = groups.flatMap(g => g.items)
    await settingApi.batch(all)
    ElMessage.success('保存成功')
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tip-alert { margin-bottom: 16px; }
.group-card { margin-bottom: 16px; }
.save-bar { margin-top: 8px; }
</style>
