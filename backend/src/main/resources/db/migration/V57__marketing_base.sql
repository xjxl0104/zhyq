-- =====================================================================
-- V57 全民营销(园区伙伴 · 云仓生态)· 阶段 A 基础表
-- 规范:docs/marketing/PARK-MKT-001-开发方案-V1.2.md §4.2
-- 落地差异:docs/superpowers/specs/2026-09-20-crm-marketing-design.md
--   · 编号从 V57 起(生产库 2026-09-20 查实 = V56)
--   · 不引入 Redis:并发靠唯一键兜底(手机号 / 邀请码 / 有效锁 / 佣金收款人)
--   · sys_audit 是通用审计表,其它模块以后可复用
-- 本迁移只建表、加列、插种子,不改任何已有数据;所有种子按 NOT EXISTS 幂等。
-- 全部新表带基座 8 列 + project_id(多园区;NULL = 全部园区)。
-- =====================================================================

-- ---------- 0) 通用审计 ----------
CREATE TABLE sys_audit (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    module      VARCHAR(32)  NOT NULL COMMENT '模块:crm.marketing 等',
    action      VARCHAR(64)  NOT NULL COMMENT '动作:promoter.parent.change / commission.void …',
    biz_type    VARCHAR(32)  NULL COMMENT '业务对象类型',
    biz_id      BIGINT       NULL COMMENT '业务对象 id',
    reason      VARCHAR(500) NULL COMMENT '操作原因(改上级/作废/调岗等必填)',
    before_json VARCHAR(4000) NULL COMMENT '变更前快照',
    after_json  VARCHAR(4000) NULL COMMENT '变更后快照',
    operator    VARCHAR(64)  NULL COMMENT '操作人',
    ip          VARCHAR(64)  NULL,
    project_id  BIGINT       NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version     INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_audit_biz (biz_type, biz_id),
    KEY idx_audit_time (module, create_time)
) COMMENT '通用业务审计';

-- ---------- 1) 岗位 / 评级 / 规则 ----------
CREATE TABLE crm_position (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(8)   NOT NULL COMMENT 'P1..P4',
    name            VARCHAR(32)  NOT NULL COMMENT '园区伙伴/银牌合伙人/金牌合伙人/钻石合伙人',
    sort            INT          NOT NULL DEFAULT 0,
    share_pct       INT          NOT NULL COMMENT '份额%:该岗位可拿总比例的百分比,顶格 100',
    share_min_pct   INT          NOT NULL COMMENT '钻石合伙人可下调的下限',
    lock_cap        INT          NOT NULL DEFAULT 50 COMMENT '锁定客户数上限(含预锁)',
    promote_amount  DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '晋升门槛:近12月成交额',
    promote_orders  INT          NOT NULL DEFAULT 0 COMMENT '晋升门槛:近12月成交单数',
    team_counted    TINYINT      NOT NULL DEFAULT 0 COMMENT '门槛是否计直属团队成交',
    demote_enabled  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否允许自动降级',
    status          TINYINT      NOT NULL DEFAULT 1,
    project_id      BIGINT       NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    create_by       VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version         INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_position_code (code)
) COMMENT '伙伴岗位与份额';

CREATE TABLE crm_position_override (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_promoter_id   BIGINT      NOT NULL COMMENT '钻石合伙人(顶格岗位)本人',
    position_code       VARCHAR(8)  NOT NULL,
    share_pct           INT         NOT NULL COMMENT '下调后的份额,share_min_pct ≤ 值 ≤ 默认',
    project_id          BIGINT      NULL,
    tenant_id           BIGINT      NOT NULL DEFAULT 1,
    create_by           VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version             INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_position_override (owner_promoter_id, position_code)
) COMMENT '钻石合伙人团队份额覆盖';

CREATE TABLE crm_position_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    promoter_id  BIGINT      NOT NULL,
    from_code    VARCHAR(8)  NULL,
    to_code      VARCHAR(8)  NOT NULL,
    reason       VARCHAR(16) NOT NULL COMMENT 'auto 自动复核 / manual 后台手动',
    operator     VARCHAR(64) NULL,
    note         VARCHAR(500) NULL,
    project_id   BIGINT      NULL,
    tenant_id    BIGINT      NOT NULL DEFAULT 1,
    create_by    VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version      INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_position_history (promoter_id, create_time)
) COMMENT '岗位变更史';

