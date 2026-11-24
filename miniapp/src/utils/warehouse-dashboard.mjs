import { isWarehouseOwner } from './warehouse-workspace.mjs'

export const dashboardSections = ['客户与发货', '云仓管理', '账号与通知']
const entries = [
  ['warehouse-profile','云仓信息','资料与联系人','档',1,true],
  ['warehouse-customers','客户承接','分配与服务进度','客',0,true],
  ['warehouse-orders','出库单','查看发货记录','单',0,true],
  ['warehouse-rate-card','报价体系','运费与服务价卡','价',0],
  ['warehouse-contracts','合同与协议','签署件与备案','约',0],
  ['warehouse-staff','业务员管理','人员与账号','员',1],
  ['warehouse-apply','加盟申请','资料与资质','申',1],
  ['warehouse-onboarding','加盟进度','审核与上线','进',1],
  ['warehouse-protection','保护白名单','品牌与公司','保',1],
  ['warehouse-erp','ERP 接口','接入与同步','接',1],
  ['warehouse-settlement','账单与结算','核对账单','账',0],
  ['warehouse-notice','消息通知','业务提醒','信',2],
  ['account-security','账号与密码','登录安全','密',2]
].map(([page,title,note,icon,section,member]) => ({ page,title,note,icon,section,member }))

export function warehouseDashboardEntries(profile, stats) {
  const owner = isWarehouseOwner(profile)
  return entries.filter(item => item.page === 'warehouse-rate-card' ? owner && Number(stats?.joinStatus) === 5 : owner || item.member)
    .map(item => item.page === 'warehouse-profile' && !owner ? { ...item, title: '个人信息', note: '姓名与登录手机' } : item)
}
