// 线索管理的下拉选项，取自《云仓产业园客户信息收集与回访登记表·设置-下拉选项》页。
// 单独成文件：登记表页与跟进记录抽屉都要用，且以后加选项只改这一处。

export const SOURCE_OPTIONS = [
  '电话咨询', '上门到访', '朋友介绍/转介绍', '线上广告/短视频',
  '招商中介', '展会/园区活动', '老客户复购', '其他'
]

export const CUSTOMER_TYPE_OPTIONS = [
  '意向租仓客户(找仓)',
  '云仓服务商(现有客户-需匹配货源)',
  '货主/电商卖家(需找云仓服务)',
  '其他'
]

export const COOP_MODE_OPTIONS = [
  '仓库整租', '仓库分租/小面积租赁', '云仓仓储外包', '一件代发',
  '仓配一体化', '货主找仓匹配', '云仓找货主匹配', '短期仓/季节仓', '其他'
]

export const GRADE_OPTIONS = ['A-高意向高价值', 'B-中等意向', 'C-低意向/长期培育']

/** 当前状态：数值与后端 LeadService.ST_* 一一对应 */
export const STATUS_OPTIONS = [
  { value: 1, label: '待跟进', type: 'info' },
  { value: 2, label: '跟进中', type: 'warning' },
  { value: 3, label: '已约看仓/已对接', type: 'primary' },
  { value: 4, label: '已报价/洽谈中', type: 'primary' },
  { value: 5, label: '已签约/已成交', type: 'success' },
  { value: 6, label: '已流失/暂缓', type: 'danger' }
]

export const FOLLOW_TYPE_OPTIONS = ['电话', '微信/在线沟通', '上门拜访', '邀约看仓', '邮件', '其他']

export const INTENT_CHANGE_OPTIONS = ['明显升温', '持平', '降温', '已成交', '已流失']

export const statusMeta = (v) => STATUS_OPTIONS.find((s) => s.value === v)
export const statusText = (v) => statusMeta(v)?.label || '—'
export const statusType = (v) => statusMeta(v)?.type || 'info'

/** 等级标签配色：A 红(最高优先) B 橙 C 灰，便于列表里一眼扫出优先跟进对象 */
export const gradeType = (g) => {
  if (!g) return 'info'
  if (g.startsWith('A')) return 'danger'
  if (g.startsWith('B')) return 'warning'
  return 'info'
}
