# 云仓 ERP 接入契约 v1.1

> 给加盟云仓技术人员。园区侧实现见 `backend/src/main/java/com/zhyq/park/marketing/open/`;业务规则见 `PARK-MKT-001-开发方案-V1.2.md` §2.6 / §5.3。
> 当前只实现 **webhook 推送**(云仓主动推事件给园区)。拉取模式与文件模式暂不开放。

## 1. 接入流程

1. 园区运营在后台「全民营销 › ERP 对接」为你的云仓**签发沙箱凭证**,把 `App-Id` 与 `Secret` 交给你(Secret 只显示一次,丢了只能重置)。
2. 运营在「货主编码映射 → 新增映射」中**搜索并选择本系统客户名称**，填写该客户在你们 ERP 里的**货主编码**(`customer_code`) 后保存，无需手填客户 ID。同名客户可根据选项中的客户编号核对。映射列表显示当前客户名称，系统内部仍按唯一客户 ID 关联，客户改名不会改变订单归属。订单归属依据当前云仓的货主编码映射，不认收件人手机号，也不会仅凭同名自动合并客户。
3. 沙箱凭证调用同一订单接口，返回逐条契约校验结果和 `sandbox=true`。不会写入正式 Inbox、订单、佣金或正式同步日志；通过格式校验不代表合同、定价、财务业务联调完成。
4. 运营确认接入准备完成后把凭证**切正式**，同一 App-Id 与 Secret 不变；切换环境、启停或重置密钥都会要求重新验证连接。
5. 正式 ERP 调用签名心跳验证连接，再按货主映射和真实合同推送业务事件；没有订单的时段也应定时发送心跳（建议每 30 分钟一次）。

## 2. 地址与鉴权

```
POST https://<园区域名>/api/open/v1/erp/order-event
POST https://<园区域名>/api/open/v1/erp/orders/batch      # ≤ 500 条
GET  https://<园区域名>/api/open/v1/erp/contract          # 需验签,body 为空串
GET  https://<园区域名>/api/open/v1/erp/ping              # 不验签,返回 server_time 供校时
GET  https://<园区域名>/api/open/v1/erp/heartbeat         # 验签,body 为空串,证明当前凭证连接有效
```

每个请求四个头:

| 头 | 说明 |
|---|---|
| `X-App-Id` | 园区签发,形如 `wh_xxxxxxxxxxxx` |
| `X-Timestamp` | Unix 秒。与园区服务器相差 **> 300 秒**拒绝(先调 `/ping` 校时) |
| `X-Nonce` | 随机串(≤ 64 字符),**5 分钟内不得重复**,重复返回 `NONCE_REPLAY` |
| `X-Signature` | `hex( HMAC-SHA256( secret, X-Timestamp + "\n" + X-Nonce + "\n" + body ) )`,大小写不敏感 |

`body` 是**原始请求体字符串**,签名后不要再改动(不要格式化、不要重排字段)。GET `/contract` 和 `/heartbeat` 的 body 按空串参与签名。匿名 `/ping` 仅供校时和 HTTP 可达检查，不会更新任何云仓连接状态。

```python
# Python 示例
import hmac, hashlib, time, uuid, json, requests
secret = "..."; app_id = "wh_..."
body = json.dumps(event, ensure_ascii=False, separators=(",", ":"))
ts = str(int(time.time())); nonce = uuid.uuid4().hex
sig = hmac.new(secret.encode(), f"{ts}\n{nonce}\n{body}".encode(), hashlib.sha256).hexdigest()
requests.post(url, data=body.encode(), headers={
    "Content-Type": "application/json", "X-App-Id": app_id,
    "X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig})
```

## 3. 事件体

