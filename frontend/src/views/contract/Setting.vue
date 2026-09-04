<template>
  <div class="page-container">
    <div class="table-card setting-card">
      <div class="card-head">
        <div>
          <div class="card-title">合同设置</div>
          <!-- 以前这页改了值也不生效(没有任何代码读 biz_setting),看着就像个摆设。
               现在每项都标出「作用于哪里」,改之前知道会影响什么 -->
          <div class="card-sub">下面每一项都会真实影响系统行为，「作用于」列写明了生效位置。</div>
        </div>
        <el-button type="primary" :loading="saving" @click="saveAll">
          <el-icon><Check /></el-icon>保存全部
        </el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column label="配置项" min-width="200">
          <template #default="{ row }">
            <div class="cell-main">{{ meta(row.skey).label }}</div>
            <div class="cell-sub">{{ row.skey }}</div>
          </template>
        </el-table-column>
        <el-table-column label="配置值" width="220">
          <template #default="{ row }">
            <el-switch v-if="meta(row.skey).type === 'switch'"
                       :model-value="row.svalue === '1'"
                       active-text="开启" inactive-text="关闭"
                       @update:model-value="v => row.svalue = v ? '1' : '0'" />
            <el-input-number v-else-if="meta(row.skey).type === 'number'"
                             :model-value="Number(row.svalue || 0)" :min="0" :precision="0"
                             controls-position="right" style="width: 100%"
                             @update:model-value="v => row.svalue = String(v ?? 0)" />
            <el-input v-else v-model="row.svalue" placeholder="配置值" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="80">
          <template #default="{ row }">{{ meta(row.skey).unit || '-' }}</template>
        </el-table-column>
        <!-- 「有没有用」的关键就在这一列:说清改了之后哪里会变 -->
        <el-table-column label="作用于" min-width="320">
          <template #default="{ row }">
            <span v-if="meta(row.skey).effect" class="effect">{{ meta(row.skey).effect }}</span>
            <span v-else class="effect unwired">
              暂未接入业务逻辑，改了不会有效果
              <el-tooltip content="该配置项还没有代码读取，属于历史遗留；需要生效请提需求">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="说明" />
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!list.length && !loading" class="empty-tip">暂无合同配置项</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { settingApi } from '@/api/finance'

const MODULE = 'contract'
const loading = ref(false)
const saving = ref(false)
const list = ref([])

/**
 * 配置项元信息:中文名、控件类型、单位,以及最要紧的「作用于」。
 * effect 留空 = 该项还没被任何代码读取,页面会明确标出来,免得用户改了半天没反应。
 */
const SETTING_META = {
  code_prefix: {
    label: '合同编号前缀', type: 'text',
    effect: '新增合同时按「前缀+年份-4位流水」自动生成编号（编号留空即自动生成）'
  },
  expire_remind_days: {
    label: '到期提醒提前天数', type: 'number', unit: '天',
    effect: '工作台「合同即将到期」按它统计（原先写死 30 天，与此处配置对不上）'
  },
  deposit_months: {
    label: '保证金月数', type: 'number', unit: '个月',
    effect: '新增合同时按「月租金 × 月数」预填保证金'
  },
  default_term_years: {
    label: '默认租期', type: 'number', unit: '年',
    effect: '新增合同填了起租日后，按它自动推算到期日'
  },
  auto_expire_enabled: {
    label: '到期自动置为已到期', type: 'switch',
    effect: '每日任务把「执行中且已过结束日」的合同翻成已到期；关掉后只能人工改状态'
  }
}
function meta(key) {
  return SETTING_META[key] || { label: key, type: 'text' }
}

async function load() {
  loading.value = true
  try {
    list.value = await settingApi.list(MODULE) || []
  } finally {
    loading.value = false
  }
}

async function saveAll() {
  saving.value = true
  try {
    await settingApi.batch(list.value)
    // 说清生效时机:多数项下次操作就生效,定时任务类的要等下一轮
    ElMessage.success('已保存，新增合同/工作台统计立即生效；每日任务类配置下一轮生效')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.setting-card { padding: 18px; }
.card-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 14px; gap: 16px; }
.card-title { font-size: 16px; font-weight: 650; color: var(--el-text-color-primary); }
.card-sub { margin-top: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
.cell-main { line-height: 1.4; font-weight: 600; }
.cell-sub { margin-top: 2px; font-size: 12px; line-height: 1.3; color: var(--el-text-color-secondary); }
.effect { font-size: 13px; line-height: 1.5; color: var(--el-text-color-regular); }
.effect.unwired { color: var(--el-color-warning); display: inline-flex; align-items: center; gap: 4px; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 30px 0; }
</style>
