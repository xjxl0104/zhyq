import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/Layout.vue'

const routes = [
  // 登录(不套 Layout)
  { path: '/login', name: 'Login', meta: { title: '登录', public: true }, component: () => import('@/views/Login.vue') },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', meta: { title: '首页' }, component: () => import('@/views/dashboard/Index.vue') },

      // 建筑
      { path: 'building/project', meta: { title: '项目管理' }, component: () => import('@/views/building/Project.vue') },
      { path: 'building/building', meta: { title: '建筑管理' }, component: () => import('@/views/building/Building.vue') },
      { path: 'building/room', meta: { title: '租控管理' }, component: () => import('@/views/building/RoomControl.vue') },
      { path: 'building/room/detail/:id', meta: { title: '房源详情' }, component: () => import('@/views/building/RoomDetail.vue') },

      // 招商
      { path: 'crm/lead', meta: { title: '线索管理' }, component: () => import('@/views/crm/Lead.vue') },
      { path: 'crm/customer', meta: { title: '意向客户' }, component: () => import('@/views/crm/Customer.vue') },
      { path: 'crm/plan', meta: { title: '销售计划' }, component: () => import('@/views/crm/Plan.vue') },
      { path: 'crm/commission', meta: { title: '佣金管理' }, component: () => import('@/views/crm/Commission.vue') },
      { path: 'crm/analysis', meta: { title: '招商分析' }, component: () => import('@/views/crm/Analysis.vue') },
      { path: 'crm/channel', meta: { title: '渠道管理' }, component: () => import('@/views/crm/Channel.vue') },

      // 租客
      { path: 'tenant/list', meta: { title: '租客列表' }, component: () => import('@/views/tenant/TenantList.vue') },
      { path: 'tenant/detail/:id', meta: { title: '租客详情' }, component: () => import('@/views/tenant/TenantDetail.vue') },
      { path: 'tenant/staff', meta: { title: '租客员工' }, component: () => import('@/views/tenant/Staff.vue') },
      { path: 'tenant/message', meta: { title: '站内信管理' }, component: () => import('@/views/tenant/Message.vue') },

      // 合同
      { path: 'contract/list', meta: { title: '合同列表' }, component: () => import('@/views/contract/ContractList.vue') },
      { path: 'contract/detail/:id', meta: { title: '合同详情' }, component: () => import('@/views/contract/ContractDetail.vue') },
      { path: 'contract/archive', meta: { title: '合同归档' }, component: () => import('@/views/contract/Archive.vue') },
      { path: 'contract/setting', meta: { title: '合同设置' }, component: () => import('@/views/contract/Setting.vue') },

      // 财务
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

      // 物业
      { path: 'property/workorder', meta: { title: '物业报修' }, component: () => import('@/views/property/WorkOrder.vue') },
      { path: 'property/meeting', meta: { title: '会议室预约' }, component: () => import('@/views/property/Meeting.vue') },
      { path: 'property/asset', meta: { title: '资产管理' }, component: () => import('@/views/property/Asset.vue') },
      { path: 'property/inspection', meta: { title: '设备巡检' }, component: () => import('@/views/property/Inspection.vue') },
      { path: 'property/complaint', meta: { title: '投诉建议', ftype: '投诉' }, component: () => import('@/views/property/Feedback.vue') },
      { path: 'property/feedback', meta: { title: '意见反馈', ftype: '意见' }, component: () => import('@/views/property/Feedback.vue') },
      { path: 'property/survey', meta: { title: '投票问卷' }, component: () => import('@/views/property/Survey.vue') },
      { path: 'property/activity', meta: { title: '物业活动' }, component: () => import('@/views/property/Activity.vue') },
      { path: 'property/patrol', meta: { title: '安防巡更' }, component: () => import('@/views/property/Patrol.vue') },
      { path: 'property/check-clean', meta: { title: '保洁检查', ctype: '保洁' }, component: () => import('@/views/property/Check.vue') },
      { path: 'property/check-green', meta: { title: '绿化检查', ctype: '绿化' }, component: () => import('@/views/property/Check.vue') },
      { path: 'property/check-quality', meta: { title: '品质核查', ctype: '品质' }, component: () => import('@/views/property/Check.vue') },

      // 资源预订(#23,统一目录+预订,叠加于既有会议室/场地预约)
      { path: 'rsv/resource', meta: { title: '资源目录' }, component: () => import('@/views/rsv/Resource.vue') },
      { path: 'rsv/booking', meta: { title: '资源预订' }, component: () => import('@/views/rsv/Booking.vue') },

      // 资产管理(#20,台账+签出签入/报废/维修/盘点)
      { path: 'am/asset', meta: { title: '资产管理' }, component: () => import('@/views/am/Asset.vue') },

      // 便捷通行(#21,门禁记录/访客登记/停车)
      { path: 'acc/access', meta: { title: '门禁通行记录' }, component: () => import('@/views/acc/Access.vue') },
      { path: 'acc/visitor', meta: { title: '访客登记' }, component: () => import('@/views/acc/Visitor.vue') },
      { path: 'acc/parking', meta: { title: '停车管理' }, component: () => import('@/views/acc/Parking.vue') },

      // 惠企
      { path: 'service/visitor', meta: { title: '访客预约' }, component: () => import('@/views/service/Visitor.vue') },
      { path: 'service/mall', meta: { title: '在线商城' }, component: () => import('@/views/service/Mall.vue') },
      { path: 'service/pass', meta: { title: '物品放行' }, component: () => import('@/views/service/Pass.vue') },
      { path: 'service/site', meta: { title: '场地预约' }, component: () => import('@/views/service/SiteBooking.vue') },
      { path: 'service/decoration', meta: { title: '装修申请' }, component: () => import('@/views/service/Decoration.vue') },
      { path: 'service/forum', meta: { title: '园区论坛' }, component: () => import('@/views/service/Forum.vue') },
      { path: 'service/passcard', meta: { title: '出入证管理' }, component: () => import('@/views/service/PassCard.vue') },
      { path: 'service/declare', meta: { title: '申报服务' }, component: () => import('@/views/service/Declare.vue') },
      { path: 'service/ip', meta: { title: '知产服务' }, component: () => import('@/views/service/Ip.vue') },
      { path: 'service/policy', meta: { title: '政策服务' }, component: () => import('@/views/service/Policy.vue') },
      { path: 'service/ecosystem', meta: { title: '生态配置' }, component: () => import('@/views/service/Ecosystem.vue') },

      // 办公
      { path: 'oa/task', meta: { title: '任务' }, component: () => import('@/views/oa/Task.vue') },
      { path: 'oa/schedule', meta: { title: '日程' }, component: () => import('@/views/oa/Schedule.vue') },
      { path: 'oa/notice', meta: { title: '企业公告' }, component: () => import('@/views/oa/Notice.vue') },
      { path: 'oa/approval', meta: { title: '审批中心' }, component: () => import('@/views/oa/Approval.vue') },
      { path: 'oa/recruit', meta: { title: '人才招聘' }, component: () => import('@/views/oa/Recruit.vue') },
      { path: 'oa/article', meta: { title: '文章管理' }, component: () => import('@/views/oa/Article.vue') },
      { path: 'oa/attendance', meta: { title: '考勤管理' }, component: () => import('@/views/oa/Attendance.vue') },
      { path: 'oa/document', meta: { title: '公文管理' }, component: () => import('@/views/oa/Document.vue') },
      { path: 'oa/flow', meta: { title: '流程管理' }, component: () => import('@/views/oa/Flow.vue') },

      // 应用中心
      { path: 'app/center', meta: { title: '应用中心' }, component: () => import('@/views/app/AppCenter.vue') },
      { path: 'app/vending', meta: { title: '自动售货机' }, component: () => import('@/views/app/Vending.vue') },

      // 能耗
      { path: 'energy/meter', meta: { title: '智能表计' }, component: () => import('@/views/energy/Meter.vue') },
      { path: 'energy/stats', meta: { title: '能耗统计' }, component: () => import('@/views/energy/Stats.vue') },

      // 智慧物联
      { path: 'iot/device', meta: { title: '智能硬件' }, component: () => import('@/views/iot/Device.vue') },
      { path: 'iot/alarm', meta: { title: '预警事件' }, component: () => import('@/views/iot/Alarm.vue') },
      { path: 'iot/point', meta: { title: '点位管理' }, component: () => import('@/views/iot/Point.vue') },
      { path: 'iot/channel', meta: { title: '通道管理' }, component: () => import('@/views/iot/Channel.vue') },
      { path: 'iot/vendor', meta: { title: '厂商配置' }, component: () => import('@/views/iot/Vendor.vue') },
      { path: 'iot/lock', meta: { title: '智能门锁', category: '门锁' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/parking', meta: { title: '智慧停车场', category: '停车' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/charging', meta: { title: '智能充电桩', category: '充电桩' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/access', meta: { title: '人脸识别门禁', category: '门禁' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/sensor', meta: { title: '空气质量传感器', category: '传感器' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/breaker', meta: { title: '智能空开', category: '空开' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/fire', meta: { title: '智慧安消', category: '消防' }, component: () => import('@/views/iot/DeviceCategory.vue') },
      { path: 'iot/camera', meta: { title: '智能安防', category: '摄像头' }, component: () => import('@/views/iot/DeviceCategory.vue') },

      // 数据中心
      { path: 'data/center', meta: { title: '数据看板' }, component: () => import('@/views/data/DataCenter.vue') },
      { path: 'data/section', meta: { title: '剖面图' }, component: () => import('@/views/data/SectionView.vue') },
      { path: 'data/report', meta: { title: '报表统计' }, component: () => import('@/views/data/ReportIndex.vue') },

      // 建议与反馈
      { path: 'suggestion/mine', meta: { title: '我的建议' }, component: () => import('@/views/suggestion/MySuggestions.vue') },
      { path: 'suggestion/manage', meta: { title: '建议管理' }, component: () => import('@/views/suggestion/SuggestionManage.vue') },

      // BI 使用度统计
      { path: 'bi/admin', meta: { title: '使用度统计(管理层)' }, component: () => import('@/views/bi/BiAdmin.vue') },
      { path: 'bi/product', meta: { title: '使用度统计(产品)' }, component: () => import('@/views/bi/BiProduct.vue') },

      // 系统管理
      { path: 'system/user', meta: { title: '用户管理' }, component: () => import('@/views/system/User.vue') },
      { path: 'system/role', meta: { title: '角色管理' }, component: () => import('@/views/system/Role.vue') },
      { path: 'system/dept', meta: { title: '部门管理' }, component: () => import('@/views/system/Dept.vue') },
      { path: 'system/menu', meta: { title: '菜单管理' }, component: () => import('@/views/system/Menu.vue') },
      { path: 'system/post', meta: { title: '岗位管理' }, component: () => import('@/views/system/Post.vue') },
      { path: 'system/dict', meta: { title: '字典管理' }, component: () => import('@/views/system/Dict.vue') },
      { path: 'system/resource', meta: { title: '运营资源' }, component: () => import('@/views/system/Resource.vue') },
      { path: 'system/message', meta: { title: '消息中心' }, component: () => import('@/views/system/MessageCenter.vue') }
    ]
  },
  // 监控大屏(独立全屏,不套 Layout)
  { path: '/screen', name: 'Screen', meta: { title: '一体监控大屏' }, component: () => import('@/views/screen/BigScreen.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫:无 token 一律送去登录页
router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!localStorage.getItem('zhyq_token')) {
    return { path: '/login' }
  }
  return true
})

export default router
