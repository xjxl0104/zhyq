-- V141:
-- 1. 云山免租期备注简化:「免租期（首个年度）」统一改为「免租期」
-- 2. 单张缴费通知单支持调整独立抬头信息(header_override_json)

UPDATE eng_lease_contract
SET rules_json = REPLACE(rules_json, '免租期（首个年度）', '免租期')
WHERE rules_json LIKE '%免租期（首个年度）%';

UPDATE eng_lease_fee_entry
SET remark = REPLACE(remark, '免租期（首个年度）', '免租期')
WHERE remark LIKE '%免租期（首个年度）%';

UPDATE eng_lease_fee_notice_item
SET remark = REPLACE(remark, '免租期（首个年度）', '免租期')
WHERE remark LIKE '%免租期（首个年度）%';

UPDATE eng_lease_fee_period
SET snapshot_json = REPLACE(snapshot_json, '免租期（首个年度）', '免租期')
WHERE snapshot_json LIKE '%免租期（首个年度）%';

ALTER TABLE eng_fee_notice
    ADD COLUMN header_override_json TEXT NULL COMMENT '单张通知单抬头覆盖配置JSON(园区名/计费期间/开具日/截止日/联系人/收款账户)' AFTER remark;

ALTER TABLE eng_lease_fee_notice
    ADD COLUMN header_override_json TEXT NULL COMMENT '单张通知单抬头覆盖配置JSON(园区名/计费期间/开具日/截止日/联系人)' AFTER remark;
