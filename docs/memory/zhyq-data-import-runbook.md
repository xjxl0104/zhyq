# zhyq 线上数据导入 / 修补 SOP（附 2026-09-04 实战记录）

> 适用场景：代码走 git 自动上线，但**数据**在本地录、要搬到线上；或要批量修正线上某几行业务数据。
> 原则：账号表不动、已有数据不整表覆盖、每一步先备份再预演再上生产、全程留痕可回退。
> 服务器接入与部署机制见 `docs/运维手册-服务器与部署.md`。

## 一、线上库长什么样、怎么进

- MySQL 跑在容器 `zhyq-mysql` 里，**没有发布宿主端口**，宿主机 3306 不监听，只有 `zhyq-edge` 网络内的后端能连。
- 改库只有三条路：① 业务数据走网页（登记表导入/编辑、收银台、合同），这是正路；② `ssh aole` 后 `docker exec zhyq-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 zhyq_park'`；③ 本机 SSH 隧道到容器 IP 后用 GUI。**3306 不要对公网开放。**
- 客户端一定带 `--default-character-set=utf8mb4`，否则 `-e` 里的中文字面量（`LIKE '%昌泰%'`）会因编码不符匹配到 0 行，看起来像"数据没了"。
- 服务器目录约定（均未跟踪、已入 `.gitignore`，`deploy.sh` 只做 `git checkout -f` 不做 `git clean`，不会被部署清掉）：
  - `/opt/zhyq/backups/` 整库备份 `zhyq_park-<时间>-<用途>.sql.gz`
  - `/opt/zhyq/import/` 待导入的数据包、说明文档、改写后的脚本

## 二、标准流程（每次都走完，不跳步）

1. **先看代码是否已上线到数据包要求的 Flyway 版本**：`SELECT MAX(installed_rank), version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1`。旧结构配新数据会缺列。
2. **收到 SQL 先读，不先跑**，逐条核对：
   - 有没有 `CREATE DATABASE` / `USE` / `DROP TABLE`（全量包全有，等于整库替换）；
   - 是否包含 `sys_user` / `sys_role*` / `sys_menu` / `flyway_schema_history`（账号、权限、迁移记录，一律不能覆盖）；
   - 是纯 `INSERT … WHERE NOT EXISTS`（可追加）还是 `UPDATE`（改已有数据，需负责人逐项确认）；
   - **写死的 id**：`project_id` / `space_id` / `room_id` 在本地和线上完全不同（本地 id 1 是园区根，线上 id 1 是已删除的演示园区），必须逐个对照线上 `biz_project` / `sys_space` / `biz_room` 改成线上的值，或改成按名字子查询；
   - 排序规则：线上库是 `utf8mb4_unicode_ci`，本地 MySQL 8 导出多半是 `utf8mb4_general_ci`（V51 新表也是 general_ci），跨库 JOIN 会报 `Illegal mix of collations`，把临时库的表 `ALTER TABLE … CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci` 后再比。
3. **整库备份**：
   ```bash
   cd /opt/zhyq && OUT=backups/zhyq_park-$(date +%Y%m%d-%H%M%S)-<用途>.sql.gz
   docker exec zhyq-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF --default-character-set=utf8mb4 zhyq_park' | gzip -6 > "$OUT" && gzip -t "$OUT"
   ```
4. **预演库**：`CREATE DATABASE zhyq_park_rehearsal`，把上一步的备份灌进去，脚本先对它跑**两遍**（第二遍验证幂等），看核对查询，再对 `zhyq_park` 跑一遍。用完 `DROP DATABASE`。
5. **上生产后立刻对账**：账单/收款/用户/合同/登记表的 `COUNT(*)` 与预期逐项对；改过的行把改前值留在 `bak_<主题>_<日期>` 表里。
6. 登记表新增/改动后，账单不会立刻变：`ReceivableBillSyncJob` 是启动后 2 分钟跑一次、之后每 24 小时一次；要马上出账就在页面点该行「生成账单」（幂等，已收款不覆盖）。

