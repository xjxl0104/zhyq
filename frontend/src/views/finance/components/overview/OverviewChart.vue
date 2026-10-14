<template>
  <div ref="el" class="overview-chart" role="img" :aria-label="label"></div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { useChart } from '@/composables/useChart'

const props = defineProps({
  option: { type: Object, required: true },
  label: { type: String, default: '' }
})
const emit = defineEmits(['select'])
const el = ref(null)
const { refresh, getChart } = useChart(el, () => props.option)

watch(() => props.option, () => refresh())
onMounted(async () => {
  // useChart 的挂载钩子先注册、同样等待 nextTick 后初始化，这里排在它之后绑定点击。
  await nextTick()
  getChart()?.on('click', params => emit('select', params))
})
</script>

<style scoped>
.overview-chart { width: 100%; height: 100%; min-height: 0; }
</style>
