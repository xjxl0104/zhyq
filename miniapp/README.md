# 全民营销小程序：园区伙伴与云仓商家

uni-app + Vue3，同一工程输出微信小程序与 H5。当前正式小程序为“智慧云仓全民营销助手”，AppID `wx156df8d81fd39ef5`。两个身份使用独立的业务权限与 token。

## 登录与注册

- 园区伙伴、云仓商家均可使用**账号密码**注册及登录，也保留微信快捷登录。
- 账号为 4–32 位字母、数字或下划线（后端统一小写）；密码为 8–64 位且包含字母和数字，BCrypt 限制 UTF-8 长度不超过 72 字节。
- 账号注册填写的手机号仅用于联系，没有短信验证。已有业务手机号不能据此认领/合并资料。已有微信用户登录后，在“我的 → 账号与密码”或云仓工作台“账号与密码”设置密码；已配置密码时修改须提供原密码。
- 伙伴注册写入 `crm_promoter`，显示于智慧园区“招商 → 全民营销 → 伙伴管理”。云仓注册写入 `crm_warehouse` 并进入资质审核，显示于“云仓管理/加盟申请”。凭据保存于 `crm_marketing_credential`，不创建管理后台 `sys_user`。
- 新云仓注册后进入加盟资料页。注册账号不代表审核通过。

## 本地运行与构建

```sh
pnpm install
pnpm dev:h5
pnpm dev:mp-weixin
pnpm build:h5
pnpm build:mp-weixin
```

H5 开发请求经 Vite 代理到本机 `8090`。微信构建默认接口为 `https://zhyq.aoleplat.com/api/mp/v1` 和 `/api/wh/v1`，可用 `VITE_API_BASE`、`VITE_WH_API_BASE` 覆盖；微信端必须为 HTTPS 绝对地址。构建结束自动核对产物与源码的 AppID。

微信开发者工具导入 `dist/build/mp-weixin`。确认工具实际显示的 AppID 与源码一致，再重新编译/真机预览。不要只修改生成目录的 `project.config.json`；后续构建会覆盖它。

## 微信真实登录配置

前后端默认关闭 mock。正式服务器配置必须成对匹配：

- `WX_MP_APPID` / `WX_MP_SECRET`
- `WX_WH_APPID` / `WX_WH_SECRET`
- `WX_MP_MOCK_LOGIN=false` / `WX_WH_MOCK_LOGIN=false`

本工程同一个小程序包含两个身份，服务器两组凭据应配置为当前正式小程序的同一组 AppID/Secret。不得将测试号 Secret 与正式号 AppID 混用。密钥仅放服务器环境变量，不写前端或 Git。

真实流程：`uni.login` 获取一次性 code，连同运行中的 AppID 发送 `/auth/wx-login`。未注册用户获得 5 分钟一次性的 `loginTicket`；授权手机号后发送 `{openid,loginTicket,phoneCode}`。服务端使用微信 `stable_token` 和 `getuserphonenumber` 取得手机号，校验 AppID。旧 `encryptedData/iv` 仍兼容，但也需要票据。失败后重新微信登录获取新票据。

切换 AppID 会改变用户 openid。旧测试号档案不会自动合并或清空；须由原登录会话先设置密码，或由运营核验后迁移微信绑定。

## 开发模拟

仅开发时显式设置前端 `VITE_MP_MOCK_LOGIN=true` / `VITE_WH_MOCK_LOGIN=true`，以及对应后端 `WX_MP_MOCK_LOGIN=true` / `WX_WH_MOCK_LOGIN=true`。测试标识模拟微信身份、手机号明文填写；不可用于生产。

## 上线验证

- 先执行 V66 新凭据表迁移（仅新增表），核对生产迁移版本避免冲突。
- 反代部署配置 `ZHYQ_AUTH_TRUSTED_PROXIES` 为实际可信代理的 IP/CIDR。服务端仅从可信 socket peer 接受 X-Forwarded-For，并从右向左剥离可信跳点；禁止配置为全网。代理 IP 变化须同步后端配置，防止所有用户共用代理 IP 限额。
- 检查两端注册落库、错误密码、重复手机号、已有用户设置密码及跨角色接口拒绝。
- 在微信公众平台为正式小程序配置 request 合法域名 `https://zhyq.aoleplat.com`，确认手机号能力可用；真机微信授权需由微信用户亲自确认。
