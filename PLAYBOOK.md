# 智慧园区(及同类企业级)全栈项目 · AI 协作实战手册

> **这份文件是做什么的**:把「智慧园区管理系统(zhyq)」从零到上线的完整经历、技术决策、可复用模式和真实踩坑,沉淀成一份**自包含手册**。
>
> **怎么用**:换一台电脑 / 换一个 AI 终端时,把本文件直接发给 AI,它就能立刻理解「这类全栈大项目该怎么装环境、怎么选型、分几步做、要避开哪些坑」,不需要你从头解释。
>
> 适用范围:任何**企业级管理后台 / SaaS 后台 / 园区·物业·CRM·ERP 类**中大型全栈项目。技术栈可替换,方法论通用。

---

## 0. 给接手 AI 的开场指令(用户可直接照抄发给 AI)

```
我要做一个企业级全栈管理系统(类似智慧园区/物业/CRM)。请先读完这份 PLAYBOOK.md,
然后:
1. 按「第 2 节 环境与工具」检查/安装我缺的工具(skill、MCP、Docker 等);
2. 按「第 4 节 分阶段流程」跟我确认从哪一阶段开始;
3. 严格遵循「第 5 节 可复用模式」和「第 6 节 踩坑清单」写代码。
先不要写代码,先告诉我你打算怎么推进。
```

---

## 1. 这个项目做成了什么(成果基线)

一个功能对标商业产品的智慧园区管理系统,**从零手写、不套用任何低代码/脚手架框架**(明确不用 yudao 这类底座)。

- **规模**:73 个后端接口模块、76 个前端页面、79 张表、16 个数据库迁移版本。
- **覆盖**:系统管理、楼宇租控、招商 CRM、租户、合同(审批→自动生成周期账单+锁房源)、财务(账单/收款幂等/滞纳金/收银台/收据/报表)、物业(工单/会议室/巡更/三检/投诉)、惠企九项(放行/场地/装修/论坛/申报/知产/政策)、办公(日程/公文/流程/考勤)、能耗统计、智慧物联(设备/点位/通道/八类设备页)、数据中心(驾驶舱/剖面图/监控大屏)。
- **上线形态**:全 Docker 化,一条命令起全栈;演示用 Cloudflare Tunnel 免费公网。
- **成本**:官方牌价折算约 $780,走中转实际远低于此。用**混合模型 + 子代理并行**是省钱关键(见第 3 节)。

**目标心态**:这类项目"广度 > 深度"。菜单树几十上百个页面,绝大多数是 CRUD 增删改查的变体,真正有业务含量的是少数(合同账单联动、佣金计算、冲突校验)。把力气花在对的地方。

---

## 2. 环境与工具(接手第一件事:逐项检查)

### 2.1 必装运行时(如果不用 Docker 全家桶才需要本机装)
| 工具 | 版本 | 验证命令 |
|---|---|---|
| JDK | 17 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node | 20+ | `node -v` |
| pnpm | 9+ | `pnpm -v`(`npm i -g pnpm`) |
| Docker Desktop | 任意近版 | `docker version` |

> **换机最省事的路子**:上面的运行时**一个都不用装**,只装 Docker Desktop,用第 7 节的全家桶一键起。

### 2.2 强烈建议装的 Claude 技能包:ECC(Everything Claude Code)

ECC 是一套开源的 Claude Code 技能/插件集合(261+ 技能、67 代理、93 命令),**按技术栈自动匹配对应的 md 规则**。对本项目栈(Spring Boot + Java + Vue)高度对口。

**安装(二选一)**:
```bash
# 方式A:插件市场(推荐,自动加载 skills/命令/hooks)
/plugin marketplace add https://github.com/affaan-m/ECC
/plugin install ecc@ecc
/plugin list ecc@ecc        # 确认装好

# 方式B:手动装(仅当插件解析失败时)。注意:插件无法自动分发 rules/,
# 需手动只复制你要的语言规则目录(复制整个目录,不要单文件):
git clone https://github.com/affaan-m/everything-claude-code.git
cd everything-claude-code && pnpm install
mkdir -p ~/.claude/rules
cp -R rules/common ~/.claude/rules/
cp -R rules/typescript ~/.claude/rules/
# ⚠️ 已用 /plugin install 就不要再跑 install.sh --profile full,会重复加载
```

**本项目栈应让 AI 优先调用的 ECC 技能**(按技术栈自动选用):
| 场景 | 用哪个 skill/agent |
|---|---|
| 写后端业务/架构 | `springboot-patterns`、`java-coding-standards`、`jpa-patterns` |
| 后端安全 | `springboot-security`、`security-review`、`security-scan` |
| 后端测试 | `springboot-tdd`、`springboot-verification` |
| Java 代码审查/构建报错 | agent `java-reviewer`、`java-build-resolver` |
| 数据库 | `postgres-patterns`/`database-migrations`、agent `database-reviewer` |
| 前端 | `frontend-patterns`(React 系为主,Vue 取其思路)、agent `typescript-reviewer` |
| API 设计规范 | `api-design`(分页/错误响应/REST) |
| 部署 | `deployment-patterns`、`docker-patterns` |
| 省钱/长任务 | `cost-aware-llm-pipeline`、`iterative-retrieval`、`strategic-compact` |
| 先调研后编码 | `search-first` |