## 三、2026-09-04 实战记录（财务版块上线 + 本地数据搬线上）

**背景**：财务批次代码（PR #22，Flyway V49–V51）合入 main；数据在开发机本地库录的，负责人给了全量包、增量包和说明文档，要求**不动线上账号、不动线上已有数据**。说明文档假设线上是空库，实际线上已有 9 条已确认登记 / 80 张账单 / 10 笔收款，所以全量包一开始就排除。

**代码侧插曲**：main@38aec46 服务器构建失败——PR#22 改了 3 个类的构造器和 1 个 record，5 个测试文件没跟着改；Dockerfile 跑 `mvn clean package -DskipTests`，测试源码照样编译。本机自查用的 `mvn compile` 不编译测试所以全绿。已修（7b0a073）并把 CLAUDE.md 自查命令改为 `mvn -B test-compile`。旧容器全程未被替换。

**数据侧做了什么**（每步之前一份备份，共 4 份在 `backups/`）：

| 步骤 | 内容 | 与原包的差异 |
|---|---|---|
| 云帕新增 | 租户档案 + 合同 HT2026-YP-001 + 登记（序号 10，CONFIRMED）+ 合同-房间关联 | 原包 `project_id=1`、`space_id=1` 在线上都是已删除演示节点，改为 `project_id=3`（DI PARK数智云仓产业园）、`room_id=20`（DIPARK第一层防火分区一），与其余登记同为房间绑定 |
| 昌泰修正 | 登记表 1 行：月租金/物业费/合计 56400/9447/65847 → 47600/9400/57000，保证金 378000 → 11400，单价口径由"每日 0.4 元"改"每月 10.12 元" | 负责人确认后才执行；合同 HT2026-CT-001 当时线上不存在、已实收的 378000 保证金账单未动，原值在 `bak_delta_20260904_register/_bill` |
| 8 份合同 | 本地 2026-09-03 由登记表生成的 HT2026-LWN/CT/ZY/WX-001、YS-001..004 原样搬上线（负责人要求以本地这 8 条为准），并把 9 条登记挂上 `contract_id`、补 8 行 `biz_contract_room` | 跨库 `INSERT … SELECT`，租户按名字对应、`project_id=3`；鑫晨那条登记本地没有合同，当时未挂接（**2026-09-12 已补，见第四节**） |

**结果**：合同 12 份、登记 10 条（9 条已挂合同）、账单 88 张（80 张旧 + 云帕 8 张，负责人在页面点「生成账单」产生）、收款 10 笔、用户未变。脚本留在服务器 `import/`（`delta_yunpa_prod_20260904.sql`、`delta_part2_changtai_updates.sql`、`contracts8_from_local_20260904.sql`），均幂等。

**没搬的**：本地当天下午李万能的 4 笔收款——资金记录由负责人在线上收银台重收，不用 SQL 造。

**教训**：
- "不写死 id" 的脚本也要逐个核对 `project_id/space_id/room_id`；
- 自查必须 `test-compile`；
- 数据包、备份含账号哈希/银行账号，**只留服务器，不进仓库**（`.gitignore` 已加 `backups/`、`import/`）；
- 以后业务数据直接在线上录，本地库只做开发测试，两边就不会再岔开。

## 四、2026-09-12 实战记录（鑫晨补合同）

**起因**：负责人本地测智能表计页，鑫晨云仓的 4 块表（SB-1720/SB-1724 水、DB-5-02-01/DB-5-03-01 电，全挂房 21）租户列是「-」。表计租户按「房间 → 执行中合同(status=5) → 租客」反查（`MeterController`），而登记表 3（广州鑫晨供应链有限公司，协议 GZCJ-(DI PARK)-20260713-002）**从没建过合同**——09-03 那批 8 份合同漏了它，且它的 `room_id=12` 挂的是昌泰的房（两条登记的楼层都叫「DIPARK第五层」，撞名）。本地与生产数据一致。负责人拍板「在合同中增加这个」。

