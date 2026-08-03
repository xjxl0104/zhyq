# 智慧园区管理系统 (zhyq-park)

全新自研的智慧园区管理系统,Java 17 + Spring Boot 3 + Vue 3。覆盖园区运营主链路:
**项目/楼宇/房源 → 招商 → 租客 → 合同 → 账单 → 物业 → 设备 → 数据大屏**。

当前版本为 **ver4.3**，已包含 JWT 登录鉴权、RBAC 用户/角色授权、权威应收明细导入，以及自动售货机安全入口与受控数据导入。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.2、MyBatis-Plus 3.5、MySQL 8、Flyway、Apache POI、Knife4j、Hutool |
| 前端 | Vue 3、Vite 5、Element Plus、Pinia、Vue Router、ECharts 5、Vitest |
| 基础设施 | Docker Compose(MySQL) |

所有核心表统一带 `tenant_id / create_by / create_time / update_by / update_time / version(乐观锁) / deleted(逻辑删除)`,由 `BaseEntity` + `MyMetaObjectHandler` 自动填充。

## 目录结构

```
zhyq/
├── docker-compose.yml          # MySQL 8 (端口 3316)
├── backend/                    # Spring Boot 后端
│   └── src/main/
│       ├── java/com/zhyq/park/
│       │   ├── common/         # 公共层:Result/PageResult/BaseEntity/异常/配置
│       │   ├── system/         # 系统管理:用户/角色/部门/岗位/字典
│       │   ├── building/       # 建筑:项目/楼宇/楼层/房间/租控
│       │   ├── crm/            # 招商:线索/跟进/渠道
│       │   ├── tenant/         # 租客:企业/个人/员工
│       │   ├── contract/       # 合同:全生命周期 + 账单计划生成
│       │   ├── finance/        # 财务:账单/收款/流水/发票/报表
│       │   ├── importing/      # 受控导入共享批次、审计行和安全单元格读取
│       │   ├── receivable/     # 权威应收明细、规则、账户与账单生成
│       │   ├── vending/        # 自动售货机入口、标准模板与本地经营数据
│       │   ├── property/       # 物业:工单/会议室
│       │   ├── todo/           # 统一待办
│       │   ├── energy/         # 能耗:表计/抄表
│       │   ├── iot/            # 智慧物联:设备/告警
│       │   ├── oa/             # 办公:任务/公告
│       │   ├── hui/            # 惠企:访客/商城
│       │   └── dashboard/      # 驾驶舱/大屏 聚合接口
│       └── resources/db/migration/  # Flyway V1~V33(建表 + 种子数据 + 升级)
├── frontend/                   # Vue 3 前端
│   └── src/
│       ├── layout/             # 14 个一级导航布局
│       ├── views/              # 各模块页面 + dashboard + screen(大屏)
│       ├── api/                # 按模块拆分的接口封装
│       └── router/             # 路由
└── docs/PATTERN.md             # 代码模式规范
```

## 快速启动

### 1. 启动数据库
```bash
cd zhyq
docker compose up -d          # MySQL 8 起在 localhost:3316, 库名 zhyq_park
```

### 2. 启动后端
```bash
cd backend
mvn spring-boot:run           # 首次启动 Flyway 自动建表 + 灌入种子数据
```
- 后端地址:http://localhost:8090/api
- 接口文档(Knife4j):http://localhost:8090/api/doc.html

### 3. 启动前端
```bash
cd frontend
pnpm install                  # 或 npm install
pnpm dev                      # 起在 http://localhost:5273
```

### 4. 看监控大屏
浏览器打开 http://localhost:5273/screen ,或在后台右上角点「监控大屏」按钮(新标签打开)。大屏为**本地预览**形态,深色指挥中心风格,数据来自真实后端聚合接口,每秒刷新时间。

## 已实现的核心业务能力

- **建筑/租控**:项目-楼宇树、房源状态机(可租/在租/维修…)、出租率/均价/面积统计、租控图着色。
- **合同 → 账单闭环**(核心):合同审批通过后**自动锁定房源 + 按付款周期生成周期账单计划**(支持免租期、保证金);退租时释放房源并留痕合同版本。
- **财务**:账单/逾期/收款(**支付幂等**靠流水号唯一约束)、滞纳金按日计算(万分之五/天)、收缴率/账龄/收入结构报表。
- **应收明细登记表**:以园区 Excel 为权威资料，预览、主数据绑定、AES-GCM 账户加密、确认入库，并按租金/物业/保证金规则分账生成账单。
- **自动售货机**:应用中心内部工作台 + 厂商 HTTPS 入口；支持机器、销售、补货、故障、对账五类标准模板的预览、排错、确认、去重与整批撤销，不自动登录或抓取厂商页面。
- **招商**:线索公海/认领/跟进/转化,统计卡 + 转化率。
- **物业**:报修工单全流程(派单→接单→到场→完成→验收→关闭)+ SLA + 流转时间线;会议室预约含**时间冲突校验**。
- **统一待办**:跨合同/账单/工单/线索/审批聚合。
- **数据中心 + 大屏**:经营/财务/设备/房源/工单多维聚合可视化，并分开展示租金物业与售货机经营收入。

ver4.3 的字段口径、升级步骤与验收记录见 [`docs/VER4.3.md`](docs/VER4.3.md)，生产部署注意事项见 [`docs/DEPLOY.md`](docs/DEPLOY.md)。

## 种子数据

Flyway `V8__seed.sql` 已灌入 2 个项目、3 栋楼、10 个房间(多状态)、4 个租客、6 条线索、3 份合同、6 张账单(含逾期/已结清)、4 个工单、7 台设备、告警/任务/公告/访客/商品等,开箱即有可视化数据。

## 重置数据库

```bash
docker compose down -v && docker compose up -d   # 清空重建, 后端重启后 Flyway 重新灌数据
```

## 后端代码索引 (CodeGraph)

后端已用 [CodeGraph](https://www.npmjs.com/package/@colbymchenry/codegraph) 建立代码知识图谱(索引目录 `backend/.codegraph/`),便于快速定位符号、查看调用链和影响面。

```bash
cd backend
codegraph explore "ContractService approve 账单计划"   # 探索某块逻辑:相关源码 + 调用路径
codegraph query "PaymentService"                        # 按符号名搜索
codegraph callers approve                               # 谁调用了 approve
codegraph impact Contract                               # 改动 Contract 的影响面

codegraph sync                                          # ★ 改完代码后增量更新索引
codegraph status                                        # 查看索引统计
codegraph index                                         # 从零重建(结构大改后)
```

> **约定**:每次改完后端代码后跑一次 `codegraph sync`,保持索引与源码同步。首次安装:`npm i -g @colbymchenry/codegraph`。

## 安全说明

后台使用 Spring Security + JWT + RBAC。生产环境必须配置强随机 `JWT_SECRET` 和 32 字节 Base64 字段加密密钥，收紧 CORS，并在升级前备份数据库与 `uploads`。自动售货机入口不会接收或保存厂商账号、密码、Cookie，也不会把智慧园区 token 拼入外部 URL。
