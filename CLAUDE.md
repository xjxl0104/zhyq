# zhyq · 智慧园区管理系统 — Claude 项目区

> 接手顺序:本文件 → `PLAYBOOK.md`(方法论) → `docs/PATTERN.md`(代码硬规范) → `docs/memory/*`。
> 写代码前**必读** `docs/PATTERN.md`;改金额/状态逻辑前留意「踩坑清单」。

## 一句话定位
自研企业级智慧园区管理后台,**从零手写、不套低代码底座**,广度 > 深度。73 后端接口模块 / 77 前端页面 / 79 表 / 16 Flyway 迁移。

## 技术栈(实测自 pom.xml / package.json)
- **后端**:Java 17 · Spring Boot 3.2.5 · MyBatis-Plus · MySQL 8 · Flyway · Knife4j(OpenAPI3)· Hutool · Lombok。构建 Maven,产物 `zhyq-park.jar`(有 `<finalName>`,不带版本号)。
- **前端**:Vue 3.4 · Vite 5 · Element Plus 2.7 · Pinia · Vue Router 4 · ECharts 5 · Axios · Sass。包管理 **pnpm**。
- **部署**:全 Docker(`docker-compose.full.yml` = MySQL + 后端 + Nginx 前端,出口 80)。

## 目录
- `backend/` — Spring Boot,包结构 `com.<company>.<app>.<模块>.{entity,mapper,controller[,service]}`,controller 直接注入 mapper、无 service 层(仅合同/财务/物业复杂事务才加 service)。已建 codegraph 索引 `backend/.codegraph/`。
- `frontend/src/` — `request.js` 统一 token/错误/401 跳登录;api 按模块分文件;页面 = 查询栏+表格+增删改弹窗三段式。
- `docs/` — PATTERN(代码规范)/ DEPLOY / DEMO_ONLINE / memory。

## 主业务链路(灵魂在联动,不在 CRUD)
项目/楼宇/房源 → 招商CRM → 租户 → **合同(审批→自动生成周期账单 + 锁房源)** → **财务(账单/收款幂等/滞纳金/收据)** → 物业(工单/会议室/巡更/三检/投诉)→ 能耗 → 智慧物联 → 数据中心(驾驶舱/剖面图/监控大屏)。惠企九项 + 办公OA 为广度补齐。

## 硬约束(务必遵守)
- **状态流转一律条件更新**:`update ... where id=? and status in (合法前态)`,`updated==0` 抛业务异常。禁止「先查后改」。
- **收款等要幂等**:业务幂等键(payNo)唯一 + SQL 原子累加(`paid = paid + ?`)。
- **金额用 BigDecimal**,精度敏感;**本项目无自动化测试**,改财务/合同金额状态逻辑格外小心。
- **新增 entity/controller/mapper 先查重类名**(Spring bean 按类简名注册,跨包重名 → `ConflictingBeanDefinition` 启动失败):
  `find backend -name '*.java' | sed 's|.*/||' | sort | uniq -d`
- **共享文件主脑独占改**:`router/index.js`、`menu.js`、公共基座类;并行子代理只碰自己的新文件。
- `mvn` 必须在 `backend/` 目录跑;公网访问前必须有登录鉴权,并把域名加进 CORS `allowedOriginPatterns`。

## 分支与上线纪律(2026-09-01 修订,人与 AI 一律遵守)

> 上线机制(已上服务器核实):cron 每 5 分钟跑 `/opt/zhyq/deploy.sh`,**固定轮询 `main`**——合并进 main = 自动构建上线(全程约 10 分钟;失败自动回滚旧镜像并 Bark 推送手机)。`ver*` 分支是历史遗留开发分支,推了不触发部署,不再作为上线通道。

