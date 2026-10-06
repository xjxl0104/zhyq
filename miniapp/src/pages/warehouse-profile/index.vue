<template>
  <view class="page-wrap">
    <view class="card">
      <view class="wh-title">个人信息</view>
      <view v-if="loading && !ready" class="wh-note" role="status">正在读取个人信息…</view>
      <template v-if="ready">
        <view v-if="profile.profileEditable" class="wh-note">云仓名称和联系方式可稍后补充，工作台已可使用。</view>
        <view v-if="!profile.profileEditable" class="profile-notice">
          资质已完成审核；如需变更资料，请联系园区运营。{{ dirty ? '当前填写的内容尚未保存。' : '' }}
        </view>
        <view v-if="loading" class="wh-note" role="status">正在更新资料状态…</view>

        <label class="wh-label" for="warehouse-name">云仓名称</label>
        <input id="warehouse-name" v-model="form.warehouseName" class="wh-field" maxlength="100" placeholder="填写云仓名称，可稍后补充" placeholder-class="profile-placeholder" :disabled="!profile.profileEditable || busy" aria-label="云仓名称" />
        <label class="wh-label" for="contact-name">联系人</label>
        <input id="contact-name" v-model="form.name" class="wh-field" maxlength="32" placeholder="填写联系人姓名，可稍后补充" placeholder-class="profile-placeholder" :disabled="!profile.profileEditable || busy" aria-label="联系人" />
        <label class="wh-label" for="contact-phone">联系电话</label>
        <input id="contact-phone" v-model="form.phone" class="wh-field" type="number" maxlength="11" placeholder="填写 11 位手机号，可稍后补充" placeholder-class="profile-placeholder" :disabled="!profile.phoneEditable || !profile.profileEditable || busy" aria-label="联系电话" />
        <view v-if="profile.profileEditable && !profile.phoneEditable" class="wh-note">此号码用于微信登录绑定；如需变更，请联系园区运营。</view>
        <view v-if="profile.profileEditable" class="wh-note">保存后仅更新个人信息，不会提交加盟审核。</view>
      </template>

      <view v-if="error" class="wh-error" role="alert">{{ error }}</view>
      <button v-if="loadError && !loading" class="wh-secondary" :disabled="busy" @click="load">重新读取</button>
      <button v-if="ready && profile.profileEditable" class="btn" :loading="busy" :disabled="busy || loading || !dirty" @click="save">保存个人信息</button>
      <button class="wh-secondary back-button" :disabled="busy" @click="goBack">返回工作台</button>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'

const form = reactive({ warehouseName: '', name: '', phone: '' })
const profile = reactive({ profileEditable: false, phoneEditable: false })
const loading = ref(false)
const ready = ref(false)
const busy = ref(false)
const error = ref('')
const loadError = ref(false)
const savedValues = ref('')
const values = () => ({ warehouseName: form.warehouseName.trim(), name: form.name.trim(), phone: form.phone.trim() })
const dirty = computed(() => ready.value && JSON.stringify(values()) !== savedValues.value)

function applyProfile(data, preserveDraft = false) {
  Object.assign(profile, data)
  if (!preserveDraft) {
    form.warehouseName = data.warehouseName || ''
    form.name = data.name || ''
    form.phone = data.phone || ''
    savedValues.value = JSON.stringify(values())
  }
  ready.value = true
}

async function load() {
  if (loading.value || busy.value) return
  loading.value = true
  error.value = ''
  loadError.value = false
  try {
    const data = await warehouseApi.me()
    // onShow may run after switching apps or returning from another page.
    // Refresh permissions but retain any edits, including edits made during the request.
    applyProfile(data, dirty.value)
  } catch (e) {
    error.value = e.message || '个人信息读取失败，请重试'
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!ready.value || busy.value || loading.value || !profile.profileEditable || !dirty.value) return
  error.value = ''
  const data = values()
  if (!profile.phoneEditable) delete data.phone
  if (data.phone && !/^1\d{10}$/.test(data.phone)) {
    error.value = '请输入正确的 11 位手机号，或留空稍后补充'
    return
  }
  busy.value = true
  try {
    applyProfile(await warehouseApi.saveMe(data))
    loadError.value = false
    uni.showToast({ title: '个人信息已保存', icon: 'success' })
  } catch (e) {
    error.value = e.message || '保存失败，填写的内容已保留，请重试'
  } finally {
    busy.value = false
  }
}

function goBack() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/warehouse-dashboard/index' }) })
}

onShow(load)
</script>

<style scoped>
@import '../../styles/warehouse.css';
.profile-placeholder { color: #58617d; }
.profile-notice { margin-top: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #eef1ff; color: #283252; font-size: 28rpx; line-height: 1.7; }
.back-button { margin-top: 20rpx; }
.wh-field[disabled] { background: #f4f6fb; color: #58617d; }
</style>
