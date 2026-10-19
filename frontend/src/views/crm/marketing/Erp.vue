<template>
  <div class="page-container mkt-page">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="加盟云仓">
          <el-select v-model="warehouseId" placeholder="选择加盟云仓" style="width: 260px" @change="loadWarehouse">
            <el-option v-for="w in warehouses" :key="w.id" :label="`${w.name} · ${w.code}`" :value="String(w.id)" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="error" type="error" :closable="false" :title="error" class="mb" />
    <template v-if="warehouseId">
      <CustomerOperationWarehousePanel :key="warehouseId" :warehouse-id="warehouseId" :warehouse-name="warehouseName" />
      <el-collapse v-model="sourcePanels" class="mb">
        <el-collapse-item title="云仓发货量同步（单独配置数据来源）" name="yunshan">
          <YunshanVolumePanel v-if="sourcePanels.includes('yunshan')" :key="warehouseId" :warehouse-id="warehouseId" :warehouse-name="warehouseName" />
        </el-collapse-item>
      </el-collapse>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { mktYunshanApi } from '@/api/marketing'
import CustomerOperationWarehousePanel from './CustomerOperationWarehousePanel.vue'
import YunshanVolumePanel from './YunshanVolumePanel.vue'

const route = useRoute()
const warehouses = ref([])
const warehouseId = ref(route.query.warehouseId ? String(route.query.warehouseId) : null)
const warehouse = ref(null)
const sourcePanels = ref([])
const error = ref('')
const warehouseName = computed(() => warehouse.value?.name || warehouses.value.find(w => String(w.id) === warehouseId.value)?.name || '')
let requestNo = 0

async function loadWarehouse() {
  const target = warehouseId.value, request = ++requestNo
  warehouse.value = null
  error.value = ''
  if (!target) return
  try {
    const result = await mktYunshanApi.localWarehouse(target)
    if (request === requestNo && target === warehouseId.value) warehouse.value = result
  } catch (e) {
    if (request === requestNo && target === warehouseId.value) error.value = e.message || '云仓资料加载失败，请刷新重试'
  }
}

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
</style>
