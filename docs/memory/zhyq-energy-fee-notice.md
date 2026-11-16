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

## 第二版(2026-11-16,feat/energy-fee-calendar,V106)

负责人要求:年月表入口、本月读数自动衔接下月、保留两位小数且全部对得上、每月上传三张供应商账单、缴费汇总可删除、区域/用户可随时增减。

- **年月表**:电费/水费/垃圾处理费/缴费汇总四页先显示 `FeeMonthPicker`(左右各一年、当前年居中、箭头翻年),每月一个按钮带当月合计(总用电+电费 / 总用水+水费 / 垃圾总额+已分摊 / 通知单数+应收),点月份进明细,「返回年月表」回来。数据来自 `GET /energy/fee-period/overview?year=`(`FeeNoticeService.overview`):已确认月份读冻结快照,草稿月份实时算。
- **两位小数对账**:读数用量、公摊度数、各项费用全部 2 位小数。公摊度数与垃圾费用最大余数法(`FeeNoticeCalculator.apportion`)分,各户之和恰好等于公摊池/总额,所以电费页、垃圾页、通知单、汇总、年月表逐分一致。垃圾费分摊抽成 `splitGarbage`,通知单与垃圾页共用。
- **本月→下月**:新读数的上期读数默认取上个月本期读数;改本月读数时 `cascadeNext` 把下个月同表读数的上期读数/用量/费用一起改(下个月已确认出账则拦下,要先撤销)。
- **确认出账冻结**:`eng_fee_snapshot` 存电/水/垃圾三张工作表 JSON,已确认月份只读快照,之后增删区域/用户不改历史;撤销确认删快照。`eng_fee_period.inputs_changed_time` 晚于 `generated_time` 即「通知单已过期」,过期不能确认出账。
- **单据留存**:南网/自来水公司/垃圾清理供应商账单走统一附件表,biz_type=`energy_fee_electric|water|garbage`,biz_id=账期id;权限登记在 `MktDocumentAccessService.RESOURCES`(前缀 `energy:feebill`),V106 建 `energy:feebill:query/edit` 并授予已有这四个页面的角色/用户。不登记会 403。
- **删除通知单**:`POST /{id}/notices/delete`(单张/多选)、`/notices/delete-all`;已确认的连账单一起删,账单已收款则拦下。删掉的户重新生成会按读数再出现,永久去掉要删对应区域/用户。
- **增减区域/用户**:`POST /meter`(新增/改,表计编号自动生成,计租面积沿用同区域)、`POST /meter/delete`;表单组件 `FeeMeterDialog`。表计改动会让所有草稿月份的通知单标记过期。
- **验证方式**:本机 `D:\本地运行环境配置\tools\mysql-8.0.44` 可起临时库(路径含中文须 `subst` 成盘符,否则插件目录找不到),Flyway 能一路迁移到 V106;脚本化接口场景 54 项全过。

## 第三版(2026-11-16,feat/energy-public-pool,V107)——对齐负责人「修改后最终」10月表

- **公摊池口径改为线下表口径**:公摊度数 = 总度数 − 参与公摊的租户用量 − 物业公司表用量;室外充电桩、未租单元(share_public=0)既不参与也**不从池里扣**,各自另收。各户按实际用量占比分摊(`FeeNoticeCalculator.allocate`)。用 10 月数据复算:云帕电费+公摊 34655.90、云山2层 42573.66,与线下表逐分一致。
- **手工公摊度数**:线下表的公摊度数是手填的(R2 租户合计手填 142537.2,与逐表相加差 12.6 度),V107 在账期加 `electric_public_manual / water_public_manual`,电费/水费页「公摊度数」留空自动算、填了就用手填值;填了会在总度数来源后标注。
- **比例显示**:电费/水费页「公摊比例」列提前到本期用量之后(百分比,悬停看 6 位小数);垃圾页「计租/总计租面积」百分比,同一租户相邻区域(如 2层A~C区)面积只填在第一个区域,后面显示「并入上一区域」;通知单备注写明「按实际用量占比 x%」与「计租面积 A㎡/总计租面积 T㎡=x%」;导出 Excel 比例列用百分比格式。
- **过期判断精确到毫秒**:`generated_time / inputs_changed_time` 改 DATETIME(3),避免同一秒内先后操作漏判。
- **10月数据录入要点**(待登录线上后执行,数据源「【修改后最终】…10月份应收收费明细表.xlsx」):电 南网 154960 + 光伏 21340、单价 1.03、公摊度数手填 33762.8;水 总表 1126、单价 5.51(公摊自动 451);垃圾 生活 2402.78 / 工业 1854(通知单实际用的数,10月栏是 3203.71/6180,需向负责人确认);计租面积只填在每个楼层块第一个区域(1A 3300、1B 3600、1C 3500、2A 13500、3A 13500、4A 13500、5A 4700、5B 9000、6A 4700、6B 8800、7A 13500,合计 91600);云帕/云山(1B)水表通知单计费 19/32,读数差是 12.5/38.5,按通知单录就把上期读数调成 12.5/38.5;充电桩通知单有 1% 税金 3.75 填「其他费用」;充电桩与八楼A/B(桐宇,未租)关闭参与公摊;起始读数=9月读数。
