<template>
  <view class="page-wrap">
    <view v-if="error" class="card"><view class="error">{{ error }}</view><button class="btn ghost" :disabled="loading || saving" @click="reload">重新读取报价</button></view>
    <view v-if="loading" class="card muted">正在读取本仓报价…</view>
    <view v-if="saving" class="card muted">正在保存本仓报价…</view>
    <view v-else-if="schema && card">
      <view class="card"><view class="title">{{ name }} · 报价体系</view><view class="muted">使用人民币报价，留空表示待询价，填 0 表示免费。</view>
        <view class="field">报价范围、有效期及附加费 *</view><textarea class="input" maxlength="500" v-model="card.scopeNote" placeholder="请填写有效期、全国配送范围及例外" />
      </view>
      <WarehouseTariffEditor v-if="schema.tariff && schema.tariff.length" :sections="schema.tariff" :cells="card.tariff" @change="updateTariff" />
      <view class="card"><button class="section-toggle" @click="basicOpen=!basicOpen">仓储与基础作业 <text>{{ basicOpen?'收起':'展开' }}</text></button><view v-if="basicOpen">
        <view v-for="f in schema.fields" :key="f.key" class="line">
          <view>{{ f.label }} <text class="muted">{{ f.unit }}</text></view><input class="price" type="digit" :value="card.fields[f.key]" placeholder="待询价" @input="card.fields[f.key] = $event.detail.value" />
        </view>
      </view>
      </view>
      <view class="card"><button class="section-toggle" @click="genericOpen=!genericOpen">其他承运商及通用协议价 <text>{{ genericOpen?'收起':'展开' }}</text></button><view v-if="genericOpen"><view class="muted">已填写按地区报价的快递，优先使用标准报价明细。</view>
        <view v-for="(c,i) in card.carriers" :key="c.rowKey" class="carrier">
          <view class="field">快递公司</view><input class="input" v-model="c.name" maxlength="32" placeholder="输入快递公司名称" />
          <view v-for="w in schema.weights" :key="w.key" class="line"><view>{{ w.label }} <text class="muted">元/票</text></view><input class="price" type="digit" :value="c.rates[w.key]" placeholder="待询价" @input="c.rates[w.key] = $event.detail.value" /></view>
          <view class="field">超过 3 kg 的计费方式</view>
          <radio-group class="rules" @change="c.heavyRule = $event.detail.value">
            <label><radio value="labelKg" :checked="c.heavyRule !== 'firstStep'" />面单费加整公斤运价</label>
            <label><radio value="firstStep" :checked="c.heavyRule === 'firstStep'" />首重续重</label>
          </radio-group>
          <view v-if="c.heavyRule === 'firstStep'"><view v-for="f in heavySteps" :key="f.key" class="line"><view>{{ f.label }}</view><input class="price" type="digit" :value="c[f.key]" placeholder="待填写" @input="c[f.key] = $event.detail.value" /></view></view>
          <view v-else><view class="line"><view>超过 3 kg 面单费 <text class="muted">元/票</text></view><input class="price" type="digit" v-model="c.heavyLabelFee" placeholder="待询价" /></view>
          <view class="line"><view>超过 3 kg 整公斤运价 <text class="muted">元/kg</text></view><input class="price" type="digit" v-model="c.heavyPerKg" placeholder="待询价" /></view></view>
          <view class="line"><view>最高可寄重量 <text class="muted">kg（选填）</text></view><input class="price" type="digit" v-model="c.heavyMaxKg" placeholder="待填写" /></view>
          <input class="input" v-model="c.scopeNote" maxlength="500" placeholder="适用地区、例外及附加费说明" />
          <button class="remove" @click="card.carriers.splice(i,1)">移除这家快递</button>
        </view>
        <button class="btn ghost" @click="addCarrier">添加快递公司</button>
      </view>
      </view>
      <view class="card"><button class="section-toggle" @click="servicesOpen=!servicesOpen">增值服务首件单价 <text>{{ servicesOpen?'收起':'展开' }}</text></button><view v-if="servicesOpen">
        <view v-for="f in schema.services" :key="f.key" class="line"><view>{{ f.label }} <text class="muted">{{ f.unit }}</text></view><input class="price" type="digit" :value="card.services[f.key]" placeholder="待询价" @input="card.services[f.key] = $event.detail.value" /></view>
      </view>
      </view>
      <view v-if="error" class="error">{{ error }}</view>
      <button class="btn" :loading="saving" :disabled="loading || saving" @click="save">保存本仓报价</button>
    </view>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { whGet, whPut, warehouseToken } from '@/utils/request'
