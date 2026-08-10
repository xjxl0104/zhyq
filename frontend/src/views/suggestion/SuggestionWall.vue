<template>
  <div class="wall">
    <div
      v-for="(item, i) in items"
      :key="item.id"
      class="note"
      :class="colorOf(item)"
      :style="tiltOf(i)"
      @click="$emit('open', item)"
    >
      <span class="pin"></span>
      <div class="note-head">
        <span class="note-type">{{ typeMap[item.type] || '建议' }}</span>
        <span class="note-status" :class="'st-' + item.status">{{ statusMap[item.status] || '' }}</span>
      </div>
      <div class="note-title">{{ item.title }}</div>
      <div class="note-body">{{ item.content || '（无详细说明）' }}</div>
      <div class="note-foot">
        <span class="note-time">{{ (item.createTime || '').slice(0, 10) }}</span>
        <slot name="actions" :item="item" />
      </div>
    </div>
    <el-empty v-if="!items.length" description="还没有便利贴，快贴一张吧" :image-size="90" />
  </div>
</template>

<script setup>
const props = defineProps({
  items: { type: Array, default: () => [] },
  typeMap: { type: Object, default: () => ({}) },
  statusMap: { type: Object, default: () => ({}) }
})
defineEmits(['open'])

// 5 种便利贴底色,按 id 稳定分配(同一条建议颜色不变)
const COLORS = ['c-yellow', 'c-pink', 'c-green', 'c-blue', 'c-purple']
function colorOf(item) {
  return COLORS[(Number(item.id) || 0) % COLORS.length]
}
// 轻微随机旋转,按索引固定(不抖动)
function tiltOf(i) {
  const tilts = [-2.5, 1.8, -1.2, 2.4, -2, 1.2, -0.8, 2]
  return { '--tilt': tilts[i % tilts.length] + 'deg' }
}
</script>

<style scoped>
/* 扁平化背景:干净浅灰面 */
.wall {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 22px 20px; padding: 24px; min-height: 60vh; border-radius: 12px;
  background: #f4f5f7;
}
/* 便利贴:扁平、无渐变、细边、轻投影 */
.note {
  position: relative; padding: 16px 16px 12px; min-height: 150px;
  display: flex; flex-direction: column; cursor: pointer;
  color: #3a3320; border-radius: 10px;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  transform: rotate(var(--tilt, 0deg));
  box-shadow: 0 2px 8px rgba(30,40,60,.08);
  transition: transform .16s ease, box-shadow .16s ease;
}
.note:hover {
  transform: rotate(0deg) translateY(-4px);
  box-shadow: 0 10px 22px rgba(30,40,60,.16); z-index: 2;
}
/* 扁平柔和色块 */
.c-yellow { background: #fef3c7; } .c-pink { background: #fbd5de; }
.c-green { background: #d1f0d5; } .c-blue { background: #cfe6fb; }
.c-purple { background: #e4d8fb; }
/* 扁平圆点标记(替代拟物图钉) */
.pin {
  position: absolute; top: 12px; right: 12px;
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(0,0,0,.18);
}
.note-head { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
.note-type { font-size: 12px; font-weight: 700; padding: 2px 8px; background: rgba(0,0,0,.1); border-radius: 4px; }
.note-status { font-size: 12px; font-weight: 600; padding: 2px 7px; border-radius: 10px; background: rgba(0,0,0,.08); }
.st-4, .st-5 { background: rgba(34,139,34,.22); color: #1b5e20; }
.st-1 { background: rgba(180,120,0,.2); color: #7a4f00; }
.st-6 { background: rgba(0,0,0,.12); color: #555; }
.note-title { font-size: 16px; font-weight: 700; margin: 10px 0 6px; line-height: 1.3; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.note-body { font-size: 13px; line-height: 1.5; color: #4a4230; flex: 1; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; }
.note-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; padding-top: 8px; border-top: 1px dashed rgba(0,0,0,.15); }
.note-time { font-size: 12px; color: #6b6244; font-variant-numeric: tabular-nums; }
</style>
