-- 每个云仓配置一次云山发货量接口，该云仓下所有客户共用；未配置的云仓仍沿用服务端共享接口。
CREATE TABLE crm_warehouse_yunshan_conn (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id      BIGINT       NOT NULL COMMENT '园区侧云仓(crm_warehouse)',
    base_url          VARCHAR(500) NOT NULL,
    app_id_enc        TEXT         NOT NULL,
    public_key_enc    TEXT         NOT NULL,
    project_id        BIGINT       NULL,
    tenant_id         BIGINT       NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version           INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_wh_yunshan_conn (warehouse_id)
) COMMENT '云仓级云山发货量接口连接（凭证加密保存）';
