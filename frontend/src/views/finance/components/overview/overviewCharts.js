// 财务总览图表配置：纯函数，输入接口数据，输出 ECharts option，便于单测。
import { money } from '@/utils/format'

// 颜色按含义固定：蓝 = 已收，浅蓝 = 应收，琥珀 = 到期未收，红 = 逾期，灰 = 未到期。
export const COLORS = Object.freeze({
  paid: '#2f4bf0',
  due: '#c3cbfa',
  wait: '#eaa23c',
  risk: '#e0473d',
  notDue: '#dfe3ec',
  settled: '#23a26d',
  unbilled: '#b3b9cf',
  ink: '#0b0d17',
  ink2: '#68708a',
  grid: '#eef0f5'
})

// 分类色固定顺序（已做色弱校验），琥珀与红留给待收与逾期。
export const FEE_COLORS = ['#2f4bf0', '#1a9aa0', '#7a5af0', '#e0659a', '#5f8f3e']

export const STATE_META = Object.freeze([
  { key: 'overdue', label: '逾期', color: COLORS.risk },
  { key: 'unpaid', label: '未收款', color: COLORS.wait },
  { key: 'partial', label: '部分已收', color: COLORS.paid },
  { key: 'settled', label: '已结清', color: COLORS.settled },
  { key: 'unbilled', label: '未出账', color: COLORS.unbilled }
])

export const AGING_COLORS = Object.freeze({
  notDue: '#c9cfdc', undated: '#dfe3ec', grace: '#f3c983', d30: COLORS.wait, d90: '#e8743a', d90plus: COLORS.risk
})

const TOOLTIP = { backgroundColor: '#0b0d17', borderWidth: 0, padding: [8, 11], textStyle: { color: '#fff', fontSize: 12 } }

export function toWan(value) {
  return Math.round(Number(value || 0) / 100) / 100
}

export function wanLabel(value) {
  return toWan(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

export function trendOption(trend = [], selectedMonth = '') {
  const months = trend.map(point => `${Number(point.month.slice(5))}月`)
  const highlight = (value, index, color) => ({
    value: toWan(value),
    itemStyle: { color, opacity: trend[index]?.month === selectedMonth || !selectedMonth ? 1 : 0.55 }
  })
  return {
    color: [COLORS.due, COLORS.paid],
    grid: { left: 4, right: 8, top: 30, bottom: 2, containLabel: true },
    legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10, textStyle: { color: COLORS.ink2, fontSize: 12 } },
    tooltip: {
      ...TOOLTIP, trigger: 'axis', axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(47,75,240,.06)' } },
      formatter: params => {
        const point = trend[params[0]?.dataIndex] || {}
        return `${point.month}<br/>应收 ¥${money(point.receivable)}<br/>已收 ¥${money(point.received)}`
      }
    },
    xAxis: { type: 'category', data: months, axisTick: { show: false }, axisLine: { lineStyle: { color: '#d9dde6' } }, axisLabel: { color: COLORS.ink2, fontSize: 12 } },
    yAxis: { type: 'value', name: '万元', nameTextStyle: { color: COLORS.ink2, fontSize: 11, align: 'left' }, splitNumber: 3, axisLabel: { color: COLORS.ink2, fontSize: 11 }, splitLine: { lineStyle: { color: COLORS.grid, type: 'dashed' } } },
    series: [
      { name: '应收', type: 'bar', barMaxWidth: 16, barGap: '25%', itemStyle: { borderRadius: [4, 4, 0, 0] }, data: trend.map((p, i) => highlight(p.receivable, i, COLORS.due)) },
      { name: '已收', type: 'bar', barMaxWidth: 16, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: trend.map((p, i) => highlight(p.received, i, COLORS.paid)) }
    ]
  }
}

function donut(data, centerTitle, centerValue, extra = {}) {
  return {
    tooltip: { ...TOOLTIP, trigger: 'item', formatter: extra.tooltip || (item => `${item.name}<br/>¥${money(item.data.raw)}（${item.percent}%）`) },
    title: {
      text: centerValue, subtext: centerTitle, left: 'center', top: '36%', itemGap: 2,
      textStyle: { color: COLORS.ink, fontSize: 20, fontWeight: 700 }, subtextStyle: { color: COLORS.ink2, fontSize: 11 }
    },
    series: [{
      type: 'pie', radius: ['62%', '86%'], center: ['50%', '50%'], avoidLabelOverlap: true, label: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 }, emphasis: { scale: true, scaleSize: 4 },
      data
    }]
  }
}

export function feeDonutOption(fees = [], total = 0) {
  const data = fees.map((fee, index) => ({
    name: fee.feeType, value: Number(fee.receivable), raw: fee.receivable,
    itemStyle: { color: FEE_COLORS[index % FEE_COLORS.length] }
  }))
  return donut(data, '万元', wanLabel(total))
}

export function stateDonutOption(summary, activeState = '') {
  const data = STATE_META.map(meta => ({
    name: meta.label, key: meta.key, value: Number(summary?.[meta.key] || 0), raw: summary?.[meta.key] || 0,
    itemStyle: { color: meta.color, opacity: !activeState || activeState === meta.key ? 1 : 0.3 }
  })).filter(item => item.value > 0)
  const total = data.reduce((sum, item) => sum + item.value, 0)
  return donut(data, '合同', String(total), { tooltip: item => `${item.name}<br/>${item.value} 份（${item.percent}%）` })
}

export function agingOption(aging = []) {
  const rows = [...aging].reverse()
  return {
    grid: { left: 4, right: 56, top: 4, bottom: 4, containLabel: true },
    tooltip: { ...TOOLTIP, trigger: 'item', formatter: item => `${item.name}<br/>¥${money(rows[item.dataIndex]?.amount)} · ${rows[item.dataIndex]?.billCount || 0} 张` },
    xAxis: { type: 'value', show: false },
    yAxis: { type: 'category', data: rows.map(row => row.label), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: COLORS.ink2, fontSize: 12 } },
    series: [{
      type: 'bar', barWidth: 12, showBackground: true, backgroundStyle: { color: '#f3f4f8', borderRadius: 6 },
      itemStyle: { borderRadius: 6 },
      label: { show: true, position: 'right', color: COLORS.ink, fontSize: 12, formatter: item => (Number(item.value) > 0 ? `${wanLabel(rows[item.dataIndex]?.amount)}万` : '—') },
      data: rows.map(row => ({ value: toWan(row.amount), itemStyle: { color: AGING_COLORS[row.key] || COLORS.notDue } }))
    }]
  }
}

/** 合同进度条四段（已收 / 未到期 / 到期未收 / 逾期）的百分比，四段相加为 100。 */
export function contractSegments(row) {
  const parts = [
    ['paid', Number(row?.received || 0)],
    ['notDue', Number(row?.notDueOutstanding || 0)],
    ['due', Number(row?.dueOutstanding || 0)],
    ['overdue', Number(row?.overdueOutstanding || 0)]
  ].map(([key, value]) => [key, Math.max(0, value)])
  const total = parts.reduce((sum, [, value]) => sum + value, 0)
  if (total <= 0) return []
  return parts.filter(([, value]) => value > 0).map(([key, value]) => ({ key, percent: Math.round(value / total * 1000) / 10 }))
}
