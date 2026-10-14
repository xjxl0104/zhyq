import { describe, expect, it } from 'vitest'
import { STATE_META, agingOption, contractSegments, feeDonutOption, stateDonutOption, toWan, trendOption, wanLabel } from '../overviewCharts'

describe('财务总览图表配置', () => {
  it('金额换算为万元并保留两位', () => {
    expect(toWan(1018260)).toBe(101.83)
    expect(wanLabel(4386000)).toBe('438.6')
    expect(toWan(null)).toBe(0)
  })

  it('合同进度条四段按已收、未到期、到期未收、逾期排列且合计 100', () => {
    const segments = contractSegments({ received: 50, notDueOutstanding: 25, dueOutstanding: 0, overdueOutstanding: 25 })
    expect(segments).toEqual([{ key: 'paid', percent: 50 }, { key: 'notDue', percent: 25 }, { key: 'overdue', percent: 25 }])
    expect(contractSegments({ received: 0, notDueOutstanding: 0 })).toEqual([])
  })

  it('状态环形图只画有数量的状态，并淡化未选中状态', () => {
    const option = stateDonutOption({ overdue: 2, unpaid: 0, partial: 1, settled: 3, unbilled: 0 }, 'overdue')
    const data = option.series[0].data
    expect(data.map(item => item.key)).toEqual(['overdue', 'partial', 'settled'])
    expect(data.map(item => item.itemStyle.opacity)).toEqual([1, 0.3, 0.3])
    expect(option.title.text).toBe('6')
    expect(STATE_META.map(meta => meta.key)).toEqual(['overdue', 'unpaid', 'partial', 'settled', 'unbilled'])
  })

  it('趋势图按万元出数并突出所选月份', () => {
    const option = trendOption([{ month: '2026-09', receivable: 200000, received: 150000 }, { month: '2026-10', receivable: 100000, received: 50000 }], '2026-10')
    expect(option.xAxis.data).toEqual(['9月', '10月'])
    expect(option.series[0].data.map(item => item.value)).toEqual([20, 10])
    expect(option.series[1].data.map(item => item.itemStyle.opacity)).toEqual([0.55, 1])
  })

  it('费用环形图与账龄条形图使用接口数据', () => {
    const fee = feeDonutOption([{ feeType: '租金', receivable: 80000 }, { feeType: '物业费', receivable: 20000 }], 100000)
    expect(fee.series[0].data.map(item => item.name)).toEqual(['租金', '物业费'])
    expect(fee.title.text).toBe('10')
    const aging = agingOption([{ key: 'notDue', label: '未到期', amount: 10000, billCount: 1 }, { key: 'd90plus', label: '逾期 90 天以上', amount: 30000, billCount: 2 }])
    expect(aging.yAxis.data).toEqual(['逾期 90 天以上', '未到期'])
    expect(aging.series[0].data.map(item => item.value)).toEqual([3, 1])
  })
})