import WarehouseTariffEditor from '@/components/WarehouseTariffEditor.vue'
import { prepareRateCard } from '@/utils/quote-rate-card.mjs'
const schema = ref(null), card = ref(null), name = ref(''), version = ref(null), error = ref(''), loading = ref(false), saving = ref(false)
const basicOpen=ref(false),genericOpen=ref(false),servicesOpen=ref(false)
const heavySteps = [{key:'heavyFirstKg',label:'首重（kg）'},{key:'heavyFirstFee',label:'首重费（元）'},{key:'heavyStepKg',label:'续重单位（kg）'},{key:'heavyStepFee',label:'每续重单位费用（元）'}]
let nextKey = 0, session = '', snapshot = ''
onShow(() => {
  if (!card.value || session !== warehouseToken.get()) {
    card.value = null; schema.value = null; load()
  }
})
function accept(data) {
  schema.value = data.schema; version.value = data.version; name.value = data.warehouseName
  const c = data.card || {}
  card.value = { scopeNote:c.scopeNote || '', tariff:c.tariff || {}, fields:{...c.fields}, services:{...c.services}, carriers:(c.carriers || []).map(v => ({...v,rates:{...v.rates},rowKey:++nextKey})) }
  snapshot = JSON.stringify(card.value)
}
async function load() {
  if (loading.value || saving.value) return
  loading.value = true; error.value = ''; session = warehouseToken.get()
  try { accept(await whGet('/quote/rate-card')) }
  catch(e) { error.value = e.message || '读取本仓报价失败' }
  finally { loading.value = false }
}
function reload() {
  if (!card.value || JSON.stringify(card.value) === snapshot) return load()
  uni.showModal({title:'重新读取报价',content:'当前未保存的编辑将被替换。',success:r => { if (r.confirm) load() }})
}
function updateTariff({id,key,value}) {
  const row=card.value.tariff[id] || (card.value.tariff[id]={})
  row[key]=value
}
function addCarrier() { card.value.carriers.push({name:'',rates:{},heavyLabelFee:'',heavyPerKg:'',scopeNote:'',rowKey:++nextKey}) }
async function save() {
  if (!card.value || saving.value || loading.value) return
  error.value = ''
  if (warehouseToken.get() !== session) { card.value = null; schema.value = null; error.value = '云仓登录身份已变化，请重新读取'; return }
  saving.value = true
  try {
    const body = prepareRateCard(card.value)
    accept(await whPut('/quote/rate-card',{version:version.value,card:body}))
    uni.showToast({title:'报价已保存'})
  } catch(e) { error.value = Number(e.code) === 409 ? '本仓报价已被其他操作更新。请重新读取后再编辑。' : e.message || '保存失败，编辑内容已保留' }
  finally { saving.value = false }
}
</script>
<style scoped>
.section-toggle{display:flex;align-items:center;justify-content:space-between;background:transparent;padding:0;font-size:32rpx;font-weight:650;text-align:left;color:inherit}.section-toggle::after{border:0}.section-toggle text{color:#495edb;font-size:24rpx;font-weight:400}
.rules{display:flex;flex-wrap:wrap;gap:16rpx;margin:18rpx 0}.field,.carrier{margin-top:24rpx}.line{display:flex;align-items:center;justify-content:space-between;gap:16rpx;padding:18rpx 0;border-bottom:1rpx solid var(--park-line)}.price{width:150rpx;text-align:right;padding:10rpx;background:#f7f8fd;border-radius:8rpx}.carrier+.carrier{border-top:2rpx solid var(--park-line);padding-top:20rpx}.remove{color:#a3293e;background:transparent;font-size:26rpx;margin-top:16rpx}.error{color:#a3293e;line-height:1.6}
</style>
