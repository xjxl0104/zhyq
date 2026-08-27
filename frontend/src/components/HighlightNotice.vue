<template>
  <el-alert
    v-if="visible"
    type="warning"
    :closable="false"
    show-icon
    class="highlight-notice"
  >
    <template #title>
      <span>已定位到 {{ label }} #{{ highlightId }},列表仅显示该条记录</span>
      <el-button link type="primary" style="margin-left: 12px" @click="$emit('clear')">
        显示全部
      </el-button>
    </template>
  </el-alert>
</template>

<script setup>
/**
 * 从工单「查看来源记录」跳过来时的提示条。
 * 列表被筛成一条,不解释用户会以为数据丢了,所以必须有这条 + 一键还原。
 */
defineProps({
  visible: { type: Boolean, default: false },
  highlightId: { type: [Number, String], default: null },
  label: { type: String, default: '记录' }
})

defineEmits(['clear'])
</script>

<style scoped>
.highlight-notice {
  margin-bottom: 12px;
}
</style>
