<template>
  <div class="page-container">
    <div class="rpt-card">
      <div class="section-title">
        <el-icon class="title-icon"><Files /></el-icon>
        <span>报表中心</span>
      </div>
      <div class="rpt-grid">
        <div v-for="r in reports" :key="r.name" class="rpt-item" @click="go(r)">
          <div class="rpt-icon" :style="{ background: r.bg, color: r.color }">
            <el-icon :size="28"><component :is="r.icon" /></el-icon>
          </div>
          <div class="rpt-info">
            <div class="rpt-name">{{ r.name }}</div>
            <div class="rpt-desc">{{ r.desc }}</div>
          </div>
          <el-button type="primary" text class="rpt-btn">
            打开<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const reports = [
  { name: '财务报表', desc: '账单收支、应收实收统计汇总', icon: 'Money', color: '#4f46e5', bg: '#eef2ff', path: '/finance/report' },
  { name: '退房报表', desc: '退租房源与退款结算明细', icon: 'DocumentDelete', color: '#e5484d', bg: '#fdecec', path: '/finance/checkout-report' },
  { name: '招商分析', desc: '线索转化与招商漏斗分析', icon: 'DataAnalysis', color: '#f59e0b', bg: '#fef6e7', path: '/crm/analysis' },
  { name: '能耗统计', desc: '水电用量与费用趋势分析', icon: 'Lightning', color: '#06b6d4', bg: '#e7f8fb', path: '/energy/stats' },
  { name: '数据看板', desc: '园区综合运营数据总览', icon: 'DataBoard', color: '#16a34a', bg: '#e9f7ef', path: '/data/center' },
  { name: '监控大屏', desc: '全屏可视化指挥调度大屏', icon: 'Monitor', color: '#8b5cf6', bg: '#f3edfd', screen: true }
]

function go(r) {
  if (r.screen) window.open('/screen')
  else router.push(r.path)
}
</script>

<style scoped>
.rpt-card {
  background: #fff; border-radius: 8px; padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}
.section-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 16px;
}
.title-icon { color: #4f46e5; }
.rpt-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px;
}
.rpt-item {
  display: flex; align-items: center; gap: 14px; padding: 18px 16px;
  border: 1px solid #ebeef5; border-radius: 10px; cursor: pointer; transition: all 0.2s;
}
.rpt-item:hover {
  border-color: #c7cffb; box-shadow: 0 6px 18px rgba(79, 70, 229, 0.12); transform: translateY(-3px);
}
.rpt-icon {
  flex-shrink: 0; width: 54px; height: 54px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
}
.rpt-info { flex: 1; min-width: 0; }
.rpt-name { font-size: 15px; font-weight: 600; color: #303133; }
.rpt-desc { font-size: 12px; color: #909399; margin-top: 4px; }
.rpt-btn { flex-shrink: 0; }
</style>
