# 云仓 ERP 接入契约 v1.0

> 给加盟云仓技术人员。园区侧实现见 `backend/src/main/java/com/zhyq/park/marketing/open/`;业务规则见 `PARK-MKT-001-开发方案-V1.2.md` §2.6 / §5.3。
> 当前只实现 **webhook 推送**(云仓主动推事件给园区)。拉取模式与文件模式暂不开放。

## 1. 接入流程

1. 园区运营在后台「全民营销 › ERP 对接」为你的云仓**签发沙箱凭证**,把 `App-Id` 与 `Secret` 交给你(Secret 只显示一次,丢了只能重置)。
2. 运营为每个客户录入你们 ERP 里的**货主编码**(`customer_code`)。订单归属只认这个编码,不认收件人手机号。
3. 你方对接口发 3 张测试单(`shipped` / `refunded` / `cancelled` 各一),运营在日志里核对。
4. 通过后运营把凭证**切正式**,同一 App-Id 与 Secret 不变。

## 2. 地址与鉴权

```
POST https://<园区域名>/api/open/v1/erp/order-event
POST https://<园区域名>/api/open/v1/erp/orders/batch      # ≤ 500 条
GET  https://<园区域名>/api/open/v1/erp/contract          # 需验签,body 为空串
GET  https://<园区域名>/api/open/v1/erp/ping              # 不验签,返回 server_time 供校时
```

每个请求四个头:

| 头 | 说明 |
|---|---|
| `X-App-Id` | 园区签发,形如 `wh_xxxxxxxxxxxx` |
| `X-Timestamp` | Unix 秒。与园区服务器相差 **> 300 秒**拒绝(先调 `/ping` 校时) |
| `X-Nonce` | 随机串(≤ 64 字符),**5 分钟内不得重复**,重复返回 `NONCE_REPLAY` |
| `X-Signature` | `hex( HMAC-SHA256( secret, X-Timestamp + "\n" + X-Nonce + "\n" + body ) )`,大小写不敏感 |

`body` 是**原始请求体字符串**,签名后不要再改动(不要格式化、不要重排字段)。GET `/contract` 的 body 按空串参与签名。

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
| `event` | ✓ | `order.created` `order.paid` `order.shipped` `order.returned` `order.refunded` `order.cancelled` |
| `order_no` | ✓ | 出库单号,云仓内唯一;拆单的子单各自一个单号,`parent_order_no` 填父单 |
| `occurred_at` | ✓ | ISO-8601 带时区 |
| `warehouse_code` | ✓ | 必须与 App-Id 对应的云仓编码一致,否则 `WH_MISMATCH` |
| `customer_code` | shipped ✓ | 货主(园区客户)在你方 ERP 的编码 |
| `qty` / `packages` | shipped ✓ | 件数 / 包裹数。园区按客户合同单价表**自算服务费**:`perOrder × packages + perItem × qty`,不使用 `fee` |
| `logistics.tracking_no` | shipped ✓ | 缺则拒绝(防刷单) |
| `goods_amount` / `fee` / `items` | 可选 | 只做参考与对账 |

## 4. 事件语义

| 事件 | 园区动作 |
|---|---|
| `created` / `paid` | 只登记,不计佣 |
| `shipped` | **计佣事件**:按 `customer_code` 归属 → 生成佣金(冻结,发货 + 冻结天数后自动解冻) |
| `returned` | 退货入库:基数为服务费时**不扣回**(服务已发生) |
| `refunded` | 服务费退款:扣回(未结算作废,已结算生负向流水) |
| `cancelled` | 取消:同 refunded |

- **幂等**:同一 `order_no` 的 `shipped` 重复推送返回 200 且不重复计佣。
- **乱序**:`refunded` 先于 `shipped` 到达时,`refunded` 返回 200(无订单,不动作);之后 `shipped` 正常入账。请尽量按时间顺序推送。
- **未映射**:`customer_code` 没有在园区登记 → 返回 200 `attributed=false`,记日志不计佣。运营补映射后请**重发**该单。

## 5. 响应

```json
200 {"ok":true,"order_id":123,"attributed":true}
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

- 园区先把每条事件写入 Inbox，再按 `warehouse_id + app_id + event_id` 做幂等处理；没有 `event_id` 时使用报文摘要、订单号和事件类型组合键。
- 临时网络或数据库错误会进入重试队列，最多 5 次；超过次数进入死信。验签失败、字段错误、仓库不匹配等 4xx 不会自动重试。
- `attributed=false` 的事件会进入未映射队列。运营补录 `customer_code` 后，可在后台按原订单重放；重放仍以事件业务键幂等，不会重复生成订单或佣金。
- 云仓超过 6 小时没有合法事件或主动 ping 会显示断连；断连只阻止新的客户分配，不影响历史查询和已有结算快照。
- 对账差异低于 2% 可继续结算；达到或超过 2% 时相关结算批次冻结，需运营复核后处理。

沙箱测试接口只做契约校验和预览，不会进入正式 Inbox、订单、佣金或财务账单。正式事件仍必须使用本契约的 HMAC 头和原始 body 签名。
