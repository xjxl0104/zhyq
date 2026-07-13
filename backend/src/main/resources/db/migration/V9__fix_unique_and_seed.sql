-- =====================================================================
-- V9 修复:
-- 1) 逻辑删除 + (code, deleted) 复合唯一键会撞键:同一编码删两次时 (code,1) 冲突 500。
--    改为普通索引,唯一性由应用层保证(演示系统的务实取舍)。
-- 2) 种子补漏:合同3(HT2026003)补上与房间9(T-1002,签约中)的房源关联,
--    使"审批→房源变在租"演示闭环成立。
-- =====================================================================

-- 用户表
ALTER TABLE sys_user DROP INDEX uk_username, ADD INDEX idx_username (username);
-- 字典类型
ALTER TABLE sys_dict_type DROP INDEX uk_dict_type, ADD INDEX idx_dict_type_code (dict_type);
-- 项目
ALTER TABLE biz_project DROP INDEX uk_proj_code, ADD INDEX idx_proj_code (code);
-- 房间
ALTER TABLE biz_room DROP INDEX uk_room_code, ADD INDEX idx_room_code (code);
-- 租客
ALTER TABLE biz_tenant DROP INDEX uk_tenant_code, ADD INDEX idx_tenant_code (code);
-- 合同
ALTER TABLE biz_contract DROP INDEX uk_contract_code, ADD INDEX idx_contract_code (code);
-- 账单
ALTER TABLE fin_bill DROP INDEX uk_bill_code, ADD INDEX idx_bill_code (code);
-- 工单
ALTER TABLE pm_work_order DROP INDEX uk_wo_code, ADD INDEX idx_wo_code (code);
-- 表计
ALTER TABLE eng_meter DROP INDEX uk_meter_code, ADD INDEX idx_meter_code (code);
-- 设备
ALTER TABLE iot_device DROP INDEX uk_dev_code, ADD INDEX idx_dev_code2 (code);
-- 注意:fin_payment 的 uk_pay_no 保留 —— 它是收款幂等的根基,单列唯一无逻辑删除撞键问题

-- 种子补漏:合同3 ↔ 房间9(仅当尚无关联时插入,避免已手工修过的库重复)
INSERT INTO biz_contract_room (contract_id, room_id, rent_area, rent_price, tenant_id, create_by, create_time, version, deleted)
SELECT 3, 9, 200.00, 160.00, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_contract_room WHERE contract_id = 3 AND room_id = 9 AND deleted = 0);