CREATE TABLE crm_customer_grade (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                     VARCHAR(4)   NOT NULL COMMENT 'A/B/C/D',
    name                     VARCHAR(32)  NOT NULL,
    descr                    VARCHAR(255) NULL,
    lease_commission_months  DECIMAL(6,2) NOT NULL DEFAULT 0.5 COMMENT '园区入驻:佣金池 = 月租金 × 本值(0.25–2)',
    erp_total_rate           DECIMAL(6,2) NOT NULL DEFAULT 3 COMMENT '客户入仓:出库单/平台费的总比例%',
    service_total_rate       DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '增值服务总比例%(预留)',
    contract_bonus           DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '入仓合同签约奖(固定额,可为 0)',
    auto_min_rent            DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '评级自动建议:租赁线月租金下限',
    auto_min_orders          INT          NOT NULL DEFAULT 0 COMMENT '评级自动建议:云仓线预计月出库单量下限',
    sort                     INT          NOT NULL DEFAULT 0,
    status                   TINYINT      NOT NULL DEFAULT 1,
    project_id               BIGINT       NULL,
    tenant_id                BIGINT       NOT NULL DEFAULT 1,
    create_by                VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version                  INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_grade_code (code)
) COMMENT '客户评级与总比例';

CREATE TABLE crm_commission_rule (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(64)  NOT NULL,
    source_type          TINYINT      NOT NULL COMMENT '1租赁签约 2出库单 3平台费收款 4签约奖 5增值服务',
    category             VARCHAR(64)  NULL COMMENT '品类/需求类型限定,空=全部',
    base_mode            TINYINT      NOT NULL DEFAULT 1 COMMENT '1园区服务费 2固定每单 3货值',
    fixed_per_order      DECIMAL(14,2) NULL COMMENT 'base_mode=2 时每单基数',
    freeze_days          INT          NULL COMMENT '覆盖冻结天数,空=用规则参数',
    grade_rate_override  JSON         NULL COMMENT '按评级覆盖总比例 {"A":8,"B":6}',
    effective_from       DATE         NULL,
    effective_to         DATE         NULL,
    status               TINYINT      NOT NULL DEFAULT 1,
    project_id           BIGINT       NULL,
    tenant_id            BIGINT       NOT NULL DEFAULT 1,
    create_by            VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version              INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_commission_rule (source_type, project_id, status)
) COMMENT '佣金规则(覆盖评级默认)';

-- ---------- 2) 伙伴 ----------
CREATE TABLE crm_promoter (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid             VARCHAR(64)  NULL COMMENT '微信 openid(后台手工录入的伙伴为空)',
    unionid            VARCHAR(64)  NULL,
    phone              VARCHAR(20)  NOT NULL,
    name               VARCHAR(64)  NULL,
    avatar             VARCHAR(255) NULL,
    invite_code        VARCHAR(8)   NOT NULL COMMENT '8 位,去掉 0/O/1/I',
    parent_id          BIGINT       NULL COMMENT '上级,终身绑定,只有运营可改',
    path               VARCHAR(512) NOT NULL DEFAULT '/' COMMENT '物化路径 /根id/…/自己id/',
    position_code      VARCHAR(8)   NOT NULL DEFAULT 'P1',
    position_since     DATETIME     NULL,
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2冻结 3待审核 4已退出',
    is_internal        TINYINT      NOT NULL DEFAULT 0 COMMENT '手机号命中 sys_user → 内部人员,默认不计佣',
    agreement_version  VARCHAR(16)  NULL,
    agreed_at          DATETIME     NULL,
    id_verified        TINYINT      NOT NULL DEFAULT 0,
    bind_time          DATETIME     NULL COMMENT '绑定上级时间',
    invite_deadline    DATETIME     NULL COMMENT '无邀请码注册后可补填的截止时间',
    last_login         DATETIME     NULL,
    register_ip        VARCHAR(64)  NULL,
    device_id          VARCHAR(64)  NULL,
    source             VARCHAR(16)  NOT NULL DEFAULT 'mp' COMMENT 'mp 小程序 / admin 后台录入',
    remark             VARCHAR(255) NULL,
    project_id         BIGINT       NULL,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    create_by          VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version            INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_promoter_openid (openid),
    UNIQUE KEY uk_promoter_phone (phone),
    UNIQUE KEY uk_promoter_invite (invite_code),
    KEY idx_promoter_parent (parent_id),
    KEY idx_promoter_path (path(191)),
    KEY idx_promoter_status (status, position_code)
) COMMENT '园区伙伴';

