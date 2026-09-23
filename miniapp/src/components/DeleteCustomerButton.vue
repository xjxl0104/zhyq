<template>
  <view class="customer-delete" @click.stop>
    <button class="delete-button" :loading="busy" :disabled="busy || disabled" @click.stop="remove">删除客户</button>
    <view v-if="error" class="delete-error" role="alert">{{ error }}</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { bizApi } from '@/api'

const props = defineProps({ customer: { type: Object, required: true }, disabled: Boolean })
const emit = defineEmits(['deleted'])
const busy = ref(false)
const error = ref('')
async function remove() {
  if (busy.value || props.disabled) return
  const { id, name } = props.customer
  busy.value = true
  error.value = ''
  try {
    const result = await new Promise((resolve, reject) => uni.showModal({
      title: '删除推荐客户',
      content: `确认删除「${name}」？删除后将移出客户列表并释放推荐锁定。已关联合同、订单或佣金的客户无法删除。`,
      confirmText: '确认删除', cancelText: '取消', confirmColor: '#a3293e',
      success: resolve, fail: () => reject(new Error('无法打开确认窗口，请重试'))
    }))
    if (!result.confirm) return
    await bizApi.removeCustomer(id)
    uni.showToast({ title: '客户已删除' })
    emit('deleted', id)
  } catch (e) {
    error.value = e.message || '删除失败，请稍后重试'
  } finally { busy.value = false }
}
</script>

<style scoped>
.customer-delete { margin-top: 20rpx; }
.delete-button { min-height: 88rpx; margin: 0; padding: 0 24rpx; color: #a3293e; border: 1rpx solid #ecc9d0; border-radius: 16rpx; background: #fff; font-size: 28rpx; line-height: 88rpx; }
.delete-button::after { border: 0; }
.delete-button[disabled] { opacity: .5; }
.delete-error { margin-top: 14rpx; color: #a3293e; font-size: 28rpx; line-height: 1.6; overflow-wrap: anywhere; }
</style>
