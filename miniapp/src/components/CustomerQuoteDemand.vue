<template>
  <view v-if="intake?.submittedAt || intake?.requirements" class="card">
    <view class="title">二次收集 · 详细报价需求</view>
    <view v-if="intake.submittedAt" class="submitted">客户提交于 {{ String(intake.submittedAt).replace('T',' ') }}</view>
    <view v-if="stage" class="assessment">{{ stage }}</view>
    <view v-for="row in rows" :key="row.label" class="demand-row">
      <text class="field-name">{{ row.label }}</text><text>{{ row.value }}</text>
    </view>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { quoteRows } from '@/utils/quote-intake.mjs'
const props=defineProps({ intake: Object, showAssessment: Boolean })
const stages={project_required:'等待园区确认归属',pending:'正在评估云仓报价',running:'正在评估云仓报价',waiting:'等待补齐云仓报价资料',ready:'报价报告已生成',failed:'评估待园区重试'}
const rows=computed(()=>quoteRows(props.intake?.requirements || {}))
const stage=computed(()=>props.showAssessment && stages[props.intake?.assessmentStatus])
</script>
<style scoped>
.submitted,.field-name{color:var(--park-muted,#667085)}
.submitted,.assessment{font-size:25rpx;margin-top:12rpx}
.assessment{color:#3550ae}.demand-row{display:flex;justify-content:space-between;gap:24rpx;padding:18rpx 0;border-bottom:1rpx solid var(--park-line,#e7ebf0)}
.field-name{flex-shrink:0;max-width:42%}.demand-row text:last-child{text-align:right;overflow-wrap:anywhere;white-space:pre-line}
</style>