CREATE TABLE crm_promoter_account (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    promoter_id     BIGINT       NOT NULL,
    real_name       VARCHAR(64)  NULL,
    id_no_enc       VARCHAR(255) NULL COMMENT '身份证号 AES-GCM 密文',
    account_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '1微信 2银行卡 3支付宝',
    account_no_enc  VARCHAR(255) NULL COMMENT '收款账号 AES-GCM 密文',
    account_tail    VARCHAR(8)   NULL COMMENT '账号尾号,列表脱敏展示用',
    bank_name       VARCHAR(64)  NULL,
    verified_at     DATETIME     NULL,
    project_id      BIGINT       NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    create_by       VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version         INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_promoter_account (promoter_id)
) COMMENT '伙伴实名与收款账户';

-- ---------- 3) 云仓 ----------
CREATE TABLE crm_warehouse (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(32)  NOT NULL COMMENT 'WH-HZ-001',
    name                VARCHAR(128) NOT NULL,
    region              VARCHAR(64)  NULL,
    address             VARCHAR(255) NULL,
    contact             VARCHAR(64)  NULL,
    phone               VARCHAR(20)  NULL,
    contact_openid      VARCHAR(64)  NULL COMMENT '云仓端小程序登录用(阶段 C/D)',
    area_sqm            DECIMAL(12,2) NULL,
    daily_capacity      INT          NULL COMMENT '日处理单量',
    categories          VARCHAR(255) NULL,
    join_status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1申请 2资质审核 3ERP对接中 4待签协议 5已上线 6暂停 7退出',
    erp_status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0未对接 1已联通(沙箱) 2已联通(正式) 3断连',
    erp_marked_by       VARCHAR(64)  NULL COMMENT '阶段 A 人工标记已联通的运营',
    erp_marked_at       DATETIME     NULL,
    rating              DECIMAL(3,1) NULL,
    settle_cycle        TINYINT      NOT NULL DEFAULT 3 COMMENT '1周 2半月 3月',
    fee_model           JSON         NULL COMMENT '云仓费率表(园区应付) {"perOrder":x,"perItem":y,"storage":z}',
    platform_fee_model  JSON         NULL COMMENT '直签平台费 {"mode":"perOrder|ratio","value":x}',
    contract_file       VARCHAR(255) NULL COMMENT '加盟协议签署件',
    remark              VARCHAR(500) NULL,
    project_id          BIGINT       NULL,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    create_by           VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version             INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_warehouse_code (code),
    KEY idx_warehouse_status (join_status, erp_status)
) COMMENT '加盟云仓';

CREATE TABLE crm_warehouse_onboarding (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id   BIGINT       NOT NULL,
    step           TINYINT      NOT NULL COMMENT '1申请 2资质审核 3ERP打通 4签协议 5上线',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1进行中 2通过 3驳回',
    owner_id       BIGINT       NULL COMMENT '负责人 sys_user.id',
    deadline       DATE         NULL,
    done_time      DATETIME     NULL,
    reject_reason  VARCHAR(500) NULL,
    attachments    JSON         NULL,
    project_id     BIGINT       NULL,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_onboarding_step (warehouse_id, step)
) COMMENT '云仓加盟步骤';

-- ---------- 4) 客户锁定 / 合同 ----------
CREATE TABLE crm_customer_lock (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id      BIGINT       NOT NULL,
    promoter_id      BIGINT       NOT NULL,
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1预锁 2有效锁定 3已成交 4已释放',
    -- 生成列:预锁/有效锁定期间 = customer_id,其余 NULL;唯一键保证一个客户同时只有一把活锁,
    -- 两人并发报备时后到者直接撞键,不需要 Redis 锁
    active_key       BIGINT AS (IF(status IN (1, 2), customer_id, NULL)) STORED,
    prelock_until    DATETIME     NULL,
    lock_until       DATETIME     NULL,
    confirmed_by     VARCHAR(64)  NULL,
    confirmed_at     DATETIME     NULL,
    extended_count   INT          NOT NULL DEFAULT 0,
    extend_reason    VARCHAR(500) NULL,
    released_reason  VARCHAR(500) NULL,
    released_by      VARCHAR(64)  NULL,
    released_at      DATETIME     NULL,
    project_id       BIGINT       NULL,
    tenant_id        BIGINT       NOT NULL DEFAULT 1,
    create_by        VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version          INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_lock_active (active_key),
    KEY idx_lock_promoter (promoter_id, status),
    KEY idx_lock_customer (customer_id, status)
) COMMENT '伙伴锁客(报备两段式)';

