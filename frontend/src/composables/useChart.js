import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { storeToRefs } from 'pinia'
import { useThemeStore } from '@/stores/theme'

/**
 * 统一 ECharts 封装（批次③-2）。
 *
 * 解决现状散乱：各页各自 init、resize 缺失/泄漏、无暗色适配。
 * 用法：
 *   const chartRef = ref()
 *   useChart(chartRef, (theme) => ({ ...echarts option，用 theme.axisLine 等取轴色... }))
 * optionFactory 收到与亮/暗联动的 theme 调色板，isDark 切换时自动重设 option；
 * 自动 ResizeObserver 重绘、卸载时 dispose，无需各页重复处理。
 */
export function useChart(elRef, optionFactory) {
  const themeStore = useThemeStore()
  const { isDark } = storeToRefs(themeStore)
  let chart = null
  let ro = null

  function palette(dark) {
    return dark
      ? { axisLine: '#3a404d', axisLabel: '#9aa1ac', splitLine: '#2b303b', textColor: '#d1d5db' }
      : { axisLine: '#e0e3e9', axisLabel: '#9aa1ac', splitLine: '#f0f2f5', textColor: '#374151' }
  }

  function apply() {
    if (!chart) return
    chart.setOption(optionFactory(palette(isDark.value)), true)
  }

  function render(el) {
    if (!el) return
    chart = echarts.init(el)
    apply()
    ro = new ResizeObserver(() => chart && chart.resize())
    ro.observe(el)
  }

  onMounted(async () => {
    await nextTick()
    render(elRef.value)
  })

  // 主题切换 → 用对应调色板重设
  watch(isDark, apply)

  onBeforeUnmount(() => {
    if (ro) ro.disconnect()
    if (chart) { chart.dispose(); chart = null }
  })

  // 暴露给调用方，需要外部数据变化后重设时调用
  return { refresh: apply, getChart: () => chart }
}
