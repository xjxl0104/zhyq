---
name: zhyq-backend-map
description: "zhyq 智慧园区后端代码结构与 API 图谱 — 已用 codegraph 索引,查代码先用 codegraph_explore"
metadata: 
  node_type: memory
  type: reference
  originSessionId: 38b8b894-59ea-449d-acaa-9f405deb1d48
---

zhyq 后端目录 `~/Documents/zhyq/backend`,Spring Boot 3.2 + MyBatis-Plus。**已用 codegraph 索引**(`.codegraph/` 在 backend 根,2026-07-13 sync 后:253 files / 5,813 nodes / 9,107 edges,含 435 route 节点)。本机 codegraph **未全局安装**(npm -g 被系统权限拒),统一用 `npx -y @colbymchenry/codegraph <explore|query|callers|impact|sync|status>` 在 backend 目录跑;改完代码跑一次 sync 增量更新。详见 [[zhyq-project]],前端图谱见 [[zhyq-frontend-map]]。

**分层**:`com.zhyq.park.<模块>.{entity,mapper,controller[,service]}`,controller 直接注入 mapper(无 service 层),仅 contract/finance/property 有 service 承载复杂事务。`common/` 放 Result/PageResult/BaseEntity/BizException/全局异常/MyBatis-Plus 配置(自动填充审计字段+逻辑删除+乐观锁)。

**模块与路由前缀**(33 个 controller):
- system(17文件):`/system/{user,role,dept,dict,post}`
- building(12):`/building/{project,building,floor,room}` — room 有 `/stats`
- crm(9):`/crm/{lead,follow,channel}` — lead 有 `/stats`、`/{id}/convert`
- tenant(6):`/tenant/{info,staff}` — info 有 `/stats`、`/{id}/archive`
- contract(12):`/contract` + service 三大事务 `submit/approve/terminate`;**approve 生成周期账单计划+锁房源**,terminate 释放房源+写 contract_version
- finance(14):`/finance/{bill,payment,flow,invoice,report}`;payment 收款幂等(pay_no 唯一),bill 有 `/stats`、`/overdue`、`/calcLateFee`(滞纳金万5/天)
- property(12):`/property/workorder`(6步流转 service)、`/property/meeting/{room,booking}`(booking 时段冲突校验)
- todo(3):`/todo` 统一待办 + `/stats`
- energy(6):`/energy/{meter,reading}`;iot(6):`/iot/{device,alarm}`;oa(6):`/oa/{task,notice}`;hui(6):`/service/{visitor,product}`(包名 hui 避开 spring service 歧义,路由仍 /service)
- dashboard(1):`/dashboard/{workbench,overview,room-status,revenue-trend,workorder-category}` — 用 JdbcTemplate 跨模块只读聚合,供首页/数据中心/大屏

**注意**:整个后端**无测试**(codegraph blast-radius 会提示 no covering tests);改 contract/finance 的金额与状态逻辑要格外小心,没有测试兜底。

**2026-07-03 审查修复一轮**:合同状态流转改「条件 UPDATE 抢状态」防并发;账单尾期按天折算+免租按月重叠折算;收款加状态/超额守卫+payNo 幂等键+SQL 原子累加;calcLateFee 按欠款只算应收;JacksonConfig 统一 LocalDateTime "yyyy-MM-dd HH:mm:ss";V9 把 (code,deleted) 唯一键降普通索引;CORS 收紧 localhost;异常不回显内部细节。生产仍差:鉴权、会议室预约竞态、物业费/递增率账单、Bean Validation。

**2026-07-03 查缺补漏一轮**(V10/V11):新增 审批中心 `/oa/approval`(合同 submit 自动写审批单,审批联动合同状态)、资产、巡检(可生成工单)、应用中心(收藏)、菜单管理(V11 种子49条)、站内信;前端补 收支流水/发票/楼宇管理等 9 页。

**2026-07-03 全量补齐(混合模型:Fable5 主脑+opus/sonnet 子代理,分4批)**:
- 批1✅(V12):物业八小件(投诉/意见=pm_feedback 单表 ftype 区分、问卷 pm_survey、活动 pm_activity、巡更 pm_patrol 异常可转工单、三检 pm_check 单表 ctype 区分,前端 Feedback.vue/Check.vue 一组件多路由靠 route.meta)+ 财务补全(收银台 Cashier.vue 复用 payment 接口逐单收款、收据 fin_receipt **收款自动生成收据**、收款通知 fin_pay_notice 一键 generate、退房报表聚合 status=9 合同、biz_setting 通用配置表 /finance/setting?module= 各模块设置共用)。全部冒烟通过。
- 批2✅(V13):惠企九项——物品放行 srv_pass(核验/失效检查)、场地预约 srv_site_booking(**时段冲突校验**实测拒绝)、装修申请 srv_decoration(审批/驳回/完工)、出入证 srv_pass_card(挂失/注销)、申报 srv_declare、知产 srv_ip(实体名 IpAsset)、政策库 srv_policy(**/match 按租客行业匹配**)、论坛 srv_forum_post/reply(**敏感词拦截+审核流+回复原子计数**实测通过)、生态配置复用 biz_setting(module=ecosystem)。后端包统一 com.zhyq.park.hui。
- 批3✅(V14):办公六件(日程 oa_schedule 含/week、招聘 oa_recruit、文章 oa_article、考勤 oa_attendance 同步展示+stats、公文 oa_document 拟稿→核稿→签发→归档、流程定义 oa_flow steps JSON 可视化)+ 招商深化(意向客户 crm_customer **from-lead 事务转化**、销售计划 crm_plan 完成率、佣金 crm_commission **generate=首年租金×渠道费率+防重**、招商分析 /crm/analysis funnel/trend/source/summary 用 JdbcTemplate)。**坑:Spring bean 按类简名注册,跨包重名类会 ConflictingBeanDefinition——已重命名 oa.Flow→OaFlow、crm.ContractRef→CrmContractRef、iot.Channel→IotChannel;新增实体前先 `find . -name '*.java' | sed 's|.*/||' | sort | uniq -d` 查重**。
- 批4✅(V15):能耗统计 /energy/stats-api(overview/trend/meter-rank,JdbcTemplate)+ Stats.vue 图表页;**剖面图 SectionView.vue**(楼层×房间状态色格子,复用 building 接口,演示亮点页)+ 报表目录 ReportIndex.vue;物联深化(点位 iot_point/通道 iot_channel/厂商 iot_vendor 连测模拟 + **DeviceCategory.vue 一组件八分类路由** camera/access/lock/parking/charging/sensor/breaker/fire);系统(运营资源 sys_resource 图片预览+上下架、消息中心 sys_msg_template/record **模板{var}变量替换模拟发送**);合同归档 POST /contract/{id}/archive(8,9→10)+ Archive.vue 三tab + 合同设置页 + ContractList 六类型 tabs。

**最终规模(2026-07-03 全量完成)**:73 controller、76 页面、79 表、15 个 Flyway 迁移。规格书菜单树基本全覆盖;未做:登录鉴权、视频流(用户明确不要)、真实第三方对接(支付/签章/短信/厂商平台均为配置占位或模拟)。