CREATE TABLE crm_contract_template (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(64)  NOT NULL,
    service_type  TINYINT      NOT NULL COMMENT '1仓储 2代发 3仓配',
    tpl_version   INT          NOT NULL DEFAULT 1 COMMENT '模板版本号(基座 version 是乐观锁,不混用)',
    body          MEDIUMTEXT   NULL,
    variables     JSON         NULL,
    status        TINYINT      NOT NULL DEFAULT 1,
    project_id    BIGINT       NULL,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_contract_template (name, tpl_version)
) COMMENT '云仓服务合同模板';

CREATE TABLE crm_service_contract (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_no       VARCHAR(32)  NOT NULL COMMENT 'CS-yyyyMM-0001',
    sign_mode         TINYINT      NOT NULL DEFAULT 1 COMMENT '1园区签 2云仓直签(合同期内快照)',
    customer_id       BIGINT       NOT NULL,
    warehouse_id      BIGINT       NULL COMMENT '主仓(冗余,明细在 crm_contract_warehouse)',
    partner_id        BIGINT       NULL COMMENT '成交伙伴(推荐人快照)',
    grade             VARCHAR(4)   NULL COMMENT '评级快照',
    service_type      TINYINT      NOT NULL DEFAULT 2 COMMENT '1仓储 2代发 3仓配',
    fee_model         TINYINT      NOT NULL DEFAULT 2 COMMENT '1仓租 2单票 3按件 4包月',
    price_table       JSON         NULL COMMENT '{"perOrder":x,"perItem":y,"storage":z,"monthly":m}',
    deposit           DECIMAL(14,2) NOT NULL DEFAULT 0,
    start_date        DATE         NULL,
    end_date          DATE         NULL,
    auto_renew        TINYINT      NOT NULL DEFAULT 0,
    pay_cycle         TINYINT      NOT NULL DEFAULT 3 COMMENT '1周 2半月 3月',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '1草稿 2待审核 3待客户签 4已生效 5履约中 6变更中 7到期 8终止 9作废',
    sign_method       TINYINT      NULL COMMENT '1线下上传 2电子签',
    signed_at         DATETIME     NULL,
    effective_at      DATETIME     NULL,
    contract_version  INT          NOT NULL DEFAULT 1 COMMENT '合同版本(变更/续签 +1)',
    template_id       BIGINT       NULL,
    files             JSON         NULL COMMENT '签署件等附件 id 列表',
    audit_reason      VARCHAR(500) NULL,
    terminate_reason  VARCHAR(500) NULL,
    remark            VARCHAR(500) NULL,
    project_id        BIGINT       NULL,
    tenant_id         BIGINT       NOT NULL DEFAULT 1,
    create_by         VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version           INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_service_contract_no (contract_no),
    KEY idx_service_contract_customer (customer_id),
    KEY idx_service_contract_warehouse (warehouse_id),
    KEY idx_service_contract_status (status, end_date)
) COMMENT '云仓服务合同';

CREATE TABLE crm_service_contract_version (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id   BIGINT      NOT NULL,
    ver_no        INT         NOT NULL,
    snapshot      JSON        NOT NULL,
    changed_by    VARCHAR(64) NULL,
    change_note   VARCHAR(500) NULL,
    project_id    BIGINT      NULL,
    tenant_id     BIGINT      NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_service_contract_version (contract_id, ver_no)
) COMMENT '云仓服务合同版本快照';

CREATE TABLE crm_contract_warehouse (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id     BIGINT   NOT NULL,
    warehouse_id    BIGINT   NOT NULL,
    is_primary      TINYINT  NOT NULL DEFAULT 1,
    effective_from  DATE     NOT NULL,
    effective_to    DATE     NULL COMMENT '换仓时旧仓写切换日',
    project_id      BIGINT   NULL,
    tenant_id       BIGINT   NOT NULL DEFAULT 1,
    create_by       VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version         INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_contract_warehouse (contract_id, warehouse_id, effective_from)
) COMMENT '合同 ↔ 承接云仓(可多仓、可换仓)';

