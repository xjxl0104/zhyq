<template>
  <footer class="icp-footer" :class="{ 'is-compact': compact, 'is-stacked': stacked }">
    <span v-if="!compact">© 2026 {{ company }}</span>
    <a :href="MIIT_URL" target="_blank" rel="noopener noreferrer"><span v-for="part in ICP_PARTS" :key="part" class="part">{{ part }}</span></a>
  </footer>
</template>

<script setup>
// 工信部 ICP 备案要求:网站主页底部标注备案号,并链接到备案管理系统,供公众查询核对
// compact:只留备案号链接,给侧栏/手机底栏这类窄位置用
// stacked:64px 图标栏放不下一行,把备案号按「粤ICP备 / 数字 / 号-1」分三行,避免数字被拦腰折断
defineProps({
  compact: { type: Boolean, default: false },
  stacked: { type: Boolean, default: false },
})

// 三段拼起来就是完整备案号;段与段之间不能有空白,正常宽度下仍是连续的一行文字
const ICP_PARTS = ['粤ICP备', '2026136948', '号-1']
const MIIT_URL = 'https://beian.miit.gov.cn/'
const company = '广州数智云仓产业园运营有限公司'
</script>

<style scoped>
/* 默认配色给深色底(登录页、侧栏);浅色底的使用方设置 --icp-text / --icp-link 即可 */
.icp-footer {
  display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 4px 16px;
  padding: var(--icp-pad, 16px 0 calc(8px + env(safe-area-inset-bottom))); font-size: 12px; line-height: 1.6;
  color: var(--icp-text, rgba(255, 255, 255, 0.7)); text-align: center;
}
.icp-footer a { color: var(--icp-link, rgba(255, 255, 255, 0.85)); text-decoration: none; }
.icp-footer a:hover, .icp-footer a:focus-visible { color: var(--icp-link-hover, #fff); text-decoration: underline; }
.icp-footer.is-compact { gap: 0; font-size: 11px; line-height: 1.5; overflow-wrap: anywhere; }
/* 64px 栏去掉内边距后可用约 60px,十位数字在 9px 下约 56px 才放得下 */
.icp-footer.is-stacked { font-size: 9px; line-height: 1.4; }
.icp-footer.is-stacked .part { display: block; }
</style>