> **给 AI 的规则**:开工前根据当前子任务的技术栈,**主动声明你要用哪个 ECC 技能**,再动手。后端任务默认挂 `springboot-patterns`;若无 ECC,则退回本手册第 5 节的模式。

### 2.3 建议装的 MCP:codegraph(代码图谱)

大项目里"查代码在哪、改这里会炸哪"靠 grep 很慢。codegraph 建立符号级图谱。
- 后端根目录建索引后,查/改代码**先用 `codegraph_explore`**(传 `projectPath` 指向 backend 根),改完 `codegraph sync` 增量更新。
- 本项目 backend 已建过索引(`.codegraph/`)。

---

## 3. 省钱与效率:混合模型 + 子代理并行(这是关键)

这类项目页面多、重复度高,单靠一个顶配模型串行做,又慢又贵。实战验证有效的打法:

1. **主脑用强模型,做规划 + 硬骨头 + 审查集成**;CRUD 页面**派发给便宜模型的子代理并行做**。
   - 强模型(如 Opus/Fable 级):数据库迁移设计、复杂业务(合同账单联动、佣金计算、冲突校验)、跨模块集成、审查子代理产出。
   - 便宜模型(如 Sonnet 级)子代理:标准 CRUD 模块(实体+mapper+controller+列表页+弹窗)。
2. **每批 4~6 个模块并行**,一批做完立即:编译 → 启动 → 前端 build → 接口冒烟测,全绿再进下一批。
3. **共享文件由主脑独占改**(路由 `router/index.js`、菜单 `menu.js`、公共类)。并行子代理只碰自己的新文件,避免合并冲突——这是并行能跑通的**前提**。
4. **子代理指令要给足上下文**:让它先读 `PATTERN.md` + 一个样板 controller + 样板 vue,再照葫芦画瓢;明确"不要跑 mvn/pnpm、不要改共享文件"。

> 效果参考:本项目全量补齐(21 个模块)用 3 个强模型主脑批次 + 12 个子代理,分 4 批完成,每批都实测通过。

---

## 4. 分阶段流程(接手后按此推进,别跳步)

### 阶段一:选型 + 骨架(1 次会话)
- 敲定技术栈,建目录,起数据库(Docker),打通"一个模块从表到页面"的最小闭环。
- **产出规范文件 `docs/PATTERN.md`**:分层约定、命名、Result/分页/异常统一格式、前端 api/页面模板。**后续所有代码(含子代理)都以它为准**。
- 本项目选型:Spring Boot 3.2 + MyBatis-Plus + MySQL8(Docker)+ Flyway + Knife4j;Vue3 + Vite + Element Plus + Pinia + ECharts。

### 阶段二:核心业务模块(主脑主导)
- 优先做**有业务含量、相互有联动**的:楼宇租控 → 招商 → 租户 → 合同 → 财务 → 物业。
- 联动逻辑是灵魂:合同审批 → 自动生成周期账单计划 + 锁房源;收款 → 幂等 + 自动生成收据。

### 阶段三:广度补齐(混合模型 + 子代理并行,见第 3 节)
- 对照需求文档的菜单树,把剩余 CRUD 模块分批并行做掉。
- 一表多用(如投诉/意见共用一张表靠 type 区分)、一组件多路由(靠 `route.meta`)能省大量重复。

### 阶段四:审查加固(主脑,强模型)
- 并发安全(状态流转改"条件 UPDATE 抢状态")、金额精度、幂等键、CORS 收紧、异常不外泄内部细节。
- 提醒:**这类项目通常没测试**,改金额/状态逻辑要格外小心。

### 阶段五:上线
- 加最简登录鉴权(公网暴露前**必须**,见第 6 节)。
- 全 Docker 化(第 7 节)→ 演示用 Cloudflare Tunnel,正式用云服务器。

---

## 5. 可复用代码模式(无 ECC 时的兜底规范)

