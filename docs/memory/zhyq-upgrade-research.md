---
name: zhyq-upgrade-research
description: "zhyq 升级调研进度 — 方案v2(25家对标)执行中,卡片在 docs/upgrade/cards/,报告产出前不改代码"
metadata:
  node_type: memory
  type: project
---

# zhyq 升级调研 · 进度（2026-07-13）

**范围（用户已确认）**:25 家对标 = 国内商业8(华为/微瓴/阿里/明源云/海康/绿洲/特斯联/蘑菇物联) + 国际商业7(OpenBlue/EcoStruxure/Siemens Building X/Honeywell Forge/TRIRIGA·Planon/Yardi/OfficeRnD·Nexudus) + 开源7(yudao/JeecgBoot/Odoo/openMAINT·CMDBuild/ThingsBoard/Snipe-IT/GitHub精选) + 前端标杆3(AntD Pro 趋势/soybean·pure-admin/GoView·DataV)。四维:D1 功能分类 / D2 流程编排 / D3 前端体验★ / D4 架构与功能设计。**AI 暂缓**,只在升级清单预留 agent 扩展点;不研究别人后端数据处理实现;先出报告审阅后再实施,**报告产出前不改任何代码**。

**进度**:
- ✅ 方案 v2:`docs/upgrade/调研方案.md`
- ✅ 25/25 对标卡片:`docs/upgrade/cards/01~25-*.md`
- ✅ 四章差距分析:`docs/upgrade/drafts/D1功能广度|D2流程闭环|D3前端专题|D4架构开源.md`
- ✅ **最终调研报告:`docs/upgrade/调研报告.md`**——全局 P0/P1/P2 合并为 24 条目 × 四个实施批次:①架构与前端地基(事件/适配器/空间树/token层/标签页/AI接口位/鉴权) ②流程闭环(规则中心/轻量审批链/SLA/催缴/预警/告警模型) ③前端范式(useCrudPage表格封装/工作台/导航9域收敛/大屏标准化/详情页/按钮权限) ④功能广度(资产模块/便捷通行模块/租户小程序/资源预订/商机管道);§8 含预算预估(Opus4.6编码+Fable5审查:全量≈$650–900牌价,仅①+②≈$350–450)
- ⏭ **下一步:用户拍板实施范围(全量 or 先①+②)→ 按批次派 Opus 子代理编码、Fable 每批审查;批次②改财务/合同逻辑前先审设计(无测试兜底)**

**How to apply:** 接手实施时:读 `docs/upgrade/调研报告.md` §3 批次表(含依赖关系)开工;遵循 CLAUDE.md 硬约束与 docs/PATTERN.md;共享文件(router/index.js、menu.js、公共基座)主脑独占改;改完后端跑 `npx -y @colbymchenry/codegraph sync`(backend 与 frontend 各自目录)。
