import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/Layout.vue'
import { useAccessStore } from '@/stores/access'
import { canAccessNavigation, firstNavigationPath } from '@/utils/navigationAccess'

const routes = [
  // Local photo/model demo has no backend access and is excluded from production.
  ...(import.meta.env.DEV ? [
    { path: '/twin-preview', name: 'TwinPreview', meta: { title: '云仓三维预览', public: true, twinPreview: true }, component: () => import('@/views/twin/TwinHome.vue') },
    { path: '/twin-preview/module/:module(park|property|contract|camera|fire|energy)', name: 'TwinModulePreview', meta: { title: '空间业务预览', public: true, twinPreview: true }, component: () => import('@/views/twin/TwinHome.vue') },
  ] : []),
  // 登录(不套 Layout)
  { path: '/login', name: 'Login', meta: { title: '登录', public: true }, component: () => import('@/views/Login.vue') },
  // 手机自助报修:业主扫码/点链接直接进,不需要账号
  { path: '/m/repair', name: 'MobileRepair', meta: { title: '园区报修', public: true }, component: () => import('@/views/mobile/MobileRepair.vue') },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', meta: { title: '首页' }, component: () => import('@/views/twin/TwinDashboard.vue') },
      { path: 'overview', name: 'Overview', meta: { title: '经营看板' }, component: () => import('@/views/dashboard/Index.vue') },

      // 建筑
      { path: 'building/project', meta: { title: '项目管理' }, component: () => import('@/views/building/Project.vue') },
      { path: 'building/building', meta: { title: '建筑管理' }, component: () => import('@/views/building/Building.vue') },
      { path: 'building/room', meta: { title: '租控管理' }, component: () => import('@/views/building/RoomControl.vue') },
      { path: 'building/room/detail/:id', meta: { title: '房源详情' }, component: () => import('@/views/building/RoomDetail.vue') },

      // 招商
      { path: 'crm/lead', meta: { title: '线索管理' }, component: () => import('@/views/crm/Lead.vue') },
      { path: 'crm/customer', meta: { title: '意向客户' }, component: () => import('@/views/crm/Customer.vue') },
      { path: 'crm/plan', meta: { title: '销售计划' }, component: () => import('@/views/crm/Plan.vue') },
      { path: 'crm/commission', meta: { title: '渠道中介返佣' }, component: () => import('@/views/crm/Commission.vue') },
      { path: 'crm/analysis', meta: { title: '招商分析' }, component: () => import('@/views/crm/Analysis.vue') },
      { path: 'crm/channel', meta: { title: '中介管理' }, component: () => import('@/views/crm/Channel.vue') },

      // 招商 › 全民营销(园区伙伴 · 云仓生态)
      { path: 'crm/marketing/dashboard', meta: { title: '营销看板' }, component: () => import('@/views/crm/marketing/Dashboard.vue') },
      { path: 'crm/marketing/promoter', meta: { title: '伙伴管理' }, component: () => import('@/views/crm/marketing/Promoter.vue') },
      { path: 'crm/marketing/position', meta: { title: '角色与称号' }, component: () => import('@/views/crm/marketing/Position.vue') },
      { path: 'crm/marketing/grade', meta: { title: '客户评级' }, component: () => import('@/views/crm/marketing/Grade.vue') },
      { path: 'crm/marketing/customer', meta: { title: '客户管理' }, component: () => import('@/views/crm/marketing/Customer.vue') },
      { path: 'crm/marketing/contract', meta: { title: '服务合同' }, component: () => import('@/views/crm/marketing/ServiceContract.vue') },
      { path: 'crm/marketing/template', meta: { title: '合同模板' }, component: () => import('@/views/crm/marketing/ContractTemplate.vue') },
      { path: 'crm/marketing/warehouse', meta: { title: '云仓管理' }, component: () => import('@/views/crm/marketing/Warehouse.vue') },
      { path: 'crm/marketing/quote-rates', meta: { title: '报价体系' }, component: () => import('@/views/crm/marketing/QuoteRates.vue') },
      { path: 'crm/marketing/onboarding', meta: { title: '加盟申请' }, component: () => import('@/views/crm/marketing/Onboarding.vue') },
      { path: 'crm/marketing/order', meta: { title: '计佣订单' }, component: () => import('@/views/crm/marketing/ReferralOrder.vue') },
      { path: 'crm/marketing/commission', meta: { title: '伙伴佣金结算' }, component: () => import('@/views/crm/marketing/Commission.vue') },
      { path: 'crm/marketing/bill', meta: { title: '服务费账单' }, component: () => import('@/views/crm/marketing/Bill.vue') },
      { path: 'crm/marketing/settlement', meta: { title: '云仓结算' }, component: () => import('@/views/crm/marketing/Settlement.vue') },
      { path: 'crm/marketing/withdrawal', meta: { title: '提现审核' }, component: () => import('@/views/crm/marketing/Withdrawal.vue') },
      { path: 'crm/marketing/setting', meta: { title: '规则与字典' }, component: () => import('@/views/crm/marketing/Setting.vue') },
      { path: 'crm/marketing/audit', meta: { title: '审计日志' }, component: () => import('@/views/crm/marketing/AuditLog.vue') },
      { path: 'crm/marketing/erp', meta: { title: '单量与ERP对接' }, component: () => import('@/views/crm/marketing/Erp.vue') },

      // 租客
      { path: 'tenant/list', meta: { title: '租客列表' }, component: () => import('@/views/tenant/TenantList.vue') },
      { path: 'tenant/detail/:id', meta: { title: '租客详情' }, component: () => import('@/views/tenant/TenantDetail.vue') },
      { path: 'tenant/staff', meta: { title: '租客员工' }, component: () => import('@/views/tenant/Staff.vue') },
      { path: 'tenant/message', meta: { title: '站内信管理' }, component: () => import('@/views/tenant/Message.vue') },

      // 合同
      { path: 'contract/list', meta: { title: '正式合同' }, component: () => import('@/views/contract/ContractList.vue') },
      { path: 'contract/detail/:id', meta: { title: '合同详情' }, component: () => import('@/views/contract/ContractDetail.vue') },
      { path: 'contract/archive', redirect: '/contract/list' },
      { path: 'contract/simple', meta: { title: '简易合同' }, component: () => import('@/views/contract/SimpleContract.vue') },
      { path: 'contract/template', meta: { title: '合同模板' }, component: () => import('@/views/contract/ContractTemplate.vue') },
      { path: 'contract/setting', meta: { title: '合同设置' }, component: () => import('@/views/contract/Setting.vue') },

      // 财务
      { path: 'finance/overview', meta: { title: '财务总览' }, component: () => import('@/views/finance/Overview.vue') },
      { path: 'finance/receivable-register', meta: { title: '应收明细登记表' }, component: () => import('@/views/finance/ReceivableRegister.vue') },
      { path: 'finance/bill', meta: { title: '所有账单' }, component: () => import('@/views/finance/Bill.vue') },
      { path: 'finance/overdue', meta: { title: '逾期账单' }, component: () => import('@/views/finance/Overdue.vue') },
      { path: 'finance/report', meta: { title: '财务报表' }, component: () => import('@/views/finance/Report.vue') },
      { path: 'finance/flow', meta: { title: '收支流水' }, component: () => import('@/views/finance/Flow.vue') },
      { path: 'finance/invoice', meta: { title: '发票记录' }, component: () => import('@/views/finance/Invoice.vue') },
      { path: 'finance/cashier', meta: { title: '收银台' }, component: () => import('@/views/finance/Cashier.vue') },
      { path: 'finance/receipt', meta: { title: '收据记录' }, component: () => import('@/views/finance/Receipt.vue') },
      { path: 'finance/checkout-report', meta: { title: '退房报表' }, component: () => import('@/views/finance/CheckoutReport.vue') },
      { path: 'finance/notice', meta: { title: '收款通知' }, component: () => import('@/views/finance/PayNotice.vue') },
      { path: 'finance/setting', meta: { title: '财务设置' }, component: () => import('@/views/finance/Setting.vue') },

      // 物业服务
      { path: 'property/workorder-summary', meta: { title: '工单汇总' }, component: () => import('@/views/property/WorkOrderSummary.vue') },
      { path: 'property/workorder', meta: { title: '物业报修' }, component: () => import('@/views/property/WorkOrder.vue') },
      { path: 'property/responsible-unit', meta: { title: '供应商档案' }, component: () => import('@/views/budget/Supplier.vue') },
      { path: 'property/asset', meta: { title: '物资管理' }, component: () => import('@/views/property/Asset.vue') },
      { path: 'property/inspection', meta: { title: '设备巡检' }, component: () => import('@/views/property/Inspection.vue') },
      { path: 'property/patrol', meta: { title: '安防巡更' }, component: () => import('@/views/property/Patrol.vue') },
      { path: 'property/check-clean', meta: { title: '保洁检查', ctype: '保洁' }, component: () => import('@/views/property/Check.vue') },
      { path: 'property/check-green', meta: { title: '绿化检查', ctype: '绿化' }, component: () => import('@/views/property/Check.vue') },
      // 菜单已下线;保留隐藏路由仅用于历史工单「查看来源记录(FEEDBACK)」防死链
      { path: 'property/feedback', meta: { title: '意见反馈', ftype: '意见' }, component: () => import('@/views/property/Feedback.vue') },

      // 预算管理(园区运营下,与办公同级):预算(附件+预算申请审批) + 采购计划(计划下提采购申请)
      // 取代原「办公 → 采购管理」:采购计划/申请沿用 pur 后端接口,页面并入本板块
      // 一组件多路由:年度/月度靠 meta.budgetType / meta.planType 区分
      { path: 'budget/annual', meta: { title: '年度预算', budgetType: 1 }, component: () => import('@/views/budget/Budget.vue') },
      { path: 'budget/monthly', meta: { title: '月度预算', budgetType: 2 }, component: () => import('@/views/budget/Budget.vue') },
      { path: 'budget/plan-year', meta: { title: '年度采购计划', planType: 1 }, component: () => import('@/views/budget/PurPlan.vue') },
      { path: 'budget/plan-month', meta: { title: '月度采购计划', planType: 2 }, component: () => import('@/views/budget/PurPlan.vue') },
      { path: 'budget/flow', meta: { title: '审批流程' }, component: () => import('@/views/budget/FlowConfig.vue') },
      { path: 'budget/supplier', redirect: '/property/responsible-unit' },
      { path: 'budget/supplier-contract', meta: { title: '供应商合同' }, component: () => import('@/views/budget/SupplierContract.vue') },

      // 物业服务(原惠企保留模块)
      { path: 'service/visitor', meta: { title: '访客预约' }, component: () => import('@/views/service/Visitor.vue') },
      { path: 'service/decoration', meta: { title: '装修及动火' }, component: () => import('@/views/service/Decoration.vue') },

      // 办公管理
      { path: 'oa/task', meta: { title: '任务管理' }, component: () => import('@/views/oa/Task.vue') },
      { path: 'oa/approval', meta: { title: '审批中心' }, component: () => import('@/views/oa/Approval.vue') },
      { path: 'oa/seal', meta: { title: '印章管理' }, component: () => import('@/views/oa/Seal.vue') },
      { path: 'oa/document', meta: { title: '公文管理' }, component: () => import('@/views/oa/Document.vue') },
      { path: 'oa/flow', meta: { title: '流程管理' }, component: () => import('@/views/oa/Flow.vue') },
      { path: 'archive/doc', meta: { title: '资料库' }, component: () => import('@/views/archive/Doc.vue') },
      { path: 'archive/setting', meta: { title: '分类与规则' }, component: () => import('@/views/archive/Setting.vue') },

      // 应用中心
      { path: 'app/center', meta: { title: '应用中心' }, component: () => import('@/views/app/AppCenter.vue') },
      { path: 'app/vending', meta: { title: '自动售货机' }, component: () => import('@/views/app/Vending.vue') },

      // 缴费管理(园区运营下)
      // 合同基础台账统一对接招商正式合同;保证金按合同台账登记;租金 / 物业费 一组件两路由;电费 / 水费 一组件两路由
      { path: 'energy/contracts', meta: { title: '合同基础台账' }, component: () => import('@/views/energy/LeaseContracts.vue') },
      { path: 'energy/deposit', meta: { title: '保证金' }, component: () => import('@/views/energy/LeaseDeposit.vue') },
      { path: 'energy/rent', meta: { title: '租金', leaseFeeType: 'RENT' }, component: () => import('@/views/energy/LeaseFeeSheet.vue') },
      { path: 'energy/property', meta: { title: '物业费', leaseFeeType: 'PROPERTY' }, component: () => import('@/views/energy/LeaseFeeSheet.vue') },
      { path: 'energy/electric', meta: { title: '电费', energyType: '电' }, component: () => import('@/views/energy/UtilityFeeSheet.vue') },
      { path: 'energy/water', meta: { title: '水费', energyType: '水' }, component: () => import('@/views/energy/UtilityFeeSheet.vue') },
      { path: 'energy/garbage', meta: { title: '垃圾处理费' }, component: () => import('@/views/energy/GarbageFee.vue') },
      { path: 'energy/summary', meta: { title: '缴费汇总' }, component: () => import('@/views/energy/FeeSummary.vue') },

      // 智慧物联
      { path: 'iot/alarm', meta: { title: '预警事件' }, component: () => import('@/views/iot/Alarm.vue') },
      { path: 'iot/camera', meta: { title: '智能安防', category: '摄像头' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/parking', meta: { title: '智慧停车场', category: '停车' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/fire', meta: { title: '智慧安消', category: '消防' }, component: () => import('@/views/iot/DeviceCategory.vue') },

      // 数据中心
      { path: 'data/center', meta: { title: '数据看板' }, component: () => import('@/views/data/DataCenter.vue') },
      { path: 'data/section', meta: { title: '剖面图' }, component: () => import('@/views/data/SectionView.vue') },
      { path: 'data/report', meta: { title: '报表统计' }, component: () => import('@/views/data/ReportIndex.vue') },
      { path: 'screen', name: 'Screen', redirect: to => ({ path: '/data/center', query: to.query, hash: to.hash }) },

      // 建议与反馈
      { path: 'suggestion/mine', meta: { title: '我的建议' }, component: () => import('@/views/suggestion/MySuggestions.vue') },
      { path: 'suggestion/manage', meta: { title: '建议管理' }, component: () => import('@/views/suggestion/SuggestionManage.vue') },

      // BI 使用度统计
      { path: 'bi/admin', meta: { title: '使用度统计(管理层)' }, component: () => import('@/views/bi/BiAdmin.vue') },
      { path: 'bi/product', meta: { title: '使用度统计(产品)' }, component: () => import('@/views/bi/BiProduct.vue') },

      // 系统管理
      { path: 'system/user', meta: { title: '用户管理' }, component: () => import('@/views/system/User.vue') },
      { path: 'system/role', meta: { title: '角色管理' }, component: () => import('@/views/system/Role.vue') },
      { path: 'system/dept', redirect: '/system/user' },
      { path: 'system/menu', meta: { title: '菜单管理' }, component: () => import('@/views/system/Menu.vue') },
      { path: 'system/post', redirect: '/system/role' },
      { path: 'system/dict', meta: { title: '字典管理' }, component: () => import('@/views/system/Dict.vue') },
      { path: 'system/resource', meta: { title: '运营资源' }, component: () => import('@/views/system/Resource.vue') },
      { path: 'system/message', meta: { title: '消息中心' }, component: () => import('@/views/system/MessageCenter.vue') }
    ]
  },
  // 已下线菜单的旧书签/旧标签页刷新时兜底回首页，避免空路由白屏
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Route access is loaded from the active role and direct grants, never from a JWT snapshot.
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!localStorage.getItem('zhyq_token')) {
    return { path: '/login' }
  }
  const access = useAccessStore()
  try {
    await access.load()
  } catch (error) {
    console.error('加载权限失败', error)
    return { path: '/login' }
  }
  if (!canAccessNavigation(to.path, access.paths, access.admin)) {
    const fallback = firstNavigationPath(access.paths, access.admin)
    if (fallback !== to.path) return { path: fallback, replace: true }
  }
  return true
})

// 部署后旧 chunk 失效(动态 import 404):整页重新导航拉取新版本,同一目标只自动重试一次
router.onError((err, to) => {
  if (/dynamically imported module|Importing a module script|Unable to preload/i.test(err?.message || '')) {
    const key = 'chunk-reload:' + (to?.fullPath || location.pathname)
    if (!sessionStorage.getItem(key)) {
      sessionStorage.setItem(key, '1')
      location.assign(to?.fullPath || location.href)
    }
  }
})
window.addEventListener('vite:preloadError', (e) => {
  e.preventDefault()
  location.reload()
})

export default router