- **后端务实分层**:`com.<company>.<app>.<模块>.{entity,mapper,controller[,service]}`。**controller 直接注入 mapper,无 service 层**;仅合同/财务/物业等复杂事务才加 service。
- **统一基座**:`common/` 放 `Result`/`PageResult`/`BaseEntity`(tenant_id+审计字段+version+deleted)/`BizException`/全局异常处理/MyBatis-Plus 配置(自动填充审计字段、逻辑删除、乐观锁)。
- **状态流转一律条件更新**:`update ... where id=? and status in (合法前态)`,`updated==0` 抛业务异常。杜绝"先查后改"的并发窟窿。
- **收款等要幂等**:业务幂等键(payNo)唯一 + SQL 原子累加(`setSql("paid = paid + ?")`)。
- **跨表只读引用**:需要引用别模块的表时,在本模块建一个 `@TableName("别的表") 的精简只读实体`,避免跨包强依赖。**但类名要唯一**(见第 6 节 bean 冲突坑)。
- **跨模块聚合(驾驶舱/报表)**:用 `JdbcTemplate` 写常量 SQL,只读,不硬塞进某个模块。
- **前端**:一个 `request.js`(带 token、统一错误、401 跳登录);api 按模块分文件;页面 = 查询栏 + 表格 + 增删改弹窗三段式;一组件多路由靠 `route.meta` + `watch` 兜底 keep-alive。
- **数据库迁移**:Flyway `V{n}__desc.sql`,**每个迁移自带种子数据**,新库开箱即用、无需手工导数。

---

## 6. 踩坑清单(真实踩过的,务必避开)

1. **Spring bean 按类简名注册,跨包重名类会 `ConflictingBeanDefinition` 启动失败**。
   新增实体/controller/mapper 前先查重:
   `find . -name '*.java' | sed 's|.*/||' | sort | uniq -d`
   本项目撞过:`oa.Flow`↔`finance.Flow`、`crm.ContractRef`↔`finance.ContractRef`、`iot.Channel`↔`crm.Channel`,均靠加前缀(OaFlow/CrmContractRef/IotChannel)解决。
2. **`mvn spring-boot:run` 一定要在 backend 目录跑**。在别的目录跑会报 `No plugin found for prefix 'spring-boot'`——这是 cwd 不对,不是代码问题。
3. **CORS 白名单收紧后,换公网域名访问必 403**。本地测全是 localhost 发现不了。公网/隧道域名要加进 `allowedOriginPatterns`(隧道域名随机可用 `https://*.trycloudflare.com` 通配)。
4. **公网暴露前必须有登录**。没鉴权挂公网 = 任何人能改你的数据。演示级最简方案:密码加盐哈希存库 + 内存 token + 全局拦截器(除登录接口);密码字段用 `@JsonProperty(WRITE_ONLY)` 不外泄。正式上线换 Spring Security + BCrypt + JWT。
5. **Dockerfile 里 jar 名要对**。pom 设了 `<finalName>` 就不带版本号(如 `zhyq-park.jar` 而非 `zhyq-park-1.0.0.jar`),COPY 路径写错会 "jar not found"。
6. **国内 Docker 构建拉基础镜像易超时**(`auth.docker.io` 超时)。先单独 `docker pull` 把基础镜像(temurin/maven/node/nginx)拉到本地缓存,再 compose build 走缓存。Maven 用 aliyun 镜像源。
7. **`git clone` 只有在你自己推过远程仓库后才成立**。默认项目只在本机,换机靠**拷文件夹**,不是 clone(除非你主动建了 GitHub 仓库)。
8. **字符串排序陷阱**:查 Flyway 最高版本别 `MAX(version)`(字符串序里 "9">"16"),用 `MAX(CAST(version AS UNSIGNED))`。

---

## 7. 换机 / 部署(全 Docker,已验证)

新电脑只装 Docker Desktop:
```bash
# 1. 拷贝整个项目文件夹过去(或 git clone,若已推远程)
# 2. 一键起全栈
docker compose -f docker-compose.full.yml up -d --build
# 打开 http://localhost ,演示账号见项目 DEMO_ONLINE.md
```
- `docker-compose.full.yml` = MySQL + 后端 + 前端(Nginx 托管静态 + 反代 /api),唯一出口 80。
- `docker-compose.yml`(仅 MySQL)用于本地开发裸跑热重载。
- 数据库连接走环境变量(`DB_URL/DB_USERNAME/DB_PASSWORD`),Flyway 首启自动建全部表+种子。
- 端口 80 被占就把 compose 里 `"80:80"` 改 `"8080:80"`。
- 要保留手工录入的数据才需导出:`docker exec <mysql容器> mysqldump ...`。

---

## 8. 记忆与文档在哪(接手 AI 应先读)

- 本文件 `PLAYBOOK.md`(项目根)—— 方法论总纲。
- `docs/PATTERN.md` —— 代码模式硬规范(写代码前必读)。
- `docs/DEPLOY.md` —— 换机/部署细节。
- `docs/DEMO_ONLINE.md` —— 演示上线(隧道)手册与账号。
- `docs/memory/` —— 随项目走的记忆备份(zhyq-project / zhyq-backend-map),记录了每一轮做了什么、踩了什么坑。
- `README.md` —— 项目启动说明。

> 接手顺序建议:PLAYBOOK(本文) → PATTERN → docs/memory/*。读完就具备继续开发的全部上下文。
