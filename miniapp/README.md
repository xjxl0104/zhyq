# 园区伙伴小程序(全民营销)

uni-app + Vue3,同一套代码出微信小程序与 H5。规范:`../docs/marketing/PARK-MKT-001-开发方案-V1.2.md` §5.2 / §6.2。

## 跑起来
```sh
pnpm install
pnpm dev:h5          # 浏览器调试,走 vite 代理到本地后端 8090
pnpm dev:mp-weixin   # 产物在 dist/dev/mp-weixin,用微信开发者工具「导入项目」打开
```
开发环境默认 `zhyq.mp.mock-login=true`（前端 `VITE_MP_MOCK_LOGIN` 未设为 `false`）：登录页输入一段字符当 js_code，手机号明文填写。
生产环境拿到小程序 AppID/Secret 后:
1. `src/manifest.json` 里 `mp-weixin.appid` 填 AppID;
2. 后端环境变量 `WX_MP_APPID / WX_MP_SECRET / WX_MP_MOCK_LOGIN=false`；前端构建环境设置 `VITE_MP_MOCK_LOGIN=false`；
3. 登录页会调用 `uni.login()`，手机号通过 `button open-type="getPhoneNumber"` 获取 `encryptedData` 和 `iv`，后端使用微信 session_key 解密并校验手机号。接口只返回 `registered/openid/token/me`，不会返回 session_key。

验收：mock 模式输入同一标识可重复登录；真实模式用微信开发者工具登录并授权手机号，确认手机号和 openid 绑定后 token 可访问伙伴接口。缺少 AppID/Secret、微信返回错误、缺少加密字段或解密失败时请求应返回稳定业务错误，且不会写入伙伴记录。

## 页面(伙伴端 11 页)
登录/绑定 · 首页 · 推荐客户 · 我的客户 · 客户详情 · 我的岗位 · 我的团队 · 团队分配(钻石) · 收益明细 · 提现 · 我的(实名/账户/协议/海报)

微信审核文案要求(PARK-MKT-001 §9):只用「推荐有礼 / 推荐客户 / 园区伙伴 / 合伙人」,不出现「分销 / 下线 / 团队佣金」。

## 云仓端（新增）

云仓端使用独立的 `wh_token` 和 `/wh/v1/**` 接口，与伙伴端 `mp_token` 分开保存。页面覆盖云仓登录、加盟申请、五步进度、ERP 自助、看板、客户、出库单、结算、合同/协议和通知；服务端根据 token 推导 `warehouse_id`，客户端传入的仓库字段不会改变数据范围。

开发验收：

```sh
pnpm build:h5
pnpm build:mp-weixin
```

云仓 mock 登录和后台接口需要启动对应后端配置；真实微信联调仍需配置云仓 AppID/Secret、联系人授权和 ERP 沙箱凭证。云仓 401 会清理 `wh_token` 并回到云仓登录页，不会清理伙伴 `mp_token`。
