<template>
  <section v-if="canQuery" class="table-card connection-panel" aria-labelledby="yunshan-connection-title">
    <div class="toolbar">
      <h2 id="yunshan-connection-title" class="title">云仓发货量接口</h2>
      <el-tag :type="config?.configured ? 'success' : 'warning'">{{ statusText }}</el-tag>
      <el-button :loading="loading.config" @click="load">刷新</el-button>
      <el-button v-if="canConfig" type="primary" @click="openDialog">{{ config?.source === 'WAREHOUSE' ? '修改接口' : '配置接口' }}</el-button>
    </div>
    <p class="hint">{{ warehouseName || '当前云仓' }}对接一次发货量接口即可，该云仓下的所有客户共用，不需要重复对接；没有客户时也可以先对接。</p>
    <el-alert v-if="error" type="error" :closable="false" :title="error" />
    <p v-if="config?.configured" class="config-note">接口：{{ config.baseUrl }}<span v-if="config.appId"> · 应用：{{ config.appId }}</span></p>
    <el-alert v-else-if="config" type="info" :closable="false" title="该云仓还没有配置发货量接口，请先配置，再到下方为客户配置发货量同步。" />

    <el-dialog v-model="dialog.visible" title="配置云仓发货量接口" width="min(560px, calc(100vw - 32px))" :close-on-click-modal="false" @closed="reset">
      <p class="hint">云仓：{{ warehouseName || '当前所选云仓' }}。保存时会先用该接口读取授权目录验证，验证通过才会保存；凭证加密保存，不会再显示。</p>
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="接口基础地址" required><el-input v-model="form.baseUrl" :disabled="saving" maxlength="500" placeholder="接口提供方的基础地址（支持云山协议）" /></el-form-item>
        <el-form-item label="App ID" required><el-input v-model="form.appId" :disabled="saving" maxlength="256" autocomplete="off" /></el-form-item>
        <el-form-item label="RSA 公钥" required><el-input v-model="form.publicKey" :disabled="saving" type="textarea" :rows="4" maxlength="8192" placeholder="粘贴接口提供方的 RSA 公钥" /></el-form-item>
      </el-form>
      <el-alert v-if="dialog.error" type="error" :closable="false" :title="dialog.error" />
      <template #footer>
        <el-button :disabled="saving" @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">验证并保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { mktYunshanApi } from '@/api/marketing'
import { hasPermission } from '@/utils/permission'

const props = defineProps({ warehouseId: { type: [String, Number], required: true }, warehouseName: { type: String, default: '' } })
const emit = defineEmits(['changed'])
const canQuery = computed(() => hasPermission('crm:marketing:erp:query'))
const canConfig = computed(() => hasPermission('crm:marketing:erp:config'))
const config = ref(null), error = ref(''), saving = ref(false)
const loading = reactive({ config: false })
const dialog = reactive({ visible: false, error: '' })
const form = reactive({ baseUrl: '', appId: '', publicKey: '' })
const statusText = computed(() => !config.value ? '加载中' : config.value.configured ? '已配置' : '未配置')
const canSave = computed(() => canConfig.value && !saving.value && form.baseUrl.trim() && form.appId.trim() && form.publicKey.trim())
let disposed = false, version = 0

async function load() {
  if (!canQuery.value || !props.warehouseId) return
  const current = ++version, warehouse = props.warehouseId
  loading.config = true; error.value = ''
  try {
    const result = await mktYunshanApi.config(warehouse)
    if (!disposed && current === version) config.value = result
  } catch (e) { if (!disposed && current === version) error.value = e.message || '接口配置加载失败，请重试。' }
  finally { if (current === version) loading.config = false }
}
function openDialog() {
  reset(); form.baseUrl = config.value?.baseUrl || ''
  dialog.visible = true
}
function reset() { Object.assign(form, { baseUrl: '', appId: '', publicKey: '' }); dialog.error = '' }
async function save() {
  if (!canSave.value) return
  const warehouse = props.warehouseId
  saving.value = true; dialog.error = ''
  try {
    const result = await mktYunshanApi.saveConnection(warehouse, { mode: 'CUSTOM', baseUrl: form.baseUrl.trim(), appId: form.appId.trim(), publicKey: form.publicKey.trim() })
    if (disposed || warehouse !== props.warehouseId) return
    ElMessage.success(`接口已保存，授权货主 ${result?.ownerCount ?? 0} 个`)
    dialog.visible = false
    await load(); emit('changed')
  } catch (e) { if (!disposed && warehouse === props.warehouseId) dialog.error = e.message || '接口保存失败，请核对后重试。' }
  finally { saving.value = false }
}
watch(() => props.warehouseId, () => { config.value = null; dialog.visible = false; reset(); load() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; version++ })
defineExpose({ reload: load })
</script>

<style scoped>
.connection-panel { min-width: 0; margin-bottom: 12px; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.title { margin: 0 auto 0 0; font-size: 16px; font-weight: 600; }
.hint { margin: 12px 0; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; }
.config-note { margin: 8px 0 0; color: var(--el-text-color-regular); font-size: 13px; overflow-wrap: anywhere; }
.connection-panel :deep(.el-alert) { margin: 12px 0; }
</style>
