<template>
  <footer class="icp-footer">
    <span>© 2026 {{ company }}</span>
    <a :href="MIIT_URL" target="_blank" rel="noopener noreferrer">{{ ICP_NO }}</a>
  </footer>
</template>

<script setup>
// 工信部 ICP 备案要求:网站主页底部标注备案号,并链接到备案管理系统,供公众查询核对
const ICP_NO = '粤ICP备2026136948号-1'
const MIIT_URL = 'https://beian.miit.gov.cn/'
const company = '广州数智云仓产业园运营有限公司'
</script>

<style scoped>
.icp-footer {
  display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 4px 16px;
  padding: 16px 0 calc(8px + env(safe-area-inset-bottom)); font-size: 12px; line-height: 1.6;
  color: rgba(255, 255, 255, 0.7); text-align: center;
}
.icp-footer a { color: rgba(255, 255, 255, 0.85); text-decoration: none; }
.icp-footer a:hover, .icp-footer a:focus-visible { color: #fff; text-decoration: underline; }
</style>
