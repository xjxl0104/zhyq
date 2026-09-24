-- V60 全民营销 · 修复客户锁软删除后的 active_key 占用
-- V57 的 active_key 只按锁状态判断,软删除的历史锁仍会占用唯一键。
-- 逻辑删除后释放 active_key,允许同一客户重新报备。
ALTER TABLE crm_customer_lock
    MODIFY COLUMN active_key BIGINT AS (
        IF(deleted = 0 AND status IN (1, 2), customer_id, NULL)
    ) STORED;
