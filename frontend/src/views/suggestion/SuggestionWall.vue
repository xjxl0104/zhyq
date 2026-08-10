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
/* 软木板背景 */
.wall {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 26px 22px; padding: 28px 24px; min-height: 60vh; border-radius: 10px;
  background:
    radial-gradient(circle at 30% 20%, rgba(255,255,255,.06), transparent 60%),
    repeating-linear-gradient(45deg, #c9a06a 0 3px, #c19758 3px 6px);
  background-color: #bd9455;
  box-shadow: inset 0 0 60px rgba(80,50,10,.35);
}
/* 便利贴 */
.note {
  position: relative; padding: 16px 16px 12px; min-height: 150px;
  display: flex; flex-direction: column; cursor: pointer;
  color: #3a3320; font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  transform: rotate(var(--tilt, 0deg));
  box-shadow: 0 6px 14px rgba(0,0,0,.28);
  transition: transform .18s ease, box-shadow .18s ease;
}
.note:hover {
  transform: rotate(0deg) translateY(-6px) scale(1.03);
  box-shadow: 0 16px 30px rgba(0,0,0,.4); z-index: 2;
}
.c-yellow { background: #fdf389; } .c-pink { background: #ff9fb2; }
.c-green { background: #b6ec9a; } .c-blue { background: #a5d8ff; }
.c-purple { background: #d7bbff; }
/* 图钉 */
.pin {
  position: absolute; top: -9px; left: 50%; transform: translateX(-50%);
  width: 16px; height: 16px; border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #ff7a7a, #c0392b);
  box-shadow: 0 2px 4px rgba(0,0,0,.4), inset 0 -2px 3px rgba(0,0,0,.25);
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
