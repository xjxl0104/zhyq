# 部署 / 换机指南(全 Docker)

## 换到新电脑,只需两步

新电脑**只要装 Docker Desktop**(不用装 Java / Node / Maven / MySQL)。

```bash
# 1. 把整个 zhyq 文件夹拷过去(或 git clone)
# 2. 进目录一键起
cd zhyq
docker compose -f docker-compose.full.yml up -d --build
```

首次构建约 3–6 分钟(下依赖 + 编译)。完成后打开:

- **http://localhost** — 系统首页(演示账号 `admin` / `zhyq@2026`)

后端接口在 `http://localhost/api`,由前端 Nginx 反代,无需单独暴露。

## 常用命令

```bash
docker compose -f docker-compose.full.yml ps          # 看三个容器状态
docker compose -f docker-compose.full.yml logs -f backend   # 看后端日志
docker compose -f docker-compose.full.yml down        # 停止(数据保留)
docker compose -f docker-compose.full.yml up -d --build      # 改代码后重建
```

## 数据说明

- **表结构 + 演示数据**:Flyway 迁移(V1~V33)在后端首次启动时自动执行,新库开箱即用,无需手工导入。
- **端口 80 被占用**:把 `docker-compose.full.yml` 里 frontend 的 `"80:80"` 改成 `"8080:80"`,访问 http://localhost:8080。
- **迁移业务数据**(仅当你想保留手工录入的数据):
  ```bash
  # 旧机导出
  docker exec zhyq-mysql mysqldump -uroot -pzhyq123456 zhyq_park > zhyq_backup.sql
  # 新机起库后导入
  docker exec -i zhyq-mysql mysql -uroot -pzhyq123456 zhyq_park < zhyq_backup.sql
  ```

## ver4.2 升级说明（用户多角色与角色权限配置）

升级前建议先备份数据库；随后切换到 `ver4.2` 分支并重建容器：

```bash
git fetch origin
git checkout ver4.2
git pull --ff-only origin ver4.2
docker compose -f docker-compose.full.yml up -d --build
```

后端启动时 Flyway 会自动执行 `V31__rbac_management.sql`：

- 保留全部现有用户及角色关联，包括已经上线的两个超级管理员；仅清理重复关联记录。
- 为用户—角色、角色—菜单关系增加唯一约束，防止重复授权。
- 新增“用户分配角色”和“角色配置权限”两个权限点，并自动授予现有 `admin` 角色。
- `admin` 角色为系统保护角色，不能编辑、删除或重新配置权限；系统始终至少保留一个启用的超级管理员。

升级完成后，现有超级管理员需要**退出并重新登录一次**，JWT 才会包含新增管理权限。之后可在“系统管理 → 用户管理”中为用户多选角色（选择 `admin` 即创建超级管理员），并在“系统管理 → 角色管理”中为普通角色配置菜单和按钮权限。角色调整同样在相关用户下次登录时生效。

V31 不删除业务用户或角色，应用代码临时回退到 `ver4.1` 时可保留该迁移产生的唯一索引和权限数据；不要手工删除 Flyway 历史记录。

## ver4.3 升级说明（应收明细与自动售货机）

先备份数据库和宿主机 `uploads`，再准备 `.env`。字段加密密钥必须是 Base64 编码的 32 字节随机值，已上线环境一旦启用后应长期固定保存，丢失密钥将无法解密已导入的完整收款账户：

```bash
# 生成一次并写入 .env；不要把输出提交到 Git
openssl rand -base64 32

# .env 示例（填写刚生成的值）
ZHYQ_FIELD_ENCRYPTION_KEY=<base64-32-byte-key>
VENDING_EXTERNAL_URL=https://fanmaiji.top/index?isFrom=login
```

升级并启动：

```bash
git fetch origin
git checkout ver4.3
git pull --ff-only origin ver4.3
docker compose -f docker-compose.full.yml config
docker compose -f docker-compose.full.yml up -d --build
docker compose -f docker-compose.full.yml logs --tail=200 backend
```

Flyway 将依次执行：

- `V32__receivable_import.sql`：共享导入批次/审计行、应收登记表、计费规则、保证金、加密收款账户，以及账单幂等字段。
- `V33__vending_integration.sql`：售货机机器、销售、补货、故障、对账五张本地副本表，应用卡片和四项权限。

升级后现有超级管理员需退出并重新登录，使 JWT 获得新增权限。进入“财务 → 应收明细登记表”导入园区基础资料；进入“应用中心 → 自动售货机”使用外部入口或下载五类标准模板。售货机厂商当前没有 API，本版本禁止自动登录、验证码绕过、页面抓取、Cookie/密码保存和反向写回。