-- ---------- 5) 计佣事件 / 佣金流水 / 结算 / 提现 ----------
CREATE TABLE crm_referral_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_type     TINYINT      NOT NULL COMMENT '1租赁签约(一次性) 2出库单 3平台费收款 4签约奖 5增值服务收款',
    source_no       VARCHAR(64)  NOT NULL COMMENT '业务单号:LEASE-{合同id} / 出库单号 / …',
    parent_order_no VARCHAR(64)  NULL COMMENT '拆单子单的父单号',
    source_id       BIGINT       NULL COMMENT '来源记录 id(biz_contract.id / crm_service_contract.id)',
    warehouse_id    BIGINT       NULL,
    promoter_id     BIGINT       NOT NULL COMMENT '成交伙伴',
    customer_id     BIGINT       NULL,
    customer_code   VARCHAR(64)  NULL COMMENT '货主在云仓 ERP 的编码(阶段 C)',
    customer_grade  VARCHAR(4)   NULL COMMENT '评级快照',
    pool_factor     DECIMAL(8,4) NOT NULL DEFAULT 0 COMMENT '租赁=佣金月数;入仓=总比例%',
    base_mode       TINYINT      NOT NULL DEFAULT 1 COMMENT '1园区服务费 2固定每单 3货值 4月租金',
    base_amount     DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '计佣基数:月租金 / 园区服务费 / 平台费',
    pool_amount     DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '佣金池 = 基数 × pool_factor',
    qty             INT          NULL,
    packages        INT          NULL,
    service_fee     DECIMAL(14,2) NULL COMMENT '园区按合同单价表自算的服务费',
    goods_amount    DECIMAL(14,2) NULL COMMENT 'ERP 传来的货值,仅参考',
    logistics_no    VARCHAR(64)  NULL,
    status          TINYINT      NOT NULL DEFAULT 2 COMMENT '1待确认 2已确认 3已退款 4已取消 5无归属',
    event_time      DATETIME     NULL,
    confirm_time    DATETIME     NULL,
    raw_payload     JSON         NULL,
    remark          VARCHAR(500) NULL,
    project_id      BIGINT       NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    create_by       VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version         INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_referral_order_source (source_type, source_no),
    KEY idx_referral_order_promoter (promoter_id, status),
    KEY idx_referral_order_customer (customer_id),
    KEY idx_referral_order_wh_time (warehouse_id, event_time)
) COMMENT '计佣事件';

CREATE TABLE crm_promoter_commission (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    referral_order_id  BIGINT       NOT NULL,
    promoter_id        BIGINT       NOT NULL COMMENT '收款人',
    position_code      VARCHAR(8)   NOT NULL COMMENT '收款人岗位快照',
    share_pct          INT          NOT NULL COMMENT '岗位份额快照',
    diff_pct           INT          NOT NULL COMMENT '级差 = share − 下级已拿',
    rule_id            BIGINT       NULL,
    base_amount        DECIMAL(14,2) NOT NULL DEFAULT 0,
    rate               DECIMAL(8,4) NOT NULL DEFAULT 0 COMMENT '实际比例% = 总比例 × diff/100(租赁为 月数×diff/100)',
    amount             DECIMAL(14,2) NOT NULL COMMENT '正向为佣金,负向为扣回',
    sign               TINYINT      NOT NULL DEFAULT 1 COMMENT '1正向 -1扣回',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '1冻结 2可结算 3已结算 4已提现 5作废',
    unfreeze_at        DATETIME     NULL COMMENT '路径 B 自动解冻时间;路径 A 为空,靠到账事件',
    settle_batch_id    BIGINT       NULL,
    withdrawal_id      BIGINT       NULL,
    void_reason        VARCHAR(500) NULL,
    project_id         BIGINT       NULL,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    create_by          VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version            INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_commission_order_payee (referral_order_id, promoter_id, sign),
    KEY idx_commission_promoter (promoter_id, status),
    KEY idx_commission_unfreeze (status, unfreeze_at)
) COMMENT '佣金流水';

CREATE TABLE crm_settle_batch (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no      VARCHAR(32)  NOT NULL COMMENT 'SB-yyyyMMdd-0001',
    cnt           INT          NOT NULL DEFAULT 0,
    total_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,
    operator      VARCHAR(64)  NULL,
    remark        VARCHAR(255) NULL,
    project_id    BIGINT       NULL,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_settle_batch_no (batch_no)
) COMMENT '佣金结算批次';

