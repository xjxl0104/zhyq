export const ADMIN_VIEWS = [
  { value: 'personal', label: '个人总览' },
  { value: 'partners', label: '伙伴总览' },
  { value: 'warehouses', label: '云仓总览' }
]

export function overviewRange(preset, now = new Date()) {
  // Business statistics follow the park's Shanghai calendar, independent of device timezone.
  const shanghai = new Date(now.getTime() + 8 * 60 * 60 * 1000)
  const today = shanghai.toISOString().slice(0, 10)
  if (preset === 'all') return { startDate: '2000-01-01', endDate: today }
  if (preset === 'month') return { startDate: today.slice(0, 8) + '01', endDate: today }
  const start = new Date(today + 'T00:00:00Z')
  start.setUTCDate(start.getUTCDate() - 29)
  return { startDate: start.toISOString().slice(0, 10), endDate: today }
}

export function overviewQuery({ view, selectionId, group = 'p4', keyword = '', range, page = 1 }) {
  if (!['partners', 'warehouses'].includes(view)) throw new Error('请选择总览视图')
  return { view, ...(selectionId ? { selectionId } : {}), group,
    keyword: String(keyword).trim(), ...range, page, size: 20 }
}

export const overviewMoney = value => value == null ? '—' : Number(value).toFixed(3)
export const overviewCount = value => value == null ? '—' : String(value)
export const PARTNER_STATUS = { 1: '正常', 2: '冻结', 3: '待审核', 4: '已退出' }
export const WAREHOUSE_STATUS = { 1: '申请中', 2: '资质审核', 3: '对接中', 4: '试运行', 5: '已上线', 6: '暂停', 7: '已退出', 8: '待删除' }
export const CONTRACT_STATUS = { 1: '草稿', 2: '待审核', 3: '待签署', 4: '已生效', 5: '履约中', 6: '变更中', 7: '到期', 8: '终止', 9: '作废' }
