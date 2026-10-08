import { expect, it } from 'vitest'
import { rectificationDeadline, rectificationOverdue, formatRectificationDeadline, rectificationOptions } from '../workOrderDeadline'

it('offers exactly four rectification windows and derives the deadline from creation', () => {
  expect(rectificationOptions.map(option => option.label)).toEqual(['3天', '7天', '15天', '30天'])
  const order = { createTime: '2026-10-06 10:00:00', slaResolveMin: 3 * 24 * 60, status: 3 }
  expect(formatRectificationDeadline(order)).toBe('2026-10-09 10:00')
  expect(rectificationOverdue(order, new Date('2026-10-09T10:00:00'))).toBe(false)
  expect(rectificationOverdue(order, new Date('2026-10-09T10:00:01'))).toBe(true)
})

it('retains a late-completion flag but does not mark timely or legacy orders overdue', () => {
  const order = { createTime: '2026-10-06T10:00:00', slaResolveMin: 7 * 24 * 60, status: 5 }
  expect(rectificationOverdue({ ...order, finishTime: '2026-10-13T10:01:00' })).toBe(true)
  expect(rectificationOverdue({ ...order, finishTime: '2026-10-13T10:00:00' })).toBe(false)
  expect(rectificationOverdue({ ...order, slaResolveMin: null })).toBe(false)
  expect(rectificationDeadline({ ...order, createTime: 'invalid' })).toBeNull()
})
