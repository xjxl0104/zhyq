import request from '@/utils/request'

// 全民营销(园区伙伴 · 云仓生态)。后端前缀 /crm/marketing/**,规范见 docs/marketing/PARK-MKT-001 §5.1
const BASE = '/crm/marketing'
const upload = (url, formData) => request.post(url, formData, { headers: { 'Content-Type': 'multipart/form-data' } })

// 看板
export const mktDashboardApi = {
  summary: (params) => request.get(`${BASE}/dashboard/summary`, { params }),
  funnel: (params) => request.get(`${BASE}/dashboard/funnel`, { params }),
  trend: (params) => request.get(`${BASE}/dashboard/trend`, { params })
}

// 伙伴
export const mktPromoterApi = {
  page: (params) => request.get(`${BASE}/promoter/page`, { params }),
  get: (id) => request.get(`${BASE}/promoter/${id}`),
  team: (id) => request.get(`${BASE}/promoter/${id}/team`),
  customers: (id, params) => request.get(`${BASE}/promoter/${id}/customers`, { params }),
  orders: (id, params) => request.get(`${BASE}/promoter/${id}/orders`, { params }),
  commissions: (id, params) => request.get(`${BASE}/promoter/${id}/commissions`, { params }),
  withdrawals: (id, params) => request.get(`${BASE}/promoter/${id}/withdrawals`, { params }),
  history: (id) => request.get(`${BASE}/promoter/${id}/history`),
  // 阶段 A 没有小程序,后台手工录入伙伴
  manual: (data) => request.post(`${BASE}/promoter/manual`, data),
  audit: (id, data) => request.post(`${BASE}/promoter/${id}/audit`, data),
  position: (id, data) => request.post(`${BASE}/promoter/${id}/position`, data),
  parent: (id, data) => request.post(`${BASE}/promoter/${id}/parent`, data),
  freeze: (id, data) => request.post(`${BASE}/promoter/${id}/freeze`, data),
  unfreeze: (id) => request.post(`${BASE}/promoter/${id}/unfreeze`),
  resetInvite: (id) => request.post(`${BASE}/promoter/${id}/reset-invite`)
}

// 岗位与份额
export const mktPositionApi = {
  list: () => request.get(`${BASE}/position/list`),
  update: (data) => request.put(`${BASE}/position`, data),
  depth: () => request.get(`${BASE}/position/depth`),
  setDepth: (data) => request.put(`${BASE}/position/depth`, data),
  reviewNow: () => request.post(`${BASE}/position/review-now`)
}

// 客户评级
export const mktGradeApi = {
  list: () => request.get(`${BASE}/grade/list`),
  update: (data) => request.put(`${BASE}/grade`, data)
}

// 客户(复用 crm_customer,加评级/推荐人/签约方式/锁定)
export const mktCustomerApi = {
  page: (params) => request.get(`${BASE}/customer/page`, { params }),
  get: (id) => request.get(`${BASE}/customer/${id}`),
  suggestGrade: (id) => request.get(`${BASE}/customer/${id}/suggest-grade`),
  grade: (id, data) => request.post(`${BASE}/customer/${id}/grade`, data),
  signMode: (id, data) => request.post(`${BASE}/customer/${id}/sign-mode`, data),
  referrer: (id, data) => request.post(`${BASE}/customer/${id}/referrer`, data),
  lose: (id, data) => request.post(`${BASE}/customer/${id}/lose`, data),
  // 锁定
  lock: (id) => request.get(`${BASE}/customer/${id}/lock`),
  prelock: (id, data) => request.post(`${BASE}/customer/${id}/lock/prelock`, data),
  confirmLock: (lockId) => request.post(`${BASE}/customer/lock/${lockId}/confirm`),
  extendLock: (lockId, data) => request.post(`${BASE}/customer/lock/${lockId}/extend`, data),
  releaseLock: (lockId, data) => request.post(`${BASE}/customer/lock/${lockId}/release`, data),
  transferLock: (lockId, data) => request.post(`${BASE}/customer/lock/${lockId}/transfer`, data)
}

