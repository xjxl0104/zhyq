import { onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'

const lightPalette = Object.freeze({
  axisLine: '#e0e3e9',
  axisLabel: '#6b7280',
  splitLine: '#f0f2f5',
  textColor: '#374151',
})

/**
 * 统一 ECharts 封装（批次③-2）。
 *
 * 解决现状散乱：各页各自 init、resize 缺失/泄漏。
 * 用法：
 *   const chartRef = ref()
 *   useChart(chartRef, (theme) => ({ ...echarts option，用 theme.axisLine 等取轴色... }))
 * optionFactory 收到统一亮色调色板；
 * 自动 ResizeObserver 重绘、卸载时 dispose，无需各页重复处理。
 */
export function useChart(elRef, optionFactory) {
  let chart = null
  let ro = null

  function apply() {
    if (!chart) return
    chart.setOption(optionFactory(lightPalette), true)
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

  onBeforeUnmount(() => {
    if (ro) ro.disconnect()
    if (chart) { chart.dispose(); chart = null }
  })

  // 暴露给调用方，需要外部数据变化后重设时调用
  return { refresh: apply, getChart: () => chart }
}
