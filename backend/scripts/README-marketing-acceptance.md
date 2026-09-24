# 本地营销闭环验收

这三个 Python 3 脚本仅依赖标准库，目标 URL 强制限制为 loopback。必须使用全新的隔离测试数据库及独立后端，禁止将生产数据库映射到本地端口后运行。脚本会创建测试账号、合同、模拟凭证和资金记录，并修改该测试库的评级配置。

先按项目后端配置启动独立实例并完成 Flyway 迁移，再在环境变量中提供：

- `BASE_URL`：例如 `http://127.0.0.1:19092/api`。
- `ADMIN_USERNAME`、`ADMIN_PASSWORD`：该隔离库的管理员凭据。
- `FLOW_OUTPUT`：仅供本次验收使用的本机目录；默认系统临时目录下 `zhyq-marketing-acceptance`。

依次执行：

```sh
python3 backend/scripts/marketing_flow.py --stage all
python3 backend/scripts/marketing_extra.py
python3 backend/scripts/marketing_final_checks.py
```

输出目录中的 `flow-report.json` 只包含检查名，可用于验收。`flow-state.json` 与 `http-state.json` 含测试登录凭据，创建时即设置为 0600，不能提交或分享。新一轮完整验收请使用新的输出目录和隔离数据库；增量恢复使用同一目录。

覆盖注册、云仓人工模式上线、推荐归属、分派承接、园区签和直签合同、出库导入、服务费收款、成本结算、固定月费、实名资料人工审核、佣金结算、提现审核及凭证打款，并覆盖越权、幂等和真实并发互斥。
