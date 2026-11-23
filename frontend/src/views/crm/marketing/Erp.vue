<template>
  <div class="page-container mkt-page">
    <div class="search-bar erp-head-bar">
      <el-form :inline="true" class="erp-warehouse-form">
        <el-form-item label="加盟云仓">
          <el-select v-model="warehouseId" placeholder="选择加盟云仓" style="width: 260px" @change="loadWarehouse">
            <el-option v-for="w in warehouses" :key="w.id" :label="`${w.name} · ${w.code}`" :value="String(w.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-radio-group v-model="track" aria-label="单量与ERP接入轨道" class="track-switcher">
        <el-radio-button value="yunshan">☁️ 云山发货量同步</el-radio-button>
        <el-radio-button value="webhook">🔗 通用 ERP Webhook 接入</el-radio-button>
      </el-radio-group>
    </div>

    <el-alert v-if="error" type="error" :closable="false" :title="error" class="mb" />

    <template v-if="warehouseId">
      <template v-if="track === 'yunshan'">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          class="mb"
          title="计佣口径说明：园区签合同按票计佣并纳入服务费账单；云仓直签合同仅统计运营票数，按实收平台费计佣。"
        />
        <YunshanConnectionPanel
          :key="`conn-${warehouseId}`"
          :warehouse-id="warehouseId"
          :warehouse-name="warehouseName"
          @changed="yunshanPanel?.refresh()"
        />
        <CustomerOperationWarehousePanel
          ref="opsPanel"
          :key="warehouseId"
          :warehouse-id="warehouseId"
          :warehouse-name="warehouseName"
          @select="focus = $event"
          @configure="configureFor"
          @linked="afterLinked"
        />
        <YunshanVolumePanel
          ref="yunshanPanel"
          class="mb"
          :key="warehouseId"
          :warehouse-id="warehouseId"
          :warehouse-name="warehouseName"
          :focus="focus"
          @changed="opsPanel?.reload()"
        />
      </template>

      <GeneralErpPanel
        v-else
        :key="`erp-${warehouseId}`"
        :warehouse-id="warehouseId"
        :warehouse-name="warehouseName"
        :warehouse="warehouse"
        :onboarding="isOnboardingRoute"
        @updated="loadWarehouse"
      />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { mktYunshanApi } from '@/api/marketing'
import CustomerOperationWarehousePanel from './CustomerOperationWarehousePanel.vue'
import YunshanVolumePanel from './YunshanVolumePanel.vue'
import YunshanConnectionPanel from './YunshanConnectionPanel.vue'
import GeneralErpPanel from './GeneralErpPanel.vue'

const route = useRoute()
const warehouses = ref([])
const warehouseId = ref(route.query?.warehouseId ? String(route.query.warehouseId) : null)
const warehouse = ref(null)
const error = ref('')
const isOnboardingRoute = computed(() => String(route.query?.onboarding || '') === '1')

function resolvePreferredTrack(wh) {
  if (String(route.query?.onboarding || '') === '1' || route.query?.source === 'webhook') return 'webhook'
  if (route.query?.source === 'yunshan') return 'yunshan'
  if (wh?.orderMode === 'erp') return 'webhook'
  return 'yunshan'
}

const track = ref(resolvePreferredTrack(null))

// 上方“客户与作业仓库”列表选中的行；下方“云仓发货量同步”只显示该客户，为空显示全部
const focus = ref(null)
const opsPanel = ref(null)
const yunshanPanel = ref(null)
const warehouseName = computed(() => warehouse.value?.name || warehouses.value.find(w => String(w.id) === warehouseId.value)?.name || '')
let requestNo = 0

async function configureFor(link) {
  focus.value = link
  await nextTick()
  await yunshanPanel.value?.openMap()
}

// 新增客户关联保存后接着开通发货量同步；云仓还没配置接口时跳过，配置后在列表里点“配置同步”
async function afterLinked(link) {
  if (yunshanPanel.value?.ready) await yunshanPanel.value.openMap(link)
}

async function loadWarehouse() {
  const target = warehouseId.value, request = ++requestNo
  warehouse.value = null
  focus.value = null
  error.value = ''
  if (!target) return
  try {
    const result = await mktYunshanApi.localWarehouse(target)
    if (request === requestNo && target === warehouseId.value) {
      warehouse.value = result
      track.value = resolvePreferredTrack(result)
    }
  } catch (e) {
    if (request === requestNo && target === warehouseId.value) error.value = e.message || '云仓资料加载失败，请刷新重试'
  }
}

watch(() => [route.query?.onboarding, route.query?.source], () => {
  track.value = resolvePreferredTrack(warehouse.value)
})

onBeforeUnmount(() => { requestNo++ })
onMounted(async () => {
  try {
    let pageNo = 1, total = 0
    do {
      const result = await mktYunshanApi.localWarehouses({ pageNo, pageSize: 100 })
      warehouses.value.push(...result.records)
      total = Number(result.total)
      if (!result.records.length) break
      pageNo++
    } while (warehouses.value.length < total)
    if (warehouseId.value) await loadWarehouse()
  } catch (e) {
    error.value = e.message || '云仓列表加载失败，请刷新重试'
  }
})
</script>

<style scoped>
.mb { margin: 12px 0; }
.erp-head-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 14px;
}
.erp-warehouse-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.track-switcher {
  flex-shrink: 0;
  max-width: 100%;
}
@media (max-width: 767px) {
  .erp-warehouse-form { width: 100%; }
  .track-switcher { width: 100%; }
}
</style>
