<template>
  <el-drawer :model-value="modelValue" :title="`按单佣金 · ${customer?.name || ''}`" size="min(680px, 100vw)"
             @update:model-value="$emit('update:modelValue', $event)">
    <div class="pricing-detail" v-loading="loading" :aria-busy="loading">
      <p class="pricing-note">P4 在全民营销小程序中为客户 / 品牌定价，P1–P4 的每单金额均由 P4 自定义。本页查看已保存的配置。</p>
      <div v-if="error" class="pricing-error" role="alert">
        <p>佣金配置加载失败，请重试。</p>
        <el-button @click="load">重新加载</el-button>
      </div>
      <template v-else-if="pricing">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="客户 / 品牌">{{ pricing.customerName || customer?.name || '—' }}</el-descriptions-item>
          <el-descriptions-item label="推荐来源">{{ customer?.referrerName || (customer?.referrerId ? `伙伴 #${customer.referrerId}` : '暂无推荐伙伴') }}</el-descriptions-item>
          <el-descriptions-item label="承接云仓">{{ customer?.assignedWarehouseName || '尚未分派' }}</el-descriptions-item>
          <el-descriptions-item v-if="customer?.attributionNote" label="归因记录">{{ customer.attributionNote }}</el-descriptions-item>
            <el-descriptions-item label="定价 P4">{{ ownerLabel }}</el-descriptions-item>
          <el-descriptions-item label="配置状态">
            <el-tag :type="pricing.configured ? 'success' : 'info'">{{ pricing.configured ? '已配置' : '待 P4 配置' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="pricing.configured">
          <h3>每单金额 <span>元 / 单</span></h3>
          <dl class="pricing-amounts">
            <div><dt>总佣金单价</dt><dd>{{ perOrder(pricing.totalPerOrder) }}</dd></div>
            <div><dt>P4 本人金额</dt><dd>{{ perOrder(pricing.ownerPerOrder) }}</dd></div>
            <div><dt>其他受益人合计</dt><dd>{{ perOrder(pricing.allocatedPerOrder) }}</dd></div>
            <div class="company-amount"><dt>公司剩余额</dt><dd>{{ perOrder(pricing.companyPerOrder ?? pricing.remainingPerOrder) }}</dd></div>
          </dl>
          <p class="pricing-hint">公司剩余额 = 总佣金单价 − P4 本人金额 − 其他受益人合计。</p>

          <h3>其他受益人 <span>{{ pricing.beneficiaries?.length || 0 }} / 2 人</span></h3>
          <el-table :data="pricing.beneficiaries || []" border empty-text="未分配给其他受益人">
            <el-table-column label="伙伴" min-width="150">
              <template #default="{ row }">{{ row.promoterName || `伙伴 #${row.promoterId}` }}</template>
            </el-table-column>
            <el-table-column prop="positionCode" label="角色" width="80" />
            <el-table-column label="元 / 单" width="120" align="right">
              <template #default="{ row }"><span class="amount">{{ perOrder(row.amountPerOrder) }}</span></template>
            </el-table-column>
          </el-table>
          <p class="pricing-hint">最近更新：{{ pricing.updatedAt || '—' }}</p>
        </template>
        <el-empty v-else :description="pricing.ownerPromoterId ? '尚未设置按单金额' : '暂无可定价的 P4 归属'">
          <p class="empty-hint">{{ pricing.ownerPromoterId ? '请由该客户所属 P4 在小程序中填写总佣金、本人金额及其他受益人的每单金额。' : '请先核实客户的推荐伙伴及所属关系，再由对应 P4 在小程序中配置。' }}</p>
        </el-empty>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { mktCustomerApi } from '@/api/marketing'

const props = defineProps({ modelValue: Boolean, customer: { type: Object, default: null } })
defineEmits(['update:modelValue'])
const loading = ref(false)
const error = ref(false)
const pricing = ref(null)
let requestId = 0
const ownerLabel = computed(() => pricing.value?.ownerPromoterId
  ? `${pricing.value.ownerName || '伙伴'} · P4 #${pricing.value.ownerPromoterId}` : '暂未关联')
const perOrder = (value) => value == null || value === '' || !Number.isFinite(Number(value)) ? '—' : Number(value).toFixed(3)

async function load() {
  if (!props.modelValue || !props.customer?.id) return
  const current = ++requestId
  loading.value = true
  error.value = false
  pricing.value = null
  try {
    const result = await mktCustomerApi.pricing(props.customer.id)
    if (current === requestId) pricing.value = result
  } catch {
    if (current === requestId) error.value = true
  } finally {
    if (current === requestId) loading.value = false
  }
}

watch(() => [props.modelValue, props.customer?.id], () => {
  ++requestId
  if (props.modelValue) load()
  else { loading.value = false; pricing.value = null; error.value = false }
}, { immediate: true })
</script>

<style scoped>
.pricing-detail { min-height: 200px; }
.pricing-note { margin: 0 0 20px; color: var(--el-text-color-regular); line-height: 1.7; }
h3 { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin: 28px 0 12px; font-size: 15px; }
h3 span { color: var(--el-text-color-regular); font-size: 12px; font-weight: 400; }
.pricing-amounts { margin: 0; }
.pricing-amounts > div { display: flex; justify-content: space-between; gap: 20px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-light); }
.pricing-amounts dt { color: var(--el-text-color-regular); }
.pricing-amounts dd { margin: 0; color: var(--el-text-color-primary); font-variant-numeric: tabular-nums; }
.company-amount { font-weight: 600; }
.amount { font-variant-numeric: tabular-nums; }
.pricing-hint, .empty-hint { color: var(--el-text-color-regular); font-size: 12px; line-height: 1.7; }
.empty-hint { max-width: 34em; margin: 0; text-align: center; }
.pricing-error { margin: 24px 0; }
.pricing-error p { color: var(--el-color-danger); }
</style>
