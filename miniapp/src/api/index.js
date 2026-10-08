import { get, post, put, request } from '@/utils/request'

export const authApi = {
  quickLogin: data => post('/auth/quick-login', data),
  wxLogin: (jsCode, appId) => post('/auth/wx-login', { jsCode, appId }),
  passwordLogin: (data) => post('/auth/password-login', data),
  passwordRegister: (data) => post('/auth/password-register', data),
  passwordSetup: (data) => post('/auth/password-setup', data),
  passwordStatus: () => get('/auth/password-status'),
  bindPhone: (data) => post('/auth/bind-phone', data),
  bindInvite: (inviteCode) => post('/auth/bind-invite', { inviteCode })
}
export const meApi = {
  me: () => get('/me'),
  update: (data) => put('/me', data),
  agree: (version) => post('/me/agree', { version }),
  accountStatus: () => get('/me/account'),
  account: (data) => put('/me/account', data),
  home: () => get('/home'),
  position: () => get('/position'),
  team: () => get('/team'),
  poster: () => get('/poster')
}
export const pricingApi = {
  customers: () => get('/pricing/customers'),
  beneficiaries: () => get('/pricing/beneficiaries'),
  team: () => get('/pricing/team'),
  positions: () => get('/pricing/positions'),
  setPosition: (promoterId, code) => put(`/pricing/team/${promoterId}/position`, { code }),
  customer: (customerId) => get(`/pricing/customer/${customerId}`),
  save: (customerId, data) => put(`/pricing/customer/${customerId}`, data)
}
export const bizApi = {
  referral: (data) => post('/referral', data),
  customers: (params) => get('/referral/page', params),
  customer: (id) => get(`/referral/${id}`),
  removeCustomer: (id) => request('DELETE', `/referral/${id}`),
  extend: (id, reason) => post(`/referral/${id}/extend`, { reason }),
  warehouses: () => get('/warehouses'),
  commissions: (params) => get('/commission/page', params),
  balance: () => get('/withdrawal/balance'),
  withdraw: (amount) => post('/withdrawal', { amount }),
  withdrawals: (params) => get('/withdrawal/page', params),
  noticeRead: (id) => post(`/notice/${id}/read`),
  notices: (params) => get('/notice/page', params)
}

export const accountAdminApi = {
  page: params => get('/admin/accounts', params),
  create: data => post('/admin/accounts', data),
  invite: (id, data) => put(`/admin/accounts/mp/${id}/invite-code`, data),
  status: (type, id, data) => put(`/admin/accounts/${type}/${id}/login-status`, data),
  remove: (type, id, reason) => request('DELETE', `/admin/accounts/${type}/${id}`, { reason })
}
