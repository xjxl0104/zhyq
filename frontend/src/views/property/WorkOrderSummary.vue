<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="统计范围">
          <el-select v-model="days" style="width: 130px" @change="load">
            <el-option :value="7" label="近 7 天" />
            <el-option :value="30" label="近 30 天" />
            <el-option :value="90" label="近 90 天" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        </el-form-item>
        <el-form-item>
          <span class="hint">趋势与 SLA 按所选范围统计；状态与分类为全量口径</span>
        </el-form-item>
      </el-form>
    </div>

    <!-- 状态总览:点击卡片跳到工单列表并带上状态筛选 -->
    <div class="stat-row" v-loading="loading">
      <div class="stat-card total">
        <div class="stat-label">工单总数</div>
        <div class="stat-value">{{ stats.total || 0 }}</div>
        <div class="stat-foot">在办 {{ stats.open || 0 }}</div>
      </div>
      <div
        v-for="s in statusCards"
        :key="s.code"
        class="stat-card clickable"
        @click="gotoList(s.code)"
      >
        <div class="stat-label">{{ s.name }}</div>
        <div class="stat-value" :class="s.cls">{{ s.count }}</div>
        <div class="stat-foot">占比 {{ pct(s.count) }}</div>
      </div>
    </div>

    <!-- SLA -->
    <div class="table-card">
      <div class="card-title">SLA 达成情况<span class="sub">仅统计已完成/已关闭的工单</span></div>
      <div class="sla-row">
        <div class="sla-item">
          <div class="sla-label">已结单</div>
          <div class="sla-value">{{ sla.settled || 0 }}</div>
        </div>
        <div class="sla-item">
          <div class="sla-label">按时完成</div>
          <div class="sla-value success">{{ sla.met || 0 }}</div>
        </div>
        <div class="sla-item">
          <div class="sla-label">已超时</div>
          <div class="sla-value danger">{{ sla.breached || 0 }}</div>
        </div>
        <div class="sla-item">
          <div class="sla-label">达成率</div>
          <div class="sla-value" :class="rateCls">
            {{ sla.metRate == null ? '—' : sla.metRate + '%' }}
          </div>
        </div>
        <div class="sla-item warn-box" v-if="sla.openTimeout > 0">
          <div class="sla-label">在办已超时</div>
          <div class="sla-value danger">{{ sla.openTimeout }}</div>
          <div class="sla-foot">需尽快干预</div>
        </div>
      </div>
      <el-alert
        v-if="sla.metRate == null"
        type="info"
        :closable="false"
        show-icon
        title="暂无已结单的工单，达成率待有数据后显示"
      />
    </div>

    <!-- 多维分组 -->
    <el-row :gutter="16">
      <el-col :span="12" v-for="g in groups" :key="g.key">
        <div class="table-card">
          <div class="card-title">{{ g.title }}</div>
          <el-table :data="g.rows" v-loading="loading" size="small" border>
            <el-table-column prop="name" :label="g.nameLabel" min-width="120" />
            <el-table-column prop="count" label="数量" width="90" align="right" />
            <el-table-column label="占比" min-width="160">
              <template #default="{ row }">
                <div class="bar-wrap">
                  <div class="bar" :style="{ width: barWidth(row.count, g.rows) }"></div>
                  <span class="bar-text">{{ pct(row.count) }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && !g.rows.length" description="暂无数据" :image-size="60" />
        </div>
      </el-col>
    </el-row>

    <!-- 趋势 -->
    <div class="table-card">
      <div class="card-title">新增趋势<span class="sub">近 {{ days }} 天</span></div>
      <div class="trend" v-if="trend.length">
        <div v-for="t in trend" :key="t.date" class="trend-col" :title="`${t.date}: ${t.count} 单`">
          <div class="trend-bar" :style="{ height: trendHeight(t.count) }"></div>
          <div class="trend-date">{{ t.date.slice(5) }}</div>
        </div>
      </div>
      <el-empty v-else description="所选范围内暂无新增工单" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { workOrderApi } from '@/api/property'

const router = useRouter()
const loading = ref(false)
const days = ref(30)
const stats = ref({})
const sla = ref({})
const byStatus = ref([])
const bySource = ref([])
const byOrderType = ref([])
const byCategory = ref([])
const byUrgency = ref([])
const trend = ref([])

// 状态码 → 卡片配色,与工单列表的 tag 颜色保持一致
const STATUS_CLS = {
  1: 'warning', 2: 'warning', 3: 'primary', 4: 'primary',
  5: 'success', 6: 'info', 7: 'danger'
}

const statusCards = computed(() =>
  byStatus.value.map((s) => ({ ...s, cls: STATUS_CLS[s.code] || '' }))
)

const groups = computed(() => [
  { key: 'source', title: '按来源', nameLabel: '来源', rows: bySource.value },
  { key: 'type', title: '按工单类型', nameLabel: '类型', rows: byOrderType.value },
  { key: 'category', title: '按分类/工种', nameLabel: '分类', rows: byCategory.value },
  { key: 'urgency', title: '按紧急度', nameLabel: '紧急度', rows: byUrgency.value }
])

const rateCls = computed(() => {
  const r = sla.value.metRate
  if (r == null) return ''
  return r >= 95 ? 'success' : r >= 80 ? 'warning' : 'danger'
})

function pct(n) {
  const t = stats.value.total || 0
  if (!t) return '0%'
  return Math.round((n * 1000) / t) / 10 + '%'
}

function barWidth(n, rows) {
  const max = Math.max(...rows.map((r) => r.count), 1)
  return Math.max((n / max) * 100, 2) + '%'
}

function trendHeight(n) {
  const max = Math.max(...trend.value.map((t) => t.count), 1)
  return Math.max((n / max) * 100, 4) + '%'
}

// 点状态卡片跳到工单列表,带上状态便于继续处理
function gotoList(status) {
  router.push({ path: '/property/workorder', query: { status } })
}

async function load() {
  loading.value = true
  try {
    const d = await workOrderApi.summary({ days: days.value })
    stats.value = d.stats || {}
    sla.value = d.sla || {}
    byStatus.value = d.byStatus || []
    bySource.value = d.bySource || []
    byOrderType.value = d.byOrderType || []
    byCategory.value = d.byCategory || []
    byUrgency.value = d.byUrgency || []
    trend.value = d.trend || []
  } catch (e) {
    ElMessage.error(e?.message || '加载汇总数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.stat-card {
  flex: 1 1 130px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 14px 16px;
}
.stat-card.total {
  background: var(--el-color-primary-light-9);
}
.stat-card.clickable {
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.stat-card.clickable:hover {
  box-shadow: var(--el-box-shadow-light);
  transform: translateY(-2px);
}
.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.stat-value {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.4;
}
.stat-foot,
.sla-foot {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.table-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}
.card-title .sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-placeholder);
  margin-left: 8px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.sla-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.sla-item {
  min-width: 96px;
}
.sla-item.warn-box {
  padding: 8px 14px;
  border-radius: 6px;
  background: var(--el-color-danger-light-9);
}
.sla-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.sla-value {
  font-size: 22px;
  font-weight: 600;
}
.primary { color: var(--el-color-primary); }
.success { color: var(--el-color-success); }
.warning { color: var(--el-color-warning); }
.danger  { color: var(--el-color-danger); }
.info    { color: var(--el-text-color-secondary); }
.bar-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bar {
  height: 10px;
  border-radius: 5px;
  background: var(--el-color-primary-light-3);
  min-width: 2px;
}
.bar-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.trend {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 160px;
  padding-top: 8px;
}
.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  height: 100%;
  min-width: 18px;
}
.trend-bar {
  width: 70%;
  background: var(--el-color-primary-light-3);
  border-radius: 3px 3px 0 0;
}
.trend-bar:hover {
  background: var(--el-color-primary);
}
.trend-date {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
  white-space: nowrap;
}
</style>
