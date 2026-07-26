---
name: zhyq-ver4-prod-hardening
description: "zhyq ver4.0 任务 = 生产化改造与云端部署;当前 prod-hardening 分支;3.3→4.0 提交节奏"
metadata:
  node_type: memory
  type: project
---

**ver4.0 = 「智慧园区系统生产化改造与云端部署计划」**(来源:2026-07-26 另一 session 的规格原文,dipark/zhyq,目标阿里云 ECS 121.40.120.226,独立部署不并入奥乐平台)。

## 提交节奏(user 明确要求)
- 从 `ver3.2` 拉 `prod-hardening` 分支改,不动稳定分支。
- **3.2 节(清默认密码/硬编码)+ 3.3 节(SHA256+内存Token → Spring Security+BCrypt+JWT)完成 = 提交 ver3.3**。
- **3.4 节(用户→角色→权限标识 RBAC)完成 = 提交 ver4.0**。

## 代码改造清单(3 节)
- **3.2 配置外置**:DB/JWT/加密密钥全走环境变量,生产不给不安全默认值;`.env.example` 只留变量名;`.env`/私钥/证书/生产配置进 `.gitignore`;删默认 MySQL 密码。→ 已做(commit 30d1015)
- **3.3 正式认证**:Spring Security + BCrypt + JWT Access Token + DB 用户状态校验 + 统一 401/403 + 可配 Token 有效期 + 登录/退出/鉴权自动化测试;密码字段长度够存 BCrypt;不兼容明文。→ 已做(commit 96edde0,JwtServiceTest 4 绿)
- **3.4 RBAC**:用现有 用户/角色/资源/菜单权限模型 建 `用户 -> 角色 -> 权限标识`。→ **待办(做完即 ver4.0)**

## 部署顺序(8 步,后续)
调研 ECS 但无权限不阻塞 → 本地生产化改造 → 预留真实设备连接器+统一数据模型 → 本地 Docker 模拟生产验收 → 拿到 ECS 权限后部独立测试环境 → 临时二级域名+HTTPS → 拿到厂家协议后逐个接真实设备 → 备份/监控/回滚验证后上线。环境无关:IP/域名/端口/密钥全环境变量,不写死。

## 坑
- user 要求「完成 ver4.0 前每 10 分钟自查一次进程状态、不要中断」→ 这是多个后台 job/cron 窗口分裂的根因。进度用主任务文字 narrate 即可,别再另起独立 cron/监控 job。见 [[zhyq-build-env]] [[zhyq-project]]。
