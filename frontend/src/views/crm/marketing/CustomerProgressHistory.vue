<template>
  <div class="progress-history">
    <h4>跟进历史</h4>
    <p v-if="hasMore" class="history-note">显示最近 100 条跟进记录。</p>
    <el-empty v-if="!items.length" description="暂无跟进记录" :image-size="50" />
    <el-timeline v-else>
      <el-timeline-item v-for="(item, index) in items" :key="item.id || index" :timestamp="formatTime(item.createdAt)">
        <div class="history-title"><strong v-if="item.percentage != null">{{ item.percentage }}%</strong><span>{{ stageLabel(item.stage) }}</span></div>
        <div class="history-summary">{{ item.summary || '进度已更新' }}</div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>
<script setup>
defineProps({ items:{ type:Array, default:() => [] }, hasMore:Boolean })
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : ''
const stageLabel = stage => ({ unassigned:'待分派', assigned:'已分派', accepted:'已承接', rejected:'等待重新分派', signed:'已签约', following:'跟进中', followup:'跟进中', legacy:'历史记录' }[stage] || '')
</script>
<style scoped>.progress-history { margin-top:24px; }.progress-history h4 { margin-bottom:20px; }.history-title { display:flex; gap:12px; align-items:center; }.history-summary { white-space:pre-wrap; overflow-wrap:anywhere; margin-top:6px; line-height:1.7; }</style>
