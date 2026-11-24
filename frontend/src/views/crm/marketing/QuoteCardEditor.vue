<template>
  <div v-loading="busy" class="editor">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button v-if="error" @click="load">重新读取最新版本</el-button>
    <el-form v-if="card" label-position="top" :disabled="busy || !editable">
      <p>版本 {{ version }} · 金额为人民币，免费项目填 0。留空的价格在比价时显示为待确认。</p>
      <el-form-item label="报价适用范围、有效期和附加费用" required><el-input v-model="card.scopeNote" type="textarea" maxlength="500" /></el-form-item>
      <QuoteTariffEditor v-if="schema.tariff?.length" :sections="schema.tariff" :cells="card.tariff" :editable="editable" />
      <h3>仓内操作、仓租及开票</h3>
      <div class="grid"><el-form-item v-for="col in schema.fields" :key="col.key" :label="col.label + '（' + col.unit + '）'">
        <el-input-number v-model="card.fields[col.key]" :min="0" :controls="false" />
      </el-form-item></div>
      <h3>其他承运商及通用协议价</h3>
      <p>已录入按地区报价的承运商，系统优先使用标准报价明细。</p>
      <article v-for="(carrier,index) in card.carriers" :key="index">
        <div class="carrier"><el-select v-model="carrier.name" filterable allow-create default-first-option placeholder="选择或输入承运商"><el-option v-for="name in schema.couriers" :key="name" :label="name" :value="name" /></el-select>
        <el-button v-if="editable" link type="danger" @click="card.carriers.splice(index,1)">移除此承运商</el-button></div>
        <div class="grid"><el-form-item v-for="col in schema.weights" :key="col.key" :label="col.label + '（元/票）'"><el-input-number v-model="carrier.rates[col.key]" :min="0" :controls="false" /></el-form-item></div>
        <el-form-item label="超过 3 kg 的计费方式"><el-radio-group v-model="carrier.heavyRule"><el-radio value="labelKg">面单费加公斤价</el-radio><el-radio value="firstStep">首重加续重</el-radio></el-radio-group></el-form-item>
        <div class="grid"><el-form-item v-for="col in heavyColumns(carrier.heavyRule)" :key="col[0]" :label="col[1]"><el-input-number v-model="carrier[col[0]]" :min="0" :controls="false" /></el-form-item></div>
        <el-form-item label="承运范围及附加条款"><el-input v-model="carrier.scopeNote" type="textarea" maxlength="500" /></el-form-item>
      </article>
      <el-button v-if="editable" @click="card.carriers.push({name:'',rates:{},heavyRule:'labelKg',scopeNote:''})">添加承运商</el-button>
      <h3>增值服务首件单价</h3>
      <div class="grid"><el-form-item v-for="col in schema.services" :key="col.key" :label="col.label + '（' + col.unit + '）'"><el-input-number v-model="card.services[col.key]" :min="0" :controls="false" /></el-form-item></div>
      <el-button v-if="editable" type="primary" @click="save">保存价卡</el-button>
    </el-form>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { hasPermission } from '@/utils/permission'
import QuoteTariffEditor from './QuoteTariffEditor.vue'
import { quoteApi } from '@/api/warehouseQuotes'

const props = defineProps({warehouseId:{type:[Number,String],required:true},projectId:{type:[Number,String],required:true}})
const editable = computed(() => hasPermission('crm:marketing:warehouse:edit'))
const busy = ref(false), error = ref(''), card = ref(null), schema = ref({}), version = ref(0)
const steps = [['heavyFirstKg','首重（kg）'],['heavyFirstFee','首重价（元）'],['heavyStepKg','续重步长（kg）'],['heavyStepFee','每步续重价（元）']]
const linear = [['heavyLabelFee','面单费（元）'],['heavyPerKg','全部计费重量单价（元/kg）']]
function heavyColumns(mode) { return [...(mode === 'firstStep' ? steps : linear),['heavyMaxKg','最高可寄重量（kg）']] }
function display(result) {
  card.value = {...result.card,tariff:result.card.tariff||{}}
  for (const carrier of card.value.carriers) carrier.heavyRule ||= 'labelKg'
  schema.value = result.schema
  version.value = result.version
}
async function load() {
  busy.value = true; error.value = ''
  try { display(await quoteApi.card(props.warehouseId,props.projectId)) }
  catch (e) { error.value = e.message || '价卡加载失败' }
  finally { busy.value = false }
}
async function save() {
  if (busy.value || !editable.value) return
  busy.value = true; error.value = ''
  try {
    const draft = JSON.parse(JSON.stringify(card.value, (key,value) => value == null ? undefined : value))
    for (const carrier of draft.carriers) {
      for (const [key] of (carrier.heavyRule === 'firstStep' ? linear : steps)) delete carrier[key]
    }
    display(await quoteApi.save(props.warehouseId,props.projectId,{version:version.value,card:draft}))
    ElMessage.success('价卡已保存')
  } catch (e) { error.value = e.message || '价卡保存失败' }
  finally { busy.value = false }
}
onMounted(load)
</script>
<style scoped>
.editor p { color:var(--el-text-color-secondary) }
.grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(160px,1fr)); gap:0 14px }
.grid :deep(.el-input-number) { width:100% }
article { border:1px solid var(--el-border-color); padding:15px; margin-bottom:12px; border-radius:8px }
.carrier { display:flex; gap:16px; margin-bottom:16px }
.carrier .el-select { max-width:280px }
</style>
