import { get, post, put } from '@/utils/request'

export const authApi = {
  wxLogin: (jsCode) => post('/auth/wx-login', { jsCode }),
  bindPhone: (data) => post('/auth/bind-phone', data),
  bindInvite: (inviteCode) => post('/auth/bind-invite', { inviteCode })
}
export const meApi = {
  me: () => get('/me'),
  update: (data) => put('/me', data),
  agree: (version) => post('/me/agree', { version }),
  account: (data) => put('/me/account', data),
  home: () => get('/home'),
  position: () => get('/position'),
  team: () => get('/team'),
  allocation: () => get('/team/allocation'),
  setAllocation: (data) => put('/team/allocation', data),
  poster: () => get('/poster')
}
export const bizApi = {
  referral: (data) => post('/referral', data),
  customers: (params) => get('/referral/page', params),
  customer: (id) => get(`/referral/${id}`),
  extend: (id, reason) => post(`/referral/${id}/extend`, { reason }),
  warehouses: () => get('/warehouses'),
  commissions: (params) => get('/commission/page', params),
  balance: () => get('/withdrawal/balance'),
  withdraw: (amount) => post('/withdrawal', { amount }),
  withdrawals: (params) => get('/withdrawal/page', params),
  notices: (params) => get('/notice/page', params)
}
