-- 保留历史映射和快照；原配置继续使用共享连接，新配置必须关联本地作业仓库。
ALTER TABLE crm_yunshan_volume_map
    ADD COLUMN operation_warehouse_id BIGINT NULL COMMENT '本地作业仓库',
    ADD COLUMN operation_warehouse_name VARCHAR(128) NULL,
    ADD COLUMN connection_base_url VARCHAR(500) NULL COMMENT '空值沿用服务端共享接口',
    ADD COLUMN connection_app_id_enc TEXT NULL,
    ADD COLUMN connection_public_key_enc TEXT NULL;