应收工作簿导入前必须先预览并绑定租户、空间/房间与合同；原始工作簿不会保存，完整账户只以 AES-GCM 密文保存。售货机仅接受系统下载的标准模板，厂商原生导出文件在取得真实样例并通过验证前不受支持。两类导入均可从页面查看持久化批次并受控撤销，详细口径和回滚边界见 `docs/VER4.3.md`。

## 两套 compose 的区别

| 文件 | 用途 |
|---|---|
| `docker-compose.yml` | **本地开发**:只起 MySQL(:3316),前后端在宿主机裸跑热重载 |
| `docker-compose.full.yml` | **整体部署/换机/上云**:MySQL + 后端 + 前端全容器化,一个 80 端口出口 |

## 空间树(#3)部署后回填步骤 ⚠️ 必读

统一空间树主数据 `sys_space` 采用**手动 reconcile**（不随启动自动跑）。历史业务数据的 `space_id` 回填迁移 `V21__backfill_space_id.sql` 会在容器启动时由 Flyway 自动执行，但**此时 `sys_space` 尚未 reconcile、表为空，V21 会匹配 0 行**（且 Flyway 记为已应用、不会再自动重跑）。

因此**每次全新部署（或首次灌种子后）**，须按下面顺序手动补一次：

```bash
# 1) 登录拿 token（admin / zhyq@2026）
TK=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"zhyq@2026"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# 2) 触发空间树全量回填（把 project/building/floor/room 投影进 sys_space）
curl -s -X POST http://localhost/api/space/reconcile -H "Authorization: Bearer $TK"

# 3) reconcile 完成后，重跑一次 V21 的回填 UPDATE（幂等，只填 space_id IS NULL 的行）
#    直接对容器内 MySQL 执行下面 5 条语句（库名 zhyq_park）：
docker compose -f docker-compose.full.yml exec -T mysql \
  mysql -uroot -pzhyq123456 zhyq_park <<'SQL'
UPDATE biz_contract_room r JOIN sys_space s ON s.ref_type='room' AND s.ref_id=r.room_id AND s.deleted=0 SET r.space_id=s.id WHERE r.space_id IS NULL;
UPDATE pm_work_order w JOIN sys_space s ON s.ref_type='room' AND s.ref_id=w.room_id AND s.deleted=0 SET w.space_id=s.id WHERE w.space_id IS NULL;
UPDATE eng_reading rd JOIN eng_meter m ON rd.meter_id=m.id JOIN sys_space s ON s.ref_type='room' AND s.ref_id=m.room_id AND s.deleted=0 SET rd.space_id=s.id WHERE rd.space_id IS NULL;
UPDATE biz_contract c JOIN sys_space s ON s.ref_type='project' AND s.ref_id=c.project_id AND s.deleted=0 SET c.space_id=s.id WHERE c.space_id IS NULL;
UPDATE iot_device d JOIN sys_space s ON s.ref_type='building' AND s.ref_id=d.building_id AND s.deleted=0 SET d.space_id=s.id WHERE d.space_id IS NULL;
SQL
```

> 说明：V21 语句本身幂等（`WHERE space_id IS NULL`），重跑安全。日常运行中新建的房间等由 building controller 增量同步进 `sys_space`，无需手动 reconcile；每日 03:17 有对账 Job 兜底漂移。`biz_contract`（无 room_id，回填到 PROJECT 级）与 `iot_device`（无 room_id，回填到 BUILDING 级）为已知粒度降级，详见 `docs/upgrade/#3-空间树设计.md`。

## 上云(生产)提醒

这套 full 镜像同样可直接部署到云服务器。上生产前须改:
1. ~~MySQL root 密码(compose 里的 `zhyq123456`)~~ 已外置:compose 读 `.env` 的 `DB_ROOT_PASSWORD`(必填)
2. ~~登录鉴权换成 Spring Security + BCrypt + JWT~~ 已完成(ver4.0):无状态 JWT + BCrypt,`JWT_SECRET` 必须由 `.env` 提供
3. CORS 白名单收紧到正式域名:`.env` 的 `CORS_ALLOWED_ORIGINS`(默认仍放通 localhost/trycloudflare,生产必改)
4. 上线后立刻逐用户改密:V29 曾把演示账号统一重置为 `zhyq@2026`,用户管理页(编辑用户 → 填新密码)即可 BCrypt 重置
5. 附件访问已收紧:`/uploads` 不再匿名静态放行,统一走鉴权接口 `GET /api/file/download/{id}`;附件删除仅上传者本人或 admin

### 文件上传持久化
上传文件存于后端容器 /app/uploads,已通过 docker-compose.full.yml 挂载宿主 ./uploads 持久化。
换机/重建容器前备份宿主 ./uploads 目录即可保留附件。
