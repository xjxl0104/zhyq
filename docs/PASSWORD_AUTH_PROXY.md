# 小程序密码登录：可信代理与按来源限速

密码登录、注册、设置接口使用 `ClientAddressResolver` 计算来源地址。默认
`ZHYQ_AUTH_TRUSTED_PROXIES` 为空，忽略所有代理头，仅使用实际连接对端。这适用于
本地直接访问后端；反向代理部署必须显式配置已核验的可信代理，否则多个用户将
共享代理地址的限额。`docker-compose.full.yml` 因此将此变量设为必填。

## 配置规则

- 配置值为逗号分隔的数值 IP 或 CIDR，建议固定代理的精确 `/32`（IPv6 `/128`）。
- 不接受域名、无效地址或 `0.0.0.0/0`、`::/0`。不要盲目信任全部私有网段。
- 保持 `server.forward-headers-strategy=none`，不要另外启用改写 `remoteAddr` 的
  `ForwardedHeaderFilter` / Tomcat RemoteIpValve。解析器必须能看到实际 socket 对端。
- 仅当实际对端可信时才读取 `X-Forwarded-For`，从右向左剥离已配置的代理，遇到
  第一个非可信地址即停止；用户伪造的更左侧地址不会成为限速键。
- 每一跳可信代理必须按其真实上游连接追加或覆盖 XFF。现有 Nginx 的
  `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for` 符合此规则。
  不使用 `X-Real-IP`：多跳代理时它可能仍然是上一跳代理地址。
- 后端不得通过公网或宿主端口绕过这些代理公开；也不要把不可信应用容器加入
  被信任范围。直连且不在信任名单的来源即使发送代理头，仍按它自己的 socket IP 限速。

例如 `客户端 → Caddy → Nginx → backend`，实际连接来自 Nginx，XFF 为
`客户端, Caddy`。需同时配置 Nginx 和 Caddy 的地址，才能取出客户端地址：

```dotenv
ZHYQ_AUTH_TRUSTED_PROXIES=<nginx实际IP>/32,<caddy实际IP>/32
```

## 当前生产链路核验与发布要求

2026-09-23 只读核验结果：生产公网入口为 Caddy 443，经 `zhyq-frontend:80` 转到
`backend:8090/api`；前后端均无宿主端口映射。三者处于 `zhyq-edge` 网络，当时
Nginx 为 `172.24.0.5`，Caddy 为 `172.24.0.2`。Caddy 为标准 `reverse_proxy`，
没有额外 `trusted_proxies` / `header_up` 覆盖；Nginx 按上述方式追加 XFF。

这些容器当前使用动态 IP，不能把该快照永久硬编码。发布时：

1. 核验实际生效的 Compose、Caddy、Nginx 配置，确认后端没有新增宿主端口映射，
   Caddy 仍为面对外部用户的入口，且 XFF 没有被不可信头覆盖。
2. 重建前端后重新读取 Nginx 和 Caddy 的网络地址，以精确 `/32` 写入后端部署环境。
3. 重建后端使环境生效，再核对运行环境值与实际两跳地址相同。日后任一代理重建
   改变地址，也必须同步更新后端环境；可在运维配置中为代理分配经过审查的固定 IP。
4. 分别从两个真实来源发起请求，确认日志/限速按来源区分；从非可信来源添加伪造
   XFF 不应改变其限速地址。先在隔离测试环境验证，避免在生产耗尽登录窗口。

不要仅配置 Nginx：这会把全部用户归到 Caddy 地址。不要为省略 IP 更新而直接信任
整个包含其他业务容器的 `/16`。更换为 Cloudflare Tunnel、负载均衡器或新增代理时，
先重新核验完整链路及最外层代理对 XFF 的处理，然后调整信任配置。

自动化回归覆盖默认不信任、伪造头、两跳真实链、未知中间代理、多个头行、IPv6、
无效/超长链，以及同一 Nginx 后两个来源不会互相耗尽 IP 配额。
