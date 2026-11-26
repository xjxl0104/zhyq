<template>
  <div class="page-container mkt-page">
    <div class="dict-tabs-bar">
      <el-radio-group v-model="activeTab" aria-label="规则与字典页签切换">
        <el-radio-button value="connectors">🔌 外部数据连接器中心 (18通道)</el-radio-button>
        <el-radio-button value="setting">规则参数</el-radio-button>
        <el-radio-button value="position">岗位称号与锁客上限</el-radio-button>
        <el-radio-button value="grade">客户等级字典</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="activeTab === 'connectors'" class="embedded-dict">
      <ConnectorHubPanel />
    </div>

    <div v-else-if="activeTab === 'setting'" class="table-card">
      <div class="toolbar">
        <span class="title">规则参数</span>
        <el-button type="primary" :loading="saving" @click="save">保存参数</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column label="业务参数" width="260">
          <template #default="{ row }">
            <div class="param-label">{{ labelOf(row.skey) }}</div>
            <code class="param-key">{{ row.skey }}</code>
          </template>
        </el-table-column>
        <el-table-column label="当前生效值" width="240">
          <template #default="{ row }">
            <el-input v-model="row.svalue" size="small" :aria-label="labelOf(row.skey)" />
          </template>
        </el-table-column>
        <el-table-column label="口径与填写说明" min-width="320">
          <template #default="{ row }">
            <div>{{ row.remark || hintOf(row.skey) }}</div>
            <div v-if="hintOf(row.skey) && row.remark !== hintOf(row.skey)" class="param-sub-hint">
              {{ hintOf(row.skey) }}
            </div>
          </template>
        </el-table-column>
      </el-table>
      <p class="hint">云仓按单佣金由 P4 在小程序按客户 / 品牌自定义（上限最多 8 人），可在「计佣订单 › 佣金分配」或「客户管理 › 按单佣金」查看。此处管理全局业务规则参数，已生成流水与锁定记录不受影响。</p>
      <p class="hint">提现窗口与自动提现说明：<code>withdraw_window_start_day = 0</code> 表示全月随时可申请提现；<code>auto_withdraw_day = 0</code> 表示关闭每月自动提现。</p>
      <p class="hint">推荐客户直接锁定三个自然月，签约成交后归属长期有效；到期未签约释放回公海。</p>
      <p class="hint">外部数据接口（高德、企查查、广告回调等）的密钥请在「🔌 外部数据连接器中心」页签填写，统一加密保存。</p>
    </div>

    <div v-else-if="activeTab === 'position'" class="embedded-dict">
      <Position />
    </div>

    <div v-else-if="activeTab === 'grade'" class="embedded-dict">
      <Grade />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { mktSettingApi } from '@/api/marketing'
import ConnectorHubPanel from './ConnectorHubPanel.vue'
import Position from './Position.vue'
import Grade from './Grade.vue'

