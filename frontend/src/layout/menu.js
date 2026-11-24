export const menuTree = [
  {
    title: '工作台', icon: 'HomeFilled', children: [
      { title: '首页', path: '/dashboard' },
      { title: '经营看板', path: '/overview' },
      {
        title: '数据中心', icon: 'DataLine', children: [
          { title: '数据看板', path: '/data/center' },
          { title: '剖面图', path: '/data/section' },
          { title: '报表统计', path: '/data/report' }
        ]
      }
    ]
  },
  {
    title: '项目概况', icon: 'OfficeBuilding', children: [
      { title: '项目管理', path: '/building/project' },
      { title: '建筑管理', path: '/building/building' },
      { title: '租控管理', path: '/building/room' }
    ]
  },
  {
    title: '招商租赁', icon: 'Promotion', children: [
      {
        title: '招商', icon: 'Promotion', children: [
          { title: '中介管理', path: '/crm/channel' },
          { title: '线索管理', path: '/crm/lead' },
          { title: '意向客户', path: '/crm/customer' },
          { title: '销售计划', path: '/crm/plan' },
          { title: '渠道中介返佣', path: '/crm/commission' },
          { title: '招商分析', path: '/crm/analysis' }
        ]
      },
      {
        // 子项很多:侧边栏按 group 合并成一个入口,同组页面在页内用标签切换(见 navigationAccess / SectionTabs)。
        // 角色与称号(/position)、客户评级(/grade)已收折进「规则与字典」内嵌字典页签；空壳合同模板(/template)隐藏。
        title: '全民营销', icon: 'Share', children: [
          { title: '营销看板', path: '/crm/marketing/dashboard', group: '看板' },
          { title: '伙伴管理', path: '/crm/marketing/promoter', group: '伙伴' },
          { title: '客户管理', path: '/crm/marketing/customer', group: '客户' },
          { title: '云仓管理', path: '/crm/marketing/warehouse', group: '云仓' },
          { title: '加盟申请', path: '/crm/marketing/onboarding', group: '云仓' },
          { title: '单量与ERP对接', path: '/crm/marketing/erp', group: '云仓' },
          { title: '报价体系', path: '/crm/marketing/quote-rates', group: '云仓' },
          { title: '服务合同', path: '/crm/marketing/contract', group: '合同' },
          { title: '计佣订单', path: '/crm/marketing/order', group: '结算' },
          { title: '伙伴佣金结算', path: '/crm/marketing/commission', group: '结算' },
          { title: '服务费账单', path: '/crm/marketing/bill', group: '结算' },
          { title: '云仓结算', path: '/crm/marketing/settlement', group: '结算' },
          { title: '提现审核', path: '/crm/marketing/withdrawal', group: '结算' },
          { title: '规则与字典', path: '/crm/marketing/setting', group: '设置' },
          { title: '审计日志', path: '/crm/marketing/audit', group: '设置' }
        ]
      },
      {
        title: '合同', icon: 'Document', children: [
          { title: '合同列表', path: '/contract/list' },
          { title: '合同归档', path: '/contract/archive' },
          { title: '合同设置', path: '/contract/setting' }
        ]
      },
      {
        title: '租客', icon: 'User', children: [
          { title: '租客列表', path: '/tenant/list' },
          { title: '租客员工', path: '/tenant/staff' },
          { title: '站内信管理', path: '/tenant/message' }
        ]
      }
    ]
  },
  {
    title: '财务', icon: 'Money', children: [
      { title: '财务总览', path: '/finance/overview' },
      { title: '应收明细登记表', path: '/finance/receivable-register' },
      { title: '所有账单', path: '/finance/bill' },
      { title: '收银台', path: '/finance/cashier' },
      { title: '逾期账单', path: '/finance/overdue' },
      { title: '收支流水', path: '/finance/flow' },
      { title: '收据记录', path: '/finance/receipt' },
      { title: '发票记录', path: '/finance/invoice' },
      { title: '收款通知', path: '/finance/notice' },
      { title: '退房报表', path: '/finance/checkout-report' },
      { title: '财务报表', path: '/finance/report' },
      { title: '财务设置', path: '/finance/setting' }
    ]
  },
  {
    title: '物业服务', icon: 'Tools', children: [
      { title: '工单汇总', path: '/property/workorder-summary' },
      { title: '物业报修', path: '/property/workorder' },
      { title: '供应商档案', path: '/property/responsible-unit' },
      { title: '设备巡检', path: '/property/inspection' },
      { title: '安防巡更', path: '/property/patrol' },
      { title: '保洁检查', path: '/property/check-clean' },
      { title: '绿化检查', path: '/property/check-green' },
      { title: '访客预约', path: '/service/visitor' },
      { title: '在线商城', path: '/service/mall' },
      { title: '物品放行', path: '/service/pass' },
      { title: '场地预约', path: '/service/site' },
      { title: '装修申请', path: '/service/decoration' }
    ]
  },
  {
    title: '智慧物联', icon: 'VideoCamera', children: [
      { title: '预警事件', path: '/iot/alarm' },
      { title: '智能安防', path: '/iot/camera' },
      { title: '智慧停车场', path: '/iot/parking' },
      { title: '智慧安消', path: '/iot/fire' }
    ]
  },
  {
    title: '园区运营', icon: 'Calendar', children: [
      { title: '物资管理', path: '/property/asset' },
      {
        title: '缴费管理', icon: 'Tickets', children: [
          { title: '保证金', path: '/energy/deposit' },
          { title: '租金', path: '/energy/rent' },
          { title: '物业费', path: '/energy/property' },
          { title: '电费', path: '/energy/electric' },
          { title: '水费', path: '/energy/water' },
          { title: '垃圾处理费', path: '/energy/garbage' },
          { title: '缴费汇总', path: '/energy/summary' }
        ]
      },
      {
        title: '办公', icon: 'Calendar', children: [
          { title: '任务', path: '/oa/task' },
          { title: '日程', path: '/oa/schedule' },
          { title: '企业公告', path: '/oa/notice' },
          { title: '审批中心', path: '/oa/approval' },
          { title: '人才招聘', path: '/oa/recruit' },
          { title: '文章管理', path: '/oa/article' },
          { title: '考勤管理', path: '/oa/attendance' },
          { title: '公文管理', path: '/oa/document' },
          { title: '流程管理', path: '/oa/flow' }
        ]
      },
      {
        title: '预算管理', icon: 'Wallet', children: [
          { title: '年度预算', path: '/budget/annual' },
          { title: '月度预算', path: '/budget/monthly' },
          { title: '年度采购计划', path: '/budget/plan-year' },
          { title: '月度采购计划', path: '/budget/plan-month' },
          { title: '审批流程', path: '/budget/flow' }
        ]
      },
      {
        title: '资料管理', icon: 'FolderOpened', children: [
          { title: '资料库', path: '/archive/doc' }
        ]
      },
      { title: '应用中心', path: '/app/center' }
    ]
  },
  {
    title: '使用度统计', icon: 'DataAnalysis', children: [
      { title: '管理层视图', path: '/bi/admin' },
      { title: '产品团队视图', path: '/bi/product' }
    ]
  },
  {
    title: '建议与反馈', icon: 'ChatDotRound', children: [
      { title: '我的建议', path: '/suggestion/mine' },
      { title: '建议管理', path: '/suggestion/manage' }
    ]
  },
  {
    title: '系统管理', icon: 'Setting', children: [
      { title: '角色管理', path: '/system/role' },
      { title: '用户管理', path: '/system/user' },
      { title: '菜单管理', path: '/system/menu' },
      { title: '字典管理', path: '/system/dict' },
      { title: '运营资源', path: '/system/resource' },
      { title: '消息中心', path: '/system/message' }
    ]
  }
]
