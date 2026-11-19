import { describe, it, expect } from 'vitest'
import { groupByContract, settleShares } from '../commissionGroups'

const rows = [
  { month: '2026-11', contractId: 5, contractNo: 'CS-5', customerName: '泽浩', promoters: '谢益长', tickets: 51, total: 4.845, frozen: 1.425, settleable: 3.42, settled: 0 },
  { month: '2026-10', contractId: 5, contractNo: 'CS-5', customerName: '泽浩', promoters: '谢益长', tickets: 43, total: 4.085, frozen: 0, settleable: 0, settled: 4.085 },
  { month: '2026-09', contractId: 6, contractNo: 'CS-6', customerName: '其他', promoters: '张三、李四', tickets: 2, total: 1, frozen: 0, settleable: 0, settled: 1 },
]

describe('按合同月结主列表', () => {
  it('一个合同一行，月份按新到旧，金额与票数汇总', () => {
    const list = groupByContract(rows)
    expect(list.map(c => c.contractId)).toEqual([5, 6])
    expect(list[0]).toMatchObject({ tickets: 94, total: 8.93, frozen: 1.425, settleable: 3.42, settled: 4.085, latestMonth: '2026-11', firstMonth: '2026-10', settleableMonths: 1 })
    expect(list[0].months.map(m => m.month)).toEqual(['2026-11', '2026-10'])
    expect(list[1].promoters).toBe('张三、李四')
  })
  it('结算进度按已结算/可结算/冻结占比', () => {
    expect(settleShares({ settled: 1, settleable: 1, frozen: 2 })).toEqual({ settled: 25, settleable: 25, frozen: 50 })
    expect(settleShares({})).toEqual({ settled: 0, settleable: 0, frozen: 0 })
  })
})
