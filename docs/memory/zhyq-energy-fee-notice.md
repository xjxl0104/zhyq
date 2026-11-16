---
name: zhyq-energy-fee-notice
description: 2026-11-15 能耗模块改版:下线水电公摊/水电结算,改为 电费/水费/垃圾处理费/缴费汇总 + 自动生成缴费通知单;口径来自负责人线下《租户对账版 应收收费明细表》
metadata:
  type: project
---

# 能耗 → 缴费通知单(2026-11-15,PR feat/energy-fee-notice)

**背景**:负责人每月线下用一个 Excel 做租户对账:`应收收费明细表`(费用项目 × 租户矩阵)+ `10月份电表`/`10月份水费`/`生活垃圾`/`工业垃圾` 四张汇总 + 每户一张 `缴费通知单` sheet。要求系统只填三类数就自动出通知单:① 电费总度数(南网用电 + 光伏用电,园区有光伏)② 每块租户表本期读数 ③ 垃圾处理费总额(生活/工业)。旧的「水电公摊」「水电结算」两页(按合同附件二不含税单价+税率算)被要求删除。

**口径(照线下表,代码在 `energy/service/FeeNoticeCalculator`,有单测 `FeeNoticeCalculatorTest` 用 10 月真实数字钉住)**:
- 电:公摊度数 = 南网 + 光伏 − **全部分表用量**(租户表 + 物业表,含不参与公摊的充电桩/未租);某户公摊 = 公摊度数 × 该户用量 / 参与公摊租户用量合计;电费 = 用量 × 单价(1.03),公摊电费 = 公摊度数 × 单价。
  - 与线下表的唯一差别:线下表公摊池只减「参与公摊的租户用量」(R3 = R1 + S3 − R2),充电桩等自身用量没扣,等于被租户再摊一遍;系统扣全部分表。负责人若要完全照表,改 `FeeNoticeCalculator.allocate` 里 `submeter` 的累加范围即可。
- 水:公摊度数 = 总表度数 − 分表用量合计;总表度数可手填(`water_total_usage`),为空则取发票总表 MAIN 当月抄表。
- 垃圾:生活/工业两笔总额按各**单元**计租面积 ÷ 参与分摊面积合计分摊;只有 `share_public=1` 的单元参与。单元 = `eng_meter.area`(如 1层A区),面积存 `eng_meter.lease_area`,同区域多块表取最大值。
- 通知单一户一张,户 = 租户 × 楼层(云山 2层A/B/C 三块表合成「2层A~C区」一张);租户经 房间→执行中合同 反查,查不到用表计「用户名」出单但确认出账时跳过。单据编号 `{前缀}-{yyyyMM}-{楼层}F-{序号}`。
- 室外充电桩 / 未租单元:表计角色仍是 TENANT,但 `share_public=0`:只付自身用量,不摊公摊和垃圾。线下表里充电桩那张还有一行 1% 税金,系统用通知单的「其他费用」手工填。

**数据**:V105 加 `eng_meter.share_public / lease_area`,新表 `eng_fee_period`(月账期:总度数/单价/垃圾总额/抬头 + 生成快照)、`eng_fee_notice`(通知单)、`eng_fee_notice_item`(明细行,`_DETAIL` 子行不计金额)。旧表 `eng_utility_bill / eng_allocation` 保留未删,只下线代码与菜单(`energy:allocation:calc` 权限点与 /energy/allocation、/energy/settlement 菜单置 visible=0,status=0)。四个新页面菜单挂在「智能表计」同目录,原有表计页权限的角色/用户自动获得。

**接口** `/energy/fee-period`(`FeePeriodController`):open(按月取/建,单价与抬头沿用上月)/ update / sheet?energyType / readings(批量按账期 upsert eng_reading,uk_eng_reading_active 兜底)/ meter-flags / garbage-sheet / generate(先清后插,已付/前期欠费/其他费用按户保留)/ notices / notice(PUT)/ confirm(每户一张 fin_bill,billing_key=`fee-notice:{id}`,金额=费用小计)/ revoke / export(POI,结构同线下工作簿)。路由放行在 `BusinessRouteAccess` 的 `energy/fee-period` 分支。

**前端**:`views/energy/UtilityFeeSheet.vue`(电费/水费一组件两路由,行内填读数)、`GarbageFee.vue`、`FeeSummary.vue`(矩阵 + 通知单列表 + 预览/打印/导出/确认出账)、`noticePrint.js`(通知单 HTML,新窗口打印,A4 横向)。月份记在 localStorage `zhyq_fee_period_month`。

**上线核对**:新增迁移编号 V105(仓库最大 V104);本机无法 ssh 生产库核对 `flyway_schema_history`,合并前请确认生产没有占用 V105。

**How to apply**:改分摊口径只动 `FeeNoticeCalculator`(纯函数,先改测试);页面上的数全部来自后端 `sheet`/`garbage-sheet`,前端不自算金额。
