export const menuTree = [
  {
    title: '工作台', icon: 'HomeFilled', children: [
      { title: '首页', path: '/dashboard' },
      {
        title: '数据中心', icon: 'DataLine', children: [
          { title: '数据看板', path: '/data/center' },
          { title: '剖面图', path: '/data/section' },
          { title: '报表统计', path: '/data/report' },
          { title: '监控大屏', path: '/screen', target: '_blank' }
        ]
      }
    ]
  },
  {
    title: '空间与资产', icon: 'OfficeBuilding', children: [
      {
        title: '建筑', icon: 'OfficeBuilding', children: [
          { title: '租控管理', path: '/building/room' },
          { title: '项目管理', path: '/building/project' },
          { title: '建筑管理', path: '/building/building' }
        ]
      },
      { title: '资产管理', path: '/property/asset' },
      { title: '资产管理', path: '/am/asset' }
    ]
  },
  {
    title: '招商租赁', icon: 'Promotion', children: [
      {
        title: '招商', icon: 'Promotion', children: [
          { title: '线索管理', path: '/crm/lead' },
          { title: '意向客户', path: '/crm/customer' },
          { title: '销售计划', path: '/crm/plan' },
          { title: '渠道管理', path: '/crm/channel' },
          { title: '佣金管理', path: '/crm/commission' },
          { title: '招商分析', path: '/crm/analysis' }
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
      {
        title: '物业', icon: 'Tools', children: [
          { title: '工单汇总', path: '/property/workorder-summary' },
          { title: '物业报修', path: '/property/workorder' },
          { title: '责任单位', path: '/property/responsible-unit' },
          { title: '会议室预约', path: '/property/meeting' },
          { title: '设备巡检', path: '/property/inspection' },
          { title: '安防巡更', path: '/property/patrol' },
          { title: '保洁检查', path: '/property/check-clean' },
          { title: '绿化检查', path: '/property/check-green' },
          { title: '品质核查', path: '/property/check-quality' },
          { title: '投诉建议', path: '/property/complaint' },
          { title: '意见反馈', path: '/property/feedback' },
          { title: '投票问卷', path: '/property/survey' },
          { title: '物业活动', path: '/property/activity' },
          { title: '资源目录', path: '/rsv/resource' },
          { title: '资源预订', path: '/rsv/booking' }
        ]
      },
      {
        title: '惠企', icon: 'Shop', children: [
          { title: '访客预约', path: '/service/visitor' },
          { title: '在线商城', path: '/service/mall' },
          { title: '物品放行', path: '/service/pass' },
          { title: '场地预约', path: '/service/site' },
          { title: '装修申请', path: '/service/decoration' },
          { title: '出入证管理', path: '/service/passcard' },
          { title: '园区论坛', path: '/service/forum' },
          { title: '申报服务', path: '/service/declare' },
          { title: '知产服务', path: '/service/ip' },
          { title: '政策服务', path: '/service/policy' },
          { title: '生态配置', path: '/service/ecosystem' }
        ]
      }
    ]
  },
  {
    title: '能源与物联', icon: 'Lightning', children: [
      {
        title: '能耗', icon: 'Lightning', children: [
          { title: '能耗统计', path: '/energy/stats' },
          { title: '智能表计', path: '/energy/meter' }
        ]
      },
      {
        title: '智慧物联', icon: 'VideoCamera', children: [
          { title: '智能硬件', path: '/iot/device' },
          { title: '预警事件', path: '/iot/alarm' },
          { title: '智能安防', path: '/iot/camera' },
          { title: '人脸识别门禁', path: '/iot/access' },
          { title: '智能门锁', path: '/iot/lock' },
          { title: '智慧停车场', path: '/iot/parking' },
          { title: '智能充电桩', path: '/iot/charging' },
          { title: '空气质量传感器', path: '/iot/sensor' },
          { title: '智能空开', path: '/iot/breaker' },
          { title: '智慧安消', path: '/iot/fire' },
          { title: '点位管理', path: '/iot/point' },
          { title: '通道管理', path: '/iot/channel' },
          { title: '厂商配置', path: '/iot/vendor' }
        ]
      },
      {
        title: '便捷通行', icon: 'Postcard', children: [
          { title: '门禁通行记录', path: '/acc/access' },
          { title: '访客登记', path: '/acc/visitor' },
          { title: '停车管理', path: '/acc/parking' }
        ]
      }
    ]
  },
  {
    title: '园区运营', icon: 'Calendar', children: [
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
      { title: '用户管理', path: '/system/user' },
      { title: '角色管理', path: '/system/role' },
      { title: '部门管理', path: '/system/dept' },
      { title: '菜单管理', path: '/system/menu' },
      { title: '岗位管理', path: '/system/post' },
      { title: '字典管理', path: '/system/dict' },
      { title: '运营资源', path: '/system/resource' },
      { title: '消息中心', path: '/system/message' }
    ]
  }
]
