<template>
  <el-button v-if="isAdmin" @click="open">提现审批人设置</el-button>
  <el-dialog v-model="form.visible" title="提现审批人设置" width="min(640px,94vw)" :close-on-click-modal="!form.saving" :close-on-press-escape="!form.saving" :show-close="!form.saving" @closed="close">
    <el-alert type="info" :closable="false" title="将同一位审批人的后台账号与小程序伙伴身份关联，在两端办理同一条提现审批流程。" />
    <p class="hint">先在 <router-link to="/budget/flow">审批流程</router-link> 将「伙伴提现」流程的审批节点指定给该后台员工，再保存下方身份关联。配置变更会重新校验，不能绕过指定审批人。</p>
    <el-alert v-if="form.error" :title="form.error" type="error" :closable="false" />
    <el-form v-loading="form.loading" label-position="top" class="identity-form">
      <el-form-item label="后台审批账号" required>
        <el-select v-model="form.username" filterable :disabled="form.loading || form.saving" placeholder="搜索后台员工姓名或登录账号" style="width:100%" @change="form.confirmed = false">
          <el-option v-for="u in form.users" :key="u.id" :value="u.username" :label="`${u.nickname || '未命名'} · ${u.username} · ${maskPhone(u.phone)}`" />
        </el-select>
      </el-form-item>
      <el-form-item label="同一审批人的小程序伙伴身份" required>
        <el-select v-model="form.promoterId" filterable remote :remote-method="searchPartners" :loading="form.searching" :disabled="form.loading || form.saving" placeholder="搜索伙伴姓名、手机号或邀请码" style="width:100%" @change="form.confirmed = false">
          <el-option v-for="p in form.partners" :key="p.id" :value="Number(p.id)" :label="`${p.name || '未命名'} · ${p.phone || '未绑定手机号'} · #${p.id}`" />
        </el-select>
      </el-form-item>
      <el-alert v-if="form.searchError" :title="form.searchError" type="error" :closable="false" />
      <el-descriptions v-if="selectedUser || selectedPartner" :column="1" border>
        <el-descriptions-item label="后台员工">{{ selectedUser?.nickname || '未选中' }} · {{ selectedUser?.username || '—' }} · {{ maskPhone(selectedUser?.phone) }}</el-descriptions-item>
        <el-descriptions-item label="小程序伙伴">{{ selectedPartner?.name || '未选中' }} · #{{ selectedPartner?.id || '—' }} · {{ selectedPartner?.phone || '未绑定手机号' }}</el-descriptions-item>
      </el-descriptions>
      <el-checkbox v-model="form.confirmed" :disabled="form.loading || form.saving" class="identity-confirm">已核对这两个账号属于同一位提现审批人</el-checkbox>
    </el-form>
    <template #footer><el-button :disabled="form.saving" @click="close">取消</el-button><el-button type="primary" :loading="form.saving" :disabled="!canSave" @click="save">保存审批人</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed, reactive, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { mktWithdrawalApi } from '@/api/marketing'
import { userApi } from '@/api/system'
import { hasPermission } from '@/utils/permission'

const isAdmin = computed(() => hasPermission('ROLE_admin'))
const form = reactive({ visible:false,loading:false,saving:false,searching:false,username:'',promoterId:null,confirmed:false,users:[],partners:[],error:'',searchError:'' })
let request = 0, searchRequest = 0
const selectedUser = computed(() => form.users.find(u => u.username === form.username))
const selectedPartner = computed(() => form.partners.find(p => Number(p.id) === Number(form.promoterId)))
const canSave = computed(() => isAdmin.value && form.visible && !form.loading && !form.saving && selectedUser.value && selectedPartner.value && form.confirmed)
function maskPhone(phone) { const value=String(phone || ''); return /^\d{11}$/.test(value) ? value.slice(0,3)+'****'+value.slice(-4) : (value || '未填写手机号') }
async function open() {
  if (!isAdmin.value || form.saving) return
  const current=++request; searchRequest++
  Object.assign(form,{visible:true,loading:true,searching:false,username:'',promoterId:null,confirmed:false,users:[],partners:[],error:'',searchError:''})
  try {
    const [identity,users] = await Promise.all([mktWithdrawalApi.approvalIdentity(),userApi.list()])
    if(current!==request || !form.visible)return
    Object.assign(form,{users:users || [],username:identity.username || '',promoterId:identity.promoterId ? Number(identity.promoterId) : null})
    let partner=null
    if(identity.promoterId) {
      try { partner=(await mktWithdrawalApi.manualContext(identity.promoterId)).promoter }
      catch(e) { if(current===request && form.visible)form.error='原审批伙伴资料不可用，请重新搜索并选择正确身份' }
    }
    if(current===request && form.visible) form.partners=partner ? [partner] : []
  } catch(e) { if(current===request && form.visible)form.error=e.message || '审批人设置读取失败，请关闭后重试' }
  finally { if(current===request)form.loading=false }
}
function close() {
  if(form.saving)return
  request++;searchRequest++
  Object.assign(form,{visible:false,loading:false,searching:false,confirmed:false,users:[],partners:[]})
}
async function searchPartners(keyword) {
  if(!form.visible || form.saving)return
  const current=++searchRequest,selected=selectedPartner.value
  form.searching=true;form.searchError=''
  try {
    const options=await mktWithdrawalApi.manualOptions(String(keyword || '').trim())
    if(current===searchRequest && form.visible) form.partners=selected && !options.some(p=>Number(p.id)===Number(selected.id)) ? [selected,...options] : options
  } catch(e) { if(current===searchRequest && form.visible)form.searchError=e.message || '伙伴搜索失败，请重试' }
  finally { if(current===searchRequest)form.searching=false }
}
async function save() {
  if(!canSave.value)return
  form.saving=true;form.error=''
  try {
    await mktWithdrawalApi.saveApprovalIdentity({ username:form.username,promoterId:form.promoterId })
    ElMessage.success('提现审批人已保存');form.saving=false;close()
  } catch(e) { form.error=e.message || '保存失败，请核对审批流程与账号';form.confirmed=false }
  finally { form.saving=false }
}
onBeforeUnmount(()=>{request++;searchRequest++})
</script>
<style scoped>
.hint{font-size:13px;color:var(--el-text-color-secondary);line-height:1.6}
.identity-form{margin-top:20px}
.identity-confirm{margin-top:16px}
</style>