CREATE TABLE crm_withdrawal (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    withdrawal_no   VARCHAR(32)  NOT NULL COMMENT 'WD-yyyyMMdd-0001',
    promoter_id     BIGINT       NOT NULL,
    amount          DECIMAL(14,2) NOT NULL COMMENT '税前',
    tax_mode        TINYINT      NOT NULL DEFAULT 1 COMMENT '1个税代扣 2灵工平台代征',
    tax_amount      DECIMAL(14,2) NOT NULL DEFAULT 0,
    net_amount      DECIMAL(14,2) NOT NULL COMMENT '税后实付',
    pay_no          VARCHAR(64)  NULL COMMENT '打款幂等键',
    pay_method      TINYINT      NULL COMMENT '1线下 2微信商家转账',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1待审核 2已审核 3已打款 4已驳回',
    audit_by        VARCHAR(64)  NULL,
    audit_at        DATETIME     NULL,
    pay_by          VARCHAR(64)  NULL,
    pay_at          DATETIME     NULL,
    reject_reason   VARCHAR(500) NULL,
    commission_ids  JSON         NULL COMMENT '本次提现覆盖的流水 id',
    project_id      BIGINT       NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    create_by       VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version         INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_withdrawal_no (withdrawal_no),
    UNIQUE KEY uk_withdrawal_pay_no (pay_no),
    KEY idx_withdrawal_promoter (promoter_id, status)
) COMMENT '伙伴提现单';

-- ---------- 6) 老表加列 ----------
ALTER TABLE crm_lead
    ADD COLUMN referrer_id   BIGINT      NULL COMMENT '推荐伙伴 crm_promoter.id' AFTER channel_id,
    ADD COLUMN referral_code VARCHAR(8)  NULL COMMENT '推荐时使用的邀请码' AFTER referrer_id,
    ADD KEY idx_lead_referrer (referrer_id);

ALTER TABLE crm_customer
    ADD COLUMN grade            VARCHAR(4)   NULL COMMENT '客户评级 A/B/C/D' AFTER intent_level,
    ADD COLUMN referrer_id      BIGINT       NULL COMMENT '推荐伙伴 crm_promoter.id' AFTER grade,
    ADD COLUMN service_type     TINYINT      NULL COMMENT '需求类型:1仓储 2代发 3仓配 4园区入驻' AFTER referrer_id,
    ADD COLUMN biz_line         TINYINT      NOT NULL DEFAULT 2 COMMENT '归因业务线:1租赁 2云仓' AFTER service_type,
    ADD COLUMN sign_mode        TINYINT      NULL COMMENT '签约方式:1园区签 2云仓直签,空=园区默认' AFTER biz_line,
    ADD COLUMN attribution_note VARCHAR(500) NULL COMMENT '归因判定记录' AFTER sign_mode,
    ADD COLUMN project_id       BIGINT       NULL AFTER attribution_note,
    -- 生产库 crm_customer 为空表(2026-09-20 查实),可直接加;逻辑删除的行不占手机号
    ADD COLUMN phone_active_key VARCHAR(20) AS (IF(deleted = 0 AND phone <> '', phone, NULL)) STORED,
    ADD UNIQUE KEY uk_customer_phone_active (phone_active_key),
    ADD KEY idx_customer_referrer (referrer_id);

ALTER TABLE biz_contract
    ADD COLUMN grade VARCHAR(4) NULL COMMENT '签约时客户评级快照(租赁一次性佣金用)' AFTER source;

-- ---------- 7) 种子:岗位 / 评级 / 规则参数(幂等) ----------
INSERT INTO crm_position (code, name, sort, share_pct, share_min_pct, lock_cap, promote_amount, promote_orders, team_counted, demote_enabled, status, tenant_id, create_by, create_time, version, deleted)
SELECT t.code, t.name, t.sort, t.share_pct, t.share_min_pct, t.lock_cap, t.promote_amount, t.promote_orders, t.team_counted, t.demote_enabled, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'P1' AS code, '园区伙伴'   AS name, 1 AS sort, 50 AS share_pct, 40  AS share_min_pct, 50  AS lock_cap, 0        AS promote_amount, 0  AS promote_orders, 0 AS team_counted, 0 AS demote_enabled
    UNION ALL SELECT 'P2', '银牌合伙人', 2, 70,  60,  100, 200000,  3,  0, 0
    UNION ALL SELECT 'P3', '金牌合伙人', 3, 85,  80,  200, 800000,  10, 1, 0
    UNION ALL SELECT 'P4', '钻石合伙人', 4, 100, 100, 500, 2000000, 25, 1, 0
) t
WHERE NOT EXISTS (SELECT 1 FROM crm_position p WHERE p.code = t.code AND p.deleted = 0);