// 云仓服务合同
export const mktContractApi = {
  page: (params) => request.get(`${BASE}/contract/page`, { params }),
  get: (id) => request.get(`${BASE}/contract/${id}`),
  versions: (id) => request.get(`${BASE}/contract/${id}/versions`),
  create: (data) => request.post(`${BASE}/contract`, data),
  update: (data) => request.put(`${BASE}/contract`, data),
  submit: (id) => request.post(`${BASE}/contract/${id}/submit`),
  audit: (id, data) => request.post(`${BASE}/contract/${id}/audit`, data),
  signOffline: (id, data) => request.post(`${BASE}/contract/${id}/sign-offline`, data),
  effectDirect: (id) => request.post(`${BASE}/contract/${id}/effect-direct`),
  perform: (id) => request.post(`${BASE}/contract/${id}/perform`),
  amend: (id, data) => request.post(`${BASE}/contract/${id}/amend`, data),
  amendDone: (id) => request.post(`${BASE}/contract/${id}/amend-done`),
  renew: (id, data) => request.post(`${BASE}/contract/${id}/renew`, data),
  terminate: (id, data) => request.post(`${BASE}/contract/${id}/terminate`, data),
  void: (id, data) => request.post(`${BASE}/contract/${id}/void`, data)
}

// 合同模板
export const mktTemplateApi = {
  page: (params) => request.get(`${BASE}/template/page`, { params }),
  list: () => request.get(`${BASE}/template/list`),
  add: (data) => request.post(`${BASE}/template`, data),
  update: (data) => request.put(`${BASE}/template`, data),
  remove: (id) => request.delete(`${BASE}/template/${id}`)
}

// 云仓 + 加盟
export const mktWarehouseApi = {
  page: (params) => request.get(`${BASE}/warehouse/page`, { params }),
  online: () => request.get(`${BASE}/warehouse/online`),
  get: (id) => request.get(`${BASE}/warehouse/${id}`),
  apply: (data) => request.post(`${BASE}/warehouse`, data),
  update: (data) => request.put(`${BASE}/warehouse`, data),
  steps: (id) => request.get(`${BASE}/warehouse/${id}/steps`),
  passQualification: (id) => request.post(`${BASE}/warehouse/${id}/qualify/pass`),
  rejectQualification: (id, data) => request.post(`${BASE}/warehouse/${id}/qualify/reject`, data),
  markErp: (id) => request.post(`${BASE}/warehouse/${id}/erp/mark-connected`),
  signAgreement: (id, data) => request.post(`${BASE}/warehouse/${id}/agreement`, data),
  pause: (id, data) => request.post(`${BASE}/warehouse/${id}/pause`, data),
  resume: (id) => request.post(`${BASE}/warehouse/${id}/resume`),
  exit: (id, data) => request.post(`${BASE}/warehouse/${id}/exit`, data)
}

// 计佣订单
export const mktOrderApi = {
  page: (params) => request.get(`${BASE}/order/page`, { params }),
  get: (id) => request.get(`${BASE}/order/${id}`),
  importExcel: (formData) => upload(`${BASE}/order/import`, formData),
  templateUrl: `/api${BASE}/order/import-template`,
  void: (id, data) => request.post(`${BASE}/order/${id}/void`, data),
  splits: (id) => request.get(`${BASE}/order/${id}/splits`)
}

// 佣金结算
export const mktCommissionApi = {
  page: (params) => request.get(`${BASE}/commission/page`, { params }),
  settle: (data) => request.post(`${BASE}/commission/settle`, data),
  void: (id, data) => request.post(`${BASE}/commission/${id}/void`, data),
  batches: (params) => request.get(`${BASE}/commission/batches`, { params })
}

// 提现
export const mktWithdrawalApi = {
  page: (params) => request.get(`${BASE}/withdrawal/page`, { params }),
  balance: (promoterId) => request.get(`${BASE}/withdrawal/balance/${promoterId}`),
  manual: (data) => request.post(`${BASE}/withdrawal/manual`, data),
  approve: (id) => request.post(`${BASE}/withdrawal/${id}/approve`),
  reject: (id, data) => request.post(`${BASE}/withdrawal/${id}/reject`, data),
  pay: (id, data) => request.post(`${BASE}/withdrawal/${id}/pay`, data)
}

// 规则参数 / 审计日志
export const mktSettingApi = {
  all: () => request.get(`${BASE}/setting`),
  update: (data) => request.put(`${BASE}/setting`, data)
}
export const mktAuditApi = {
  page: (params) => request.get(`${BASE}/audit/page`, { params })
}