**依据**：房 21「DIPARK第五层储存区二及储存区三」租赁面积 9000㎡ = 登记表计费面积 9000㎡；4 块表计本来就挂 21；房 12 是昌泰的 4700㎡、已有 HT2026-CT-001。

**做了什么**（脚本 `import/delta-20260912-xinchen-contract.sql`，幂等；先 `mysqldump` 五张表到 `backups/pre-delta-20260912-xinchen-*.sql`，另建 `bak_delta_20260912_register/_room/_bill`）：

| 表 | 动作 |
|---|---|
| `biz_contract` | 新增 HT2026-XC-001（id 21）：租户 7、`project_id=3`、status 5、2026-08-01 ~ 2032-07-31、签约 2026-07-13（取协议编号日期）、租金 13/物业 2 元·㎡·月、面积 9000、保证金 270000（=234000+36000）、免租 4 个月、每 3 年递增 10%，`source=应收明细登记表`，`create_by=delta-20260912` |
| `biz_contract_room` | 合同 21 ↔ 房 21，面积 9000、单价 13 |
| `biz_room` 21 | 状态 1(可租) → 5(在租) |
| `fin_receivable_register` 3 | `room_id` 12 → 21，`contract_id` NULL → 21 |
| `fin_bill`（登记 3 的 6 张） | `room_id` 12 → 21、`contract_id` → 21；金额/实收一分未动（保证金两张已实收 270000） |

**验证**：与 `MeterController` 同款 JOIN 查 4 块表计全部命中「广州鑫晨供应链有限公司」；本地页面实测租户列已显示；生产 `SELECT` 终态与本地一致（合同 13→14、关联 13→14）。

**回滚**：`DELETE` 合同 21 与其 `biz_contract_room`；`biz_room`/登记/账单三张按 `bak_delta_20260912_*` 回写。

**教训**：登记表按楼层名挂房间会撞名（第五层两家），挂房时要拿**面积**再核一次。

## 五、2026-09-12 实战记录（能耗口径：园区总表 SB-4475 改参考表）

**起因**：能耗统计「用水概览·当年用量」显示 2284.5，负责人口径 8 月是 **1012**（总表），租户分表 562.5、公摊 449.5。库里水表有两块 MAIN：大厦总表 1012、园区总表 SB-4475 710，旧概览把总表+分表+参考表全加了（1012+562.5+710）。

**代码**（PR `fix/energy-stats-main-meter-scope`）：概览/趋势改成总表口径（总 = MAIN 读数；有总表时拆 租户/物业/公摊 = 总−租户−物业；无总表读数退分表合计并标 hasMain=false）；排行不含总表；新增表计角色 **REFERENCE 参考表**（只记读数，不进统计与公摊）；公摊分表查询改为只认 TENANT/PROPERTY。回归锁 `EnergyStatsScopeTest`。

**数据**（脚本 `import/delta-20260912-water-reference-meter.sql`，幂等；`mysqldump eng_meter` 到 `backups/pre-delta-20260912-water-ref-*.sql`，另建 `bak_delta_20260912_meter`）：`eng_meter` SB-4475 `meter_role` MAIN → REFERENCE。本地与生产均已落（生产 13:50）。

**验证**：本地 overview 接口 water.year=1012 / yearTenant=562.5 / yearPublic=449.5 / yearHasMain=true；electric.year=128598、yearHasMain=false（园区电表没装总表，退分表合计）；页面同显。

**回滚**：`UPDATE eng_meter SET meter_role='MAIN' WHERE code='SB-4475'`（或按 `bak_delta_20260912_meter` 回写）。

**负责人拍板（2026-09-12 14:20）**：「园区水表有两个，计算总表时候以大厦为准」——大厦总表（SB-大厦总表）是发票总表 MAIN，园区总表 SB-4475 只作参考表，能耗统计与公摊都只看大厦总表。以后别再把 SB-4475 改回 MAIN（系统也会拦）。
