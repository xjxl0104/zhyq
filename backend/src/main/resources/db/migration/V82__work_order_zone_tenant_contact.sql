-- Keep legacy orders at whole-floor granularity; new orders may specify A/B/C.
ALTER TABLE pm_work_order
    ADD COLUMN zone VARCHAR(1) NULL COMMENT '楼层分区 A/B/C；空值代表整层或旧工单',
    ADD COLUMN tenant_ref_id BIGINT NULL COMMENT '关联 biz_tenant 租客档案，区别于平台 tenant_id',
    ADD COLUMN tenant_contact VARCHAR(64) NULL COMMENT '租客联系人快照',
    ADD COLUMN tenant_contact_phone VARCHAR(20) NULL COMMENT '租客联系人电话快照',
    ADD INDEX idx_work_order_floor_zone (floor_id, zone),
    ADD INDEX idx_work_order_tenant_ref (tenant_ref_id);
