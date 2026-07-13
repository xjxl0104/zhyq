# 演示上线(Cloudflare Tunnel 路线B)

## 当前公网地址
每次重开隧道地址会变,以最新一次为准。查看:`grep trycloudflare /tmp/zhyq_tunnel.log`

## 演示账号
admin / zhyq@2026(全部种子用户同密码;token 8小时过期,后端重启失效)

## 三件套启动顺序(Mac 上)
```bash
cd ~/Documents/dipark/zhyq && docker compose up -d          # 1. MySQL
cd backend && mvn spring-boot:run &                          # 2. 后端 :8090
cd ../frontend && pnpm build && pnpm preview &               # 3. 前端生产包 :4173(含 /api 代理)
cloudflared tunnel --url http://localhost:4173 &             # 4. 隧道 → 输出公网 URL
```

## 注意
- Mac 关机/睡眠即断线;演示期间系统设置里关掉睡眠。
- trycloudflare 免费隧道无 SLA,正式使用走云服务器方案(见对话记录路线A)。
- 登录为演示级(SHA-256+盐、内存 token);上生产须换 Spring Security+BCrypt+JWT。
