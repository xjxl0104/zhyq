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

- **表结构 + 演示数据**:Flyway 迁移(V1~V16)在后端首次启动时自动执行,新库开箱即用,无需手工导入。
- **端口 80 被占用**:把 `docker-compose.full.yml` 里 frontend 的 `"80:80"` 改成 `"8080:80"`,访问 http://localhost:8080。
- **迁移业务数据**(仅当你想保留手工录入的数据):
  ```bash
  # 旧机导出
  docker exec zhyq-mysql mysqldump -uroot -pzhyq123456 zhyq_park > zhyq_backup.sql
  # 新机起库后导入
  docker exec -i zhyq-mysql mysql -uroot -pzhyq123456 zhyq_park < zhyq_backup.sql
  ```

## 两套 compose 的区别

| 文件 | 用途 |
|---|---|
| `docker-compose.yml` | **本地开发**:只起 MySQL(:3316),前后端在宿主机裸跑热重载 |
| `docker-compose.full.yml` | **整体部署/换机/上云**:MySQL + 后端 + 前端全容器化,一个 80 端口出口 |

## 上云(生产)提醒

这套 full 镜像同样可直接部署到云服务器。上生产前须改:
1. MySQL root 密码(compose 里的 `zhyq123456`)
2. 登录鉴权换成 Spring Security + BCrypt + JWT(当前为演示级)
3. CORS 白名单收紧到正式域名(现放通了 localhost 和 trycloudflare)
