# 模型背景配色与公网加载补修

## 背景配色

地面、天空、雾和环境光统一为冷蓝灰/淡蓝紫；加载、错误遮罩及转圈指示器同步调整。保留植被本色与模型结构。晴天、夜景和 1024px/1440px 页面均已在 Chromium 中查看，未发现页面 JavaScript 异常。

## 公网压缩根因

上一版在 2026-09-15 11:53 完成部署，但公网 `https://zhyq.aoleplat.com/models/dipark-warehouse.glb` 对 `Accept-Encoding: gzip` 仍返回 15,308,668 字节。13:16 的实际请求在 25 秒超时前只下载了 10,207,232 字节。

直接访问前端容器会返回 2,439,554 字节 gzip 文件；补上网关带来的 `Via` 请求头即可复现未压缩响应。Nginx 默认 `gzip_proxied off`，且 `gzip_static` 也受它控制，详见 [Nginx gzip_static 文档](https://nginx.org/en/docs/http/ngx_http_gzip_static_module.html)和 [gzip_proxied 文档](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_proxied)。

修复仅在 `/models/` 配置 `gzip_proxied any`，继续遵守客户端的压缩能力和协商缓存。

## 验证

- 原公网入口运行 `node frontend/scripts/check-model-compression.mjs https://zhyq.aoleplat.com` 明确失败：缺少 `Content-Encoding: gzip`。
- 使用线上相同 Nginx 1.27.5 二进制启动仅监听容器内部 127.0.0.1:18081 的独立验证进程，载入修复配置，不修改线上运行配置。`nginx -t` 通过。
- 验证进程对 `Via + Accept-Encoding: gzip` 返回 gzip、2,439,554 字节；对 `Via + Accept-Encoding: identity` 返回原始文件、15,308,668 字节。验证完成后关闭该进程。
- 公网回归脚本同时检查两种编码的响应，可在部署后重复运行。容器内部检查不能替代这项公网检查。
- 前端完整测试 30 个文件、111 项通过；`pnpm build` 与后端 `mvn -B test-compile` 通过。加载遮罩、桌面、窄屏及夜景截图检查通过。