const SETTING_META = {
  withdraw_window_start_day: {
    label: '提现申请开放起始日',
    hint: '0 = 全月随时可申请；1–28 = 每月固定开放起始日'
  },
  withdraw_window_end_day: {
    label: '提现申请开放截止日',
    hint: '填写 1–31；当起始日设为 0（全月开放）时自动忽略截止日'
  },
  auto_withdraw_day: {
    label: '每月自动提现日',
    hint: '填写 0–28：0 = 关闭自动提现；1–28 = 当日 10:00 自动为可提现余额发起申请'
  },
  platform_retain_rate: {
    label: '平台保底留存比例',
    hint: '填写 0～0.50 小数（如 0.05 代表固定留存总佣金的 5%，伙伴链路可分配上限 95%）'
  },
  withdraw_arrival_time: {
    label: '提现处理与到账时效说明',
    hint: '展示在小程序提现页的运营承诺说明（最多 200 字）'
  },
  freeze_days: {
    label: '佣金解冻观察期（天）',
    hint: '出库/收款确认后冻结满该天数进入「可结算」状态（默认 7 天）'
  },
  clawback_days: {
    label: '出库退货扣回观察期（天）',
    hint: '在此天数内发生退款/作废将自动冲正或生成负向扣回流水'
  },
  lease_clawback_days: {
    label: '园区租赁退租扣回期（天）',
    hint: '租赁合同提前退租触发佣金扣回的观察期天数'
  },
  daily_referral_cap: {
    label: '单人每日报备客户上限',
    hint: '防止恶意刷量锁客的每日最大报备户数'
  },
  lock_cooldown_days: {
    label: '公海释放后重锁冷却期（天）',
    hint: '同一伙伴对已释放客户再次报备锁定的冷却天数'
  },
  sea_sla_hours: {
    label: '公海认领首触达保活时限（小时）',
    hint: '认领后须在此小时数内提交首通核实纪要，超时未跟进自动弹回内部公海（默认 72 小时）'
  },
  sea_wh_daily_cap: {
    label: '加盟云仓每日认领公海上限（户）',
    hint: '单个上线加盟云仓每日最多可认领的公海热单户数（默认 5 户）'
  },
  sea_wh_active_cap: {
    label: '加盟云仓在途公海客户上限（户）',
    hint: '单个上线加盟云仓同时跟进未签约的公海客户上限（默认 10 户）'
  },
  sea_admin_active_cap: {
    label: '内部专员在跟公海客户上限（户）',
    hint: '每位招商专员同时在跟的公海客户上限，达到后需先推进或释放才能再认领（默认 30 户，超级管理员不受限）'
  },
  sea_region_lock_province: {
    label: '公海付费 API 定向省钱锁省份',
    hint: '默认填写「广东省」：非本省主体自动跳过付费企查/网店 API 查询（立省 85%+ 预算）；填 ALL 表示不限省'
  },
  sea_checkin_max_meters: {
    label: '地推现场 GPS 打卡最大偏差（米）',
    hint: '专员现场定位距客户高德坐标必须 ≤ 该米数方可提交打卡（默认 500 米，严防异地虚拟打卡）'
  },
  sea_visit_protect_days: {
    label: '500米实拍打卡深度地推保护期（天）',
    hint: '现场 ≤500m 定位+门头实拍打卡通过后，自动解除 72h 弹回并授予的保护期天数（默认 15 天）'
  },
  sea_followup_max_days: {
    label: '首触/打卡后最长未签约跟进天数（天）',
    hint: '完成首通纪要或 15 天打卡保护期满后，连续超过该天数无新跟进且未签合同的僵尸单自动回流公海（默认 30 天）'
  },
  old_channel_exclusive: {
    label: '传统中介渠道互斥开关',
    hint: '1 = 已走传统招商中介渠道的客户不再叠加全民营销佣金；0 = 不互斥'
  },
  override_enabled: {
    label: '团队级差管理津贴开关',
    hint: '1 = 启用上级管理分成能力；0 = 关闭'
  },
  tax_mode: {
    label: '默认提现税务模式',
    hint: '1 = 个税代扣；2 = 灵活用工代征'
  },
  tax_rate: {
    label: '提现综合税率',
    hint: '计算提现税额与税后实付金额的税率参数'
  }
}

const route = useRoute()
const initialTab = ['connectors', 'setting', 'position', 'grade'].includes(String(route?.query?.tab || ''))
  ? String(route.query.tab)
  : 'setting'

const activeTab = ref(initialTab)
const loading = ref(false)
const saving = ref(false)
const list = ref([])

const labelOf = (skey) => SETTING_META[skey]?.label || skey
const hintOf = (skey) => SETTING_META[skey]?.hint || ''

async function load() {
  loading.value = true
  try {
    list.value = ((await mktSettingApi.all()) || []).filter(row => row.skey !== 'ladder_depth')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const payload = list.value.map(r => ({ skey: r.skey, svalue: String(r.svalue ?? '') }))
    const submitFn = mktSettingApi.save || mktSettingApi.update
    await submitFn(payload)
    ElMessage.success('规则参数已保存')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)

defineExpose({ activeTab, list, load, save, labelOf, hintOf })
</script>

<style scoped>
.dict-tabs-bar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.toolbar { display: flex; align-items: center; }
.title { font-weight: 600; margin-right: auto; }
.param-label { font-weight: 500; color: var(--text-title, #111827); }
.param-key { font-size: 11.5px; color: var(--el-text-color-secondary); }
.param-sub-hint { margin-top: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
.embedded-dict :deep(.page-container) {
  padding: 0;
  min-height: 0;
}
</style>
