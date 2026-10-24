-- 订单接入方式去掉“手工导入”，改为“云山发货量同步”
ALTER TABLE crm_warehouse MODIFY COLUMN order_mode VARCHAR(16) NOT NULL DEFAULT 'erp' COMMENT 'erp外部对接/yunshan云山发货量同步';
UPDATE crm_warehouse SET order_mode = 'yunshan' WHERE order_mode = 'manual';
