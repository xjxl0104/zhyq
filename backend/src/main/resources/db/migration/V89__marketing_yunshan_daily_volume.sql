-- 云山外部接口「发货量查询」拉取(试点:棒棒猪)。只存货主/仓库/日期/票数,不存联系方式、订单明细、商品、重量。
-- 只新增表,不改已有数据。

CREATE TABLE crm_yunshan_volume_map (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id           BIGINT       NOT NULL COMMENT '园区客户',
    warehouse_id          BIGINT       NOT NULL COMMENT '园区侧云仓(crm_warehouse)',
    parent_owner_no       VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '云山实体货主 ID,按字符串原样保存',
    parent_owner_name     VARCHAR(128) NULL,
    parent_warehouse_no   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '云山实体仓库 ID,按字符串原样保存',
    parent_warehouse_name VARCHAR(128) NULL,
    status                TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用(每日拉取) 0 停用',
    last_sync_at          DATETIME     NULL,
    last_sync_ok          TINYINT      NULL,
    project_id            BIGINT       NULL,
    tenant_id             BIGINT       NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version               INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_yunshan_map_entity (parent_owner_no, parent_warehouse_no),
    KEY idx_yunshan_map_wh (warehouse_id, customer_id)
) COMMENT '园区客户 ↔ 云山实体货主+实体仓库';

CREATE TABLE crm_yunshan_daily_volume (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    map_id              BIGINT      NOT NULL,
    customer_id         BIGINT      NOT NULL,
    warehouse_id        BIGINT      NOT NULL,
    parent_owner_no     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_warehouse_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stat_date           DATE        NOT NULL COMMENT '发货日期(中国标准时间)',
    consign_num         BIGINT      NOT NULL COMMENT '云山 consignNum:发货单量(票)',
    first_consign_num   BIGINT      NOT NULL COMMENT '首次拉到的票数,用于发现云山事后修正',
    revised_count       INT         NOT NULL DEFAULT 0 COMMENT '票数被云山修正的次数',
    last_revised_at     DATETIME    NULL,
    data_as_of          DATETIME    NULL COMMENT '云山 dataAsOf',
    fetched_at          DATETIME    NOT NULL COMMENT '最近一次拉到该日数据的时间',
    project_id          BIGINT      NULL,
    tenant_id           BIGINT      NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version             INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_yunshan_volume_day (map_id, stat_date),
    KEY idx_yunshan_volume_customer (customer_id, stat_date)
) COMMENT '云山每日发货票数快照';

CREATE TABLE crm_yunshan_sync_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    map_id           BIGINT       NOT NULL,
    trigger_type     TINYINT      NOT NULL COMMENT '1 定时 2 手动补拉',
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    ok               TINYINT      NOT NULL,
    days_fetched     INT          NOT NULL DEFAULT 0,
    days_changed     INT          NOT NULL DEFAULT 0 COMMENT '与本地已有值不同的天数',
    days_missing     INT          NOT NULL DEFAULT 0 COMMENT '本地有数、本次云山未返回的天数',
    total_consign    BIGINT       NULL,
    latest_stat_date DATE         NULL,
    data_as_of       DATETIME     NULL,
    error_code       VARCHAR(64)  NULL,
    error_msg        VARCHAR(500) NULL,
    tenant_id        BIGINT       NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version          INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_yunshan_log_map (map_id, id)
) COMMENT '云山发货量拉取日志';
