import { expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { feePeriodApi } from '../energy'
vi.mock('@/utils/request', () => ({ default: { put: vi.fn().mockResolvedValue({ id: 17, paymentStatus: 1 }) } }))
it('缴费状态使用独立通知单接口，不复用金额调整接口', async () => {
  await expect(feePeriodApi.updateNoticePayment(17, { paymentStatus: 1 })).resolves.toEqual({ id: 17, paymentStatus: 1 })
  expect(request.put).toHaveBeenCalledWith('/energy/fee-period/notice/17/payment', { paymentStatus: 1 })
})
