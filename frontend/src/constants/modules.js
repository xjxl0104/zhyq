/**
 * 后端埋点模块 key → 中文名。
 *
 * key 的权威来源是库表 route_module_mapping.module(AccessLogFilter 按路由匹配后写进
 * access_log.module),使用度统计页直接拿这个 key 画图,于是横轴全是 receivable/finance
 * 这类英文,业务同事看不懂。中文名对齐系统自己的菜单叫法,不另造一套。
 *
 * 新增模块时这里若没登记,moduleLabel 原样返回 key —— 宁可显示英文,也不显示空白或猜错。
 */
export const MODULE_LABELS = Object.freeze({
  acc: '便捷通行',
  am: '资产管理',
  budget: '预算采购',
  building: '空间与资产',
  contract: '合同',
  crm: '招商',
  energy: '能耗',
  finance: '财务',
  iot: '智慧物联',
  oa: '办公 OA',
  property: '物业服务',
  receivable: '应收登记表',
  rsv: '资源预约',
  service: '惠企服务',
  space: '空间树',
  tenant: '租客',
  workflow: '工作流'
})

export function moduleLabel(key) {
  if (!key) return '未归类'
  return MODULE_LABELS[key] || key
}
