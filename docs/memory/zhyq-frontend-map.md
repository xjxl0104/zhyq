---
name: zhyq-frontend-map
description: "zhyq 前端代码结构图谱 — 已 codegraph 索引(frontend/.codegraph),查前端代码先用 codegraph"
metadata:
  node_type: memory
  type: reference
---

zhyq 前端目录 `~/Documents/zhyq/frontend`,Vue 3.4 + Vite 5 + Element Plus 2.7 + Pinia + Vue Router 4 + ECharts 5 + Axios + Sass,包管理 pnpm。**2026-07-13 已建 codegraph 索引**(`.codegraph/` 在 frontend 根:99 files / 2,281 nodes / 5,517 edges,语言覆盖 79 vue + 19 js,component 节点 79 个)。本机 codegraph 未全局安装,统一用 `npx -y @colbymchenry/codegraph <query|explore|callers|impact|sync|status>` 在 frontend 目录跑;改完前端代码跑一次 sync。后端图谱见 [[zhyq-backend-map]]。

**结构**:
- `src/request.js` — axios 统一封装(token/统一错误/401 跳登录),所有 api 走它。
- `src/api/` — 14 个模块文件:app/building/contract/crm/dashboard/energy/finance/iot/oa/property/service/system/tenant/todo,与后端路由前缀一一对应。
- `src/layout/` — `Layout.vue` + `menu.js`(14 个一级导航定义)。**menu.js 与 router/index.js 是共享文件,主脑独占改**。
- `src/router/index.js` — 89 条路由,一组件多路由靠 `route.meta` 区分(如 iot/DeviceCategory 八分类、property/Feedback 投诉/意见、Check 三检)。
- 无独立 stores/ 目录,Pinia 轻量使用。

**views 页面分布**(76 业务页 + Login.vue + screen/BigScreen.vue = 79 vue):
- service 11(惠企九项:放行/场地/装修/出入证/申报/知产/政策/论坛/访客/商城)
- finance 10(账单/收款/收银台/收据/通知/流水/发票/报表/退房/设置)
- property 9(工单/会议室/巡更/三检/投诉/问卷/活动等)、oa 9(日程/公文/流程/考勤/招聘/文章/审批/任务/公告)
- system 8、crm 6、iot 6、building 3、contract 3(含 Archive 三tab)、data 3(剖面图 SectionView.vue/报表目录/数据中心)、tenant 3、energy 2、dashboard 1、app 1
- screen/BigScreen.vue — 深色指挥中心风监控大屏(路由 /screen,升级重点)

**页面范式**:查询栏 + 表格 + 增删改弹窗三段式(规范见 docs/PATTERN.md);Element Plus 默认主题,无定制设计体系(升级调研 D3 维度的主要差距所在)。