1. **日常开发一律先进 `test`**:所有提交推 `test`(feature 分支从 `test` 拉出,PR 也指向 `test`),不直推 `main`。
2. **test → main 由 AI 自查后直接合并,默认不找用户确认**:自查 = 后端 `mvn -B test-compile`(**不是 `compile`**:服务器 Docker 构建跑的是 `package -DskipTests`,会连测试源码一起编译,只跑 `compile` 会漏掉测试编译错误——2026-09-04 main@38aec46 就因此构建失败)、前端 `pnpm build`、过一遍 diff(纯文档改动可免构建)。通过即合并(= 上线),合并后告知用户"已合入,约 10 分钟生效"即可;用户说"推上去/上线"时不要再追问确认。
3. **只有出现下列情况才停下提醒用户,并附具体操作建议**:
   - 自查失败(编译/构建报错、diff 里发现疑似 bug);
   - 含**修改/删除已有数据**的 Flyway 迁移——服务器无自动备份,先给备份命令再合;
   - 新增 Flyway 迁移先核对生产库 `flyway_schema_history` 最大版本号(2026-09-01 V42 撞号翻过车:生产库早有 ver6.7 的 `V42__budget_management`,新迁移必须从 V43 之后接着编);
   - 财务/合同的金额或状态流转逻辑、鉴权/CORS/对外暴露面的变更;
   - 回滚、强推、删远程分支等破坏性操作。
4. 合并只解决冲突、不夹带改动;`test` 长期保留,落后时从 `main` 回同步。
5. **出问题怎么查**:合并后 ~10 分钟未生效,或手机收到 Bark「已自动回滚」→ `ssh aole` 看 `/opt/zhyq/deploy.log`(GitHub 出网抽风会静默重试;Flyway/启动失败日志里有容器输出)。修好后推新提交即自动重试;代码已最新只是镜像旧,在服务器跑 `/opt/zhyq/deploy.sh --force`。

## 当前项目目标
对整个 zhyq 做升级:调研国内外同类智慧园区产品,只看**功能分类 / 流程编排 / 前端设计**(不研究别人后端),产出按优先级排序的可落地升级清单。详见 `docs/upgrade/调研方案.md`(方案v2)与 `docs/memory/zhyq-upgrade-research.md`(进度)。

## ECC 技能绑定(已启用,来自 ecc@ecc v2.0.0)

> 规则:开工前根据当前子任务技术栈,**主动声明要用哪个 ECC 技能**再动手。多数技能按栈自动触发。无 ECC 时退回 `PLAYBOOK.md` 第 5 节与 `docs/PATTERN.md`。

### 后端(Spring Boot 3.2 + Java 17 + MyBatis-Plus + MySQL8 + Flyway)
- 业务/架构:`springboot-patterns` · `java-coding-standards` · `jpa-patterns`
- 安全:`springboot-security`
- 测试(项目当前无测试,是最大空白):`springboot-tdd` · `springboot-verification`
- 数据库:`mysql-patterns` · `database-migrations`
- Review Agents:`java-reviewer` · `java-build-resolver` · `database-reviewer`

### 前端(Vue3 + Vite + Element Plus + Pinia + ECharts)— 升级重点
- 模式:`vue-patterns` · `frontend-patterns` · `ui-to-vue`
- 设计/体验/可访问性:`frontend-design-direction` · `frontend-a11y`
- Review Agent:`vue-reviewer`

### 通用 + 安全 + 部署
- API 规范(统一 Result/分页/错误/REST):`api-design`
- 安全:`security-review` · `security-scan` · Review Agent `security-reviewer`
- 部署:`docker-patterns` · `deployment-patterns`

### 调研 / 省钱(匹配当前并行升级调研任务)
- `search-first`(先调研后编码)· `cost-aware-llm-pipeline`(混合模型+子代理并行)· `strategic-compact`(长任务上下文压缩)

### 场景 → 技能速查
| 场景 | 用哪个 |
|---|---|
| 写后端 CRUD/业务 | `springboot-patterns` + `java-coding-standards` |
| 合同账单联动/财务金额状态 | `springboot-patterns` + `jpa-patterns`,改前挂 `database-reviewer` 审查 |
| 加鉴权/收紧 CORS | `springboot-security` + `security-review` |
| 建/改 Flyway 迁移 | `database-migrations` + `mysql-patterns` |
| 写/补后端测试 | `springboot-tdd` → `springboot-verification` |
| 写 Vue 页面 | `vue-patterns` + `frontend-patterns` |
| 前端设计升级/大屏 | `frontend-design-direction`(+ `frontend-a11y`) |
| 设计新接口 | `api-design` |
| 编译报错排查 | agent `java-build-resolver` |
| 提交前审查 | 按栈挂 `java-reviewer` / `vue-reviewer` / `security-reviewer` |
| 并行做多个 CRUD | `cost-aware-llm-pipeline`(子代理并行,共享文件主脑独占改) |
| 升级调研 | `search-first` + `strategic-compact` |