INSERT INTO crm_customer_grade (code, name, descr, lease_commission_months, erp_total_rate, service_total_rate, contract_bonus, auto_min_rent, auto_min_orders, sort, status, tenant_id, create_by, create_time, version, deleted)
SELECT t.code, t.name, t.descr, t.lm, t.er, 0, 0, t.rent, t.orders, t.sort, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'A' AS code, 'A 级' AS name, '头部客户' AS descr, 1.00 AS lm, 8 AS er, 80000 AS rent, 50000 AS orders, 1 AS sort
    UNION ALL SELECT 'B', 'B 级', '重点客户', 1.00, 6, 40000, 10000, 2
    UNION ALL SELECT 'C', 'C 级', '一般客户', 0.50, 5, 15000, 2000,  3
    UNION ALL SELECT 'D', 'D 级', '小客户',   0.50, 3, 0,     0,     4
) t
WHERE NOT EXISTS (SELECT 1 FROM crm_customer_grade g WHERE g.code = t.code AND g.deleted = 0);

-- 规则参数全表(PARK-MKT-001 §3),module = 'marketing'
INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'marketing', t.k, t.v, t.r, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'ladder_depth'          AS k, '2'      AS v, '岗位数 2/4,4 级常驻合规提示'            AS r
    UNION ALL SELECT 'override_enabled',    '1',    '钻石合伙人可下调团队份额开关'
    UNION ALL SELECT 'freeze_days',         '7',    '出库单佣金冻结天数 0–30'
    UNION ALL SELECT 'min_withdraw',        '100',  '最低提现金额(元)'
    UNION ALL SELECT 'withdraw_free_times', '1',    '每月免手续费提现次数'
    UNION ALL SELECT 'daily_referral_cap',  '20',   '单人日推荐上限'
    UNION ALL SELECT 'attribution_rule',    'first','归因规则 first 先到先得 / protect N 天保护期'
    UNION ALL SELECT 'protect_days',        '0',    '保护期天数(attribution_rule=protect 时)'
    UNION ALL SELECT 'dup_customer_fields', 'phone','重复客户判定字段 phone / phone,credit_code'
    UNION ALL SELECT 'clawback_days',       '90',   '服务合同生效 N 天内终止扣回'
    UNION ALL SELECT 'old_channel_exclusive','1',   '老渠道佣金互斥:同一合同两边只算一边'
    UNION ALL SELECT 'internal_commission', '0',    '内部人员(手机号命中 sys_user)是否计佣'
    UNION ALL SELECT 'monthly_cap',         '0',    '单伙伴月佣金上限,0 不限'
    UNION ALL SELECT 'tax_mode',            '1',    '1 个税代扣 2 灵工平台代征'
    UNION ALL SELECT 'tax_rate',            '0.20', '个税代扣模式的预扣率(劳务报酬简化口径)'
    UNION ALL SELECT 'default_sign_mode',   '1',    '默认签约方式 1 园区签 2 云仓直签'
    UNION ALL SELECT 'prelock_days',        '7',    '预锁期'
    UNION ALL SELECT 'lock_days',           '180',  '有效锁定期'
    UNION ALL SELECT 'lock_extend_days',    '90',   '延期一次的天数'
    UNION ALL SELECT 'lock_cooldown_days',  '30',   '释放后原伙伴不可再锁同一客户的冷却'
    UNION ALL SELECT 'lease_clawback_days', '90',   '租赁生效 N 天内退租扣回'
    UNION ALL SELECT 'lease_renew_ratio',   '0',    '租赁续签计佣比例,默认不再计'
    UNION ALL SELECT 'invite_grace_days',   '7',    '无邀请码注册后可补填的天数'
) t
WHERE NOT EXISTS (SELECT 1 FROM biz_setting s WHERE s.module = 'marketing' AND s.skey = t.k AND s.deleted = 0);
