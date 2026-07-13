---
name: zhyq-project
description: "zhyq 智慧园区新项目 — 全新 Java+Vue3、不用 yudao、从零搭,目录在 ~/Documents/zhyq"
metadata: 
  node_type: memory
  type: project
  originSessionId: 8cc85619-e366-45c1-a211-8e18d3bcb1fe
---

用户在做一个**全新的智慧园区管理系统**,代号 zhyq,技术栈 Java + Vue3,**从零搭建、明确不用 yudao**。目录 `~/Documents/zhyq`,与 [[dipark-project]] 同级但独立无关。

**2026-07-02 已完成一期 MVP(按需求文档 `~/Downloads/智慧园区管理系统_完整功能与建设需求规格书.docx`):**
- 选型敲定:Spring Boot 3.2 + MyBatis-Plus + MySQL8(Docker,端口 **3316**,库 zhyq_park,root/zhyq123456)+ Flyway(V1~V8)+ Knife4j;前端 Vue3+Vite+Element Plus+Pinia+ECharts。后端端口 **8090**(context-path `/api`),前端 **5273**(vite proxy 到 8090)。
- 后端务实分层:`com.zhyq.park.<模块>.{entity,mapper,controller}`,controller 直接用 mapper(**无 service 层**,复杂业务才加)。BaseEntity 统一 tenant_id/审计字段/version/deleted,MyMetaObjectHandler 自动填充,操作人固定 "system"。42 张表。
- 模块:system(用户/角色/部门/岗位/字典)、building(项目/楼宇/楼层/房间/租控)、crm(线索/跟进/渠道)、tenant、contract(**审批→自动生成周期账单计划+锁房源**)、finance(账单/收款幂等/滞纳金万5每天/报表)、property(工单全流程+会议室冲突校验)、todo、energy/iot/oa/hui(骨架)、dashboard(驾驶舱/大屏聚合,用 JdbcTemplate)。
- 前端 14 个一级导航 + **监控大屏 `/screen`**(深色指挥中心,本地预览)。代码模式规范在 `docs/PATTERN.md`,启动说明在 `README.md`。
- **按用户要求跳过:登录鉴权页、视频流。** 接口无鉴权层(README 已注明生产需补)。
- 已验证:合同审批生成账单+锁房、收款部分/全额结清、滞纳金、会议室冲突,前端 build 通过。

**How to apply:** 续做时遵循 `docs/PATTERN.md`;骨架模块(energy/iot/oa/hui)深度浅,需补细节;惠企/办公/能耗/物联的高级功能(商城交易、流程引擎、物模型、峰谷计费)尚未做。预算敏感(用户给过 80 美金额度),优先复用模式、用子agent并行。


**2026-07-03 演示上线(路线B)**:已加最简登录(admin/zhyq@2026,SHA256+盐、内存token 8h、拦截器全局鉴权、密码JsonProperty WRITE_ONLY 不外泄);前端 pnpm preview :4173 单端口(vite preview allowedHosts+/api代理);cloudflared quick tunnel 暴露公网(地址每次启动变,查 /tmp/zhyq_tunnel.log)。启动手册在 zhyq/docs/DEMO_ONLINE.md。正式上线走路线A(云服务器),届时鉴权换 Security+BCrypt+JWT。


**2026-07-08 全 Docker 化(换机/上云用)**:新增 backend/Dockerfile(maven多阶段→jre)、frontend/Dockerfile(node build→nginx 托管+反代 /api,配 frontend/nginx.conf)、docker-compose.full.yml(mysql+backend+frontend,唯一出口 80)、两个 .dockerignore。application.yml datasource 改环境变量占位 DB_URL/DB_USERNAME/DB_PASSWORD(默认值保留本地裸跑)。**注意 pom finalName=zhyq-park,jar 名是 zhyq-park.jar 不带版本号**。换机两步:拷文件夹→`docker compose -f docker-compose.full.yml up -d --build`,打开 http://localhost。Flyway V1~V16 容器内自动建表灌种子(实测 79 表全建)。国内构建坑:docker hub 拉基础镜像易超时,已预拉 eclipse-temurin:17-jre/maven:3.9-eclipse-temurin-17/node:20-alpine/nginx:1.27-alpine 到本地缓存。手册 docs/DEPLOY.md。docker-compose.yml(仅mysql:3316)仍用于本地开发裸跑。


**2026-07-13 换机到 Mac + 升级调研启动**:项目正式位置改为 `~/Documents/zhyq`(Mac,从 Windows 机拷来;此副本 git 无提交历史)。升级调研方案 v2 已确认并执行中——25 家对标(国内商业8/国际商业7/开源7/前端标杆3),只看功能分类/流程编排/前端设计/架构与功能设计,不看别人后端实现;AI 暂缓只预留扩展点;产出先报告后实施。25 张对标卡片在 `docs/upgrade/cards/`,方案在 `docs/upgrade/调研方案.md`,状态详见 [[zhyq-upgrade-research]]。前端也建了 codegraph 索引,见 [[zhyq-frontend-map]]。**注意**:原 `~/Downloads` 里的 zhyq 旧副本、PLAYBOOK.md、两份需求规格书 docx 已被用户清理且废纸篓已空——PLAYBOOK.md 已从会话上下文原样恢复到项目根,docx 无法恢复(要点已沉淀在 CLAUDE.md 与本记忆)。