```json
{
  "event": "order.shipped",
  "order_no": "OUT20260918000123",
  "parent_order_no": null,
  "occurred_at": "2026-09-18T10:20:00+08:00",
  "warehouse_code": "WH-HZ-001",
  "customer_code": "HZ-C0021",
  "qty": 3,
  "packages": 1,
  "fee": { "storage": 0, "handling": 4.5, "express": 8.2 },
  "goods_amount": 1180.00,
  "logistics": { "carrier": "SF", "tracking_no": "SF123456789" },
  "items": [ { "sku": "P001", "name": "...", "category": "home", "qty": 3 } ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `event_id` | 建议显式提供 | 原始事件唯一标识；补映射、网络重发仍使用同一 ID 和同一内容，不能为重试伪造新事件 |
| `event` | ✓ | `order.created` `order.paid` `order.shipped` `order.returned` `order.refunded` `order.cancelled` |
| `order_no` | ✓ | 出库单号,云仓内唯一;拆单的子单各自一个单号,`parent_order_no` 填父单 |
| `occurred_at` | ✓ | ISO-8601 带时区 |
| `warehouse_code` | ✓ | 必须与 App-Id 对应的云仓编码一致,否则 `WH_MISMATCH` |
| `customer_code` | ✓ | 货主(园区客户)在你方 ERP 的编码，所有事件需提供 |
| `qty` / `packages` | shipped ✓ | 件数 / 包裹数。园区按客户合同单价表**自算服务费**:`perOrder × packages + perItem × qty`,不使用 `fee` |
| `logistics.tracking_no` | shipped ✓ | 缺则拒绝(防刷单) |
| `goods_amount` / `fee` / `items` | 可选 | 只做参考与对账 |

## 4. 事件语义

| 事件 | 园区动作 |
|---|---|
| `created` / `paid` | 只登记,不计佣 |
| `shipped` | 按货主编码匹配后与手工导入共用业务入口：园区签按客户定价产生冻结佣金；云仓直签仅记录运营出库，平台费到账另行计佣 |
| `returned` | 退货入库:基数为服务费时**不扣回**(服务已发生) |
| `refunded` | 服务费退款:扣回(未结算作废,已结算生负向流水) |
| `cancelled` | 取消:同 refunded |

- **幂等**:同一 `order_no` 的 `shipped` 重复推送返回 200 且不重复计佣。
- **乱序**：`refunded` / `cancelled` 即使先到也按云仓和出库单号记录终态；之后到达的发货不会生成订单佣金，手工补导也不能复活已撤单。确需重新履约应使用新的出库单号。
- **未映射**：发货返回 200、`attributed=false`、`status=WAITING_MAPPING`，事件保留待匹配，不能据此推定为自有客户。保存映射会排队补处理，亦可按原 `event_id` 原内容重发；创建、付款等非发货事件不会误入待匹配队列。

## 5. 响应

```json
200 {"ok":true,"order_id":123,"attributed":true}
200 {"ok":true,"order_id":null,"attributed":false,"status":"WAITING_MAPPING","sandbox":false}
200 {"ok":true,"order_id":null,"attributed":false,"sandbox":true,"code":"SANDBOX_VALIDATED"}
400 {"ok":false,"code":"SIGN_INVALID|APP_DISABLED|BAD_PAYLOAD|WH_MISMATCH|NONCE_REPLAY|BIZ","msg":"..."}
```

批量:`200 {"ok":true,"results":[{"order_no":"...","ok":true,...},{"order_no":"...","ok":false,"code":"...","msg":"..."}]}`,整批签名一次。

## 6. 重试建议

- 4xx 不要重试(修数据后再发);网络错误 / 5xx 指数退避重试,每次**换新 nonce、新时间戳、重新签名**。
- 每个 App-Id 建议不超过 600 次/分钟。

## 7. 排错

园区后台「ERP 对接 › 同步日志」按单号可查每次请求的结果码与说明(不存原始报文,只存 sha256 摘要)。常见:

| 结果码 | 原因 |
|---|---|
| `SIGN_INVALID` | 密钥不对 / body 被改动 / 时间戳超 300 秒 / 头缺失 |
| `NONCE_REPLAY` | 5 分钟内重复 nonce(重试时忘了换) |
| `WH_MISMATCH` | `warehouse_code` 与凭证所属云仓不一致 |
| `UNMAPPED`(200) | 货主编码未登记 |
| `BIZ` | 业务拒绝,如客户无生效合同、无推荐伙伴 |

## 8. 可靠性与补映射

- 正式有效事件按 `warehouse_id + app_id + event_id` 幂等；同 ID 内容变化会被拒绝。未提供 `event_id` 时沿用 `event:order_no:occurred_at` 回退键，推荐始终显式传入。
- 已排队的后台补处理失败会退避重试，达到上限需人工处理；原请求发生 4xx 应修复数据，网络错误/5xx 由 ERP 重发。待匹配不消耗失败重试次数。
- 后台新增“待处理事件”，补映射自动排队，人工重试必须填写原因。使用原事件、原报文，不要求 ERP 生成新的事件 ID。重放约每 3 分钟扫描一次；凭证停用或处于沙箱时不补记正式账务。
- `lastHeartbeatAt` 是最近验签通信时间；`lastSyncAt` 是最近正式业务同步时间。超过 6 小时无签名通信才显示断连，无订单但心跳正常不会断连。配置改变后旧心跳立即失效，等待重新验签。
- 连接正常仅表示当前凭证通信有效，不代表所有事件已经入账；应同时检查待处理事件和周期对账。

## 9. 周期对账与结算

当前提供人工录入 ERP 导出汇总的独立数据源核对流程，不假设任何第三方查询接口已经开放。

1. 在后台 ERP 对接页选择云仓和结算期间，录入 ERP 导出的有效出库单数量及来源/凭据说明。
2. 比较范围必须一致：本云仓、所选日期、系统登记客户范围的有效出库单；包含手工补单，排除已退款/取消的出库单。不是件数、包裹数，也不是包含未接入自有客户的全仓总量。
3. 页面预览系统数量、差异及待处理事件。完成客户匹配、重试、补单或撤单处理后重新计算；不以“系统数量等于系统数量”替代独立外部数据。
4. 保存草稿，零差异且没有待处理阻塞时确认。确认生成不可变版本；结算引用该版本。后续订单、映射或事件变化会使原确认失效，需要重新核对并确认。
5. 未付款结算可按现有复核流程说明原因并使用新的确认版本，已经付款的历史快照不覆盖。旧的金额差异 2% 规则不作为新数量对账的放行条件。

## 10. 发布与回归场景

后端需先执行 Flyway V90–V92，再部署管理后台和小程序（V89 已由云山日发货量功能占用）。迁移保留历史业务数据；历史未映射发货可通过保存映射或异常台重试，不自动把未知客户认定为园区客户。已有连接状态会重置为待验证，由新的正式签名心跳重新确认；历史财务记录不会自动重算。

上线联调至少覆盖：园区签/云仓直签、手工与 ERP 同单重复、缺映射后补齐、沙箱不入正式账、无订单但心跳正常、停用/换环境/重置密钥后旧连接失效、先撤单后发货、对账差异拦截、补单后旧对账确认失效、实际结算与付款。

## 11. 云仓发货量同步配置（云山协议）

此模块主动拉取每日发货票数，与上文 ERP 订单事件推送分开配置。

1. 在“客户与作业仓库”中登记客户实际使用的仓库。
2. 在“云仓发货量同步 → 配置接口同步”选择当前云仓的已签约客户。候选必须存在当前有效的生效、履约中或变更中合同（状态 4/5/6），并属于合同当前有效的承接仓库；草稿、待签、到期、终止、作废合同不开放新配置。园区签与云仓直签均按承接仓库关系筛选，支持多仓合同。
3. 自动带出该客户已启用的本地作业仓库；只有一个时自动关联，多个时由运营选择，没有建档关系时禁止保存。
4. 选择已有共享连接，或填写该客户的接口基础地址、App ID、RSA 公钥。当前仅支持云山协议，不能直接接入任意 ERP 协议。点击“验证接口并读取客户授权”，核对接口货主及接口仓库与本地客户、作业仓库的对应关系。
5. 保存后默认立即拉取最近 7 天（截至北京时间昨天）的数据，也可以取消即时同步。已启用配置继续参与每日定时同步。专用连接不依赖共享连接是否配置；合同失效或本地关联停用后拒绝继续同步，历史快照仍可查看。

部署需先执行 V94。客户专用 App ID、公钥使用现有 `ZHYQ_FIELD_ENCRYPTION_KEY`（兼容 `APP_ENCRYPTION_KEY`）加密保存，不回传明文，不进入业务审计内容。接口基础地址仅允许共享接口的来源，或管理员通过 `YUNSHAN_ALLOWED_ORIGINS` 添加的可信来源（如 `https://api.example.com`，多个用逗号分隔），不跟随重定向。新增来源须与接口提供方核实。

V94 不覆盖历史映射和发货量快照；历史映射继续使用共享连接，列表标识尚未关联本地仓库。原 `/yunshan/customers` 查询现在必须传入 `warehouseId`；新增映射必须传入 `operationWarehouseId` 和 `connection`，保存返回映射 ID，用于后续单独执行同步。保存成功和获取数据成功是两个独立结果，拉取失败可查看同步日志并补拉。
