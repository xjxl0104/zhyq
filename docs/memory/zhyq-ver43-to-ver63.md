---
name: zhyq-ver43-to-ver63
description: "zhyq ver4.3→ver6.3 交付脉络:补全/项目切换/自动建档/大屏/工单门禁/便利贴/BI + 关键坑与部署"
metadata:
  node_type: memory
  type: project
---

**时间**:2026-08-03 ~ 08-10。仓库用**分支**标版本(origin/verX.Y),`.env` 始终 gitignore。

## 版本与分支(GitHub origin)
- **ver4.3**(`1da8607`):三大块(RBAC权限拆分 / 物业应收导入 / 售货机)已完整,本会话补全**「查看完整收款账户」前端**(能力位 accountView + 详情抽屉揭示控件调 revealAccount)。后端175测试、前端9测试全绿。
- **ver5.0**(`a5c5813`):多项目切换与数据隔离(仅前端,轻量方案),不含租户建档。
- **ver5.1**(`f2a7dd2`):= ver5.0 + 应收导入**预览确认式自动建档**(见下)。
- **ver6.0**(`e3e9a50`):平台使用度统计与建议提交系统 + logo改图标 + 我的建议附件上传。
- **ver6.3**(`0e8974d`,当前最新):= ver6.0 之后全部 = 大屏重构 + ver6.2四项 + 便利贴墙 + BI取数/SQL修复 + BI图表。
  - 注:ver6.2「四项」(工单可改/派单下拉/类型加其他/门禁访客加审批人,`fe0eb80`)当时未单独建分支,累进在 ver6.1 线上;GitHub 无独立 ver6.2 分支。

## 多项目切换(ver5.0)
纯前端:`stores/project.js`(currentProjectId 持久化 localStorage `zhyq_project_id`)+ `utils/request.js` 拦截器按排除清单(`/auth`、`/building/project`)自动注入 projectId + 顶栏 `ProjectSwitcher.vue` + Layout 切换时清 TagsView 拨断 keep-alive 重挂。后端零改。验收清单见 docs/superpowers/plans。

## 应收自动建档(ver5.1)
预览确认式:导入预览列出「表格有、系统没有」的租户/空间待建清单→用户确认→批量建租户(按清洗后name去重)+合成空间(统一DIPARK项目+主楼+每楼层串建floor/room)+同步空间树→回填绑定→确认入库。合同可留空(放宽 `ReceivableBindingValidator` 与 `bindRow` 两处校验),无合同仅登记不生成账单(`ReceivablePlanService.generate` 入口拦截)。租户名清洗只剥**半角**括号后缀、保留全角（）。真实工作簿9行端到端验收通过。

## 大屏重构(ver6.1)
`views/screen/BigScreen.vue` 视觉+布局重做为「深空指挥中心」:令牌化配色、切角面板、KPI单位分离、ECharts美化。**数据/轮询逻辑未动**。

## BI 使用度统计(ver6.3 修复+图表)
`stats/BiController.java`(raw JDBC)。读表:`user_period_metrics`(周1/月2,北极星/趋势/流程)、`user_score`(月度五维dim_*+total_score)、`access_log`(module使用率)、`suggestion`(反馈看板)。
- 前端 `bi/BiAdmin.vue`/`BiProduct.vue`:部门雷达+得分排行+趋势;模块双轴+闭环仪表盘+反馈环形。
- 演示数据只塞本地库(未进仓库),clone 后需重塞才有数据。

## 关键坑(踩过,勿重犯)
1. **字段加密密钥**:`zhyq.encryption.key`(env `ZHYQ_FIELD_ENCRYPTION_KEY`/fallback `APP_ENCRYPTION_KEY`)必须是 **Base64 的 32 字节**(`openssl rand -base64 32`),不是 hex;否则应收保存账号报「未配置」。有数据后**永不可改**。
2. **sys_space.code varchar(64)** 会链式串联各级 code(P-B-F-R),自动建空间的 code 必须短(base36),否则截断报「Data too long」。
3. **request.js 拦截器**对 `code===0`(Result.ok)**已拆包**返回 data。页面取数直接 `res.records`,**别写 `res.data.records`**(便利贴、BI 都因此踩空→列表空白)。
4. **BiController.flowAnalysis** 原用不存在的 `flow_started/flow_completed` 列→已改 `total_core_actions * flow_close_rate` 折算。
5. Docker 本地:mysql 容器是手动 run 的(无 compose label、网络 zhyq_default 无别名,只能用容器名 `zhyq-mysql` 连);backend 手动 run 时须 `DB_PASSWORD=$DB_ROOT_PASSWORD`(compose 才做此映射)。重建单服务用 `--no-deps` 避免 mysql 重名冲突。

## 生产/部署
- ECS `/opt/zhyq`,`docker-compose.full.yml + docker-compose.prod.yml`,域名 zhyq.aoleplat.com。后端冷启~65s,期间 nginx 反代 502 属正常。
- 国内 build 适配已进 **production 分支**:Dockerfile 阿里云maven镜像(`backend/settings.xml`)+ npmmirror;`.env` 不进 git。
- **自动部署框架已备**:`.github/workflows/deploy.yml`(推 production → self-hosted runner 自动 pull+build+重启),runner **尚未在服务器安装**。清单见 docs/superpowers/plans/2026-08-03-production-autodeploy.md。
- 生产遗留风险:曾发现 `test/123456` 超管弱密码账号,须删除或改强密码。

相关:[[zhyq-project]] [[zhyq-build-env]] [[zhyq-attachment-rollout]]
