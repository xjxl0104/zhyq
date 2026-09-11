-- =====================================================================
-- V54 能耗表计:补区域/用户名、抄表账期与幂等唯一键
--
-- 背景:负责人要把园区 8 月的水表/电表抄表数据登记进系统,现有结构装不下:
--   1) eng_meter 只有 project/building/room 三个 id,没有"区域"(如 1层A区)与
--      "用户名"(抄表表格里的承租方简称)两列 —— 这两列是抄表纸上的关键识别信息;
--      租户现有实现是靠 room_id → 执行中合同 → biz_tenant 反查(MeterController:263),
--      房间没挂合同就查不到,customer_name 作为兜底显示,不替代该反查。
--   2) eng_reading 没有账期列,账期靠"上一条 read_time ~ 本条 read_time"推算
--      (MeterController:327),同月补录两条会把账期切碎。补 period(yyyy-MM)按月对齐。
--   3) eng_meter.code 的唯一键在 V9__fix_unique_and_seed.sql:26 被降级成普通索引,
--      批量导入重跑会产生重复表计,无库级兜底 —— 本次恢复唯一约束。
--
-- 注:本迁移只加列与索引,不改任何既有数据。
-- =====================================================================

ALTER TABLE eng_meter
    ADD COLUMN area          VARCHAR(64)  NULL COMMENT '区域,如 1层A区/室外(抄表表格口径)' AFTER name,
    ADD COLUMN customer_name VARCHAR(128) NULL COMMENT '抄表表格上的用户名,租户反查不到时兜底显示' AFTER area;

ALTER TABLE eng_reading
    ADD COLUMN period VARCHAR(7) NULL COMMENT '账期 yyyy-MM,按月对齐,避免靠 read_time 推算' AFTER meter_id;

-- 表计编号唯一(恢复 V9 被降级的约束),但**不能**用 (code, deleted) 复合键 ——
-- V9__fix_unique_and_seed.sql:2-4 已写明那样会撞键:逻辑删除下软删行 deleted 恒为 1,
-- 同一编码删两次时第二次的 (code,1) 与第一次冲突,直接 500。
-- 改用本仓 V32__receivable_import.sql:163 的生成列写法:只对存活行(deleted=0)约束,
-- 软删后编号释放可重用,再删也不会撞。
ALTER TABLE eng_meter
    ADD COLUMN code_active_key VARCHAR(64)
        GENERATED ALWAYS AS (IF(deleted = 0, code, NULL)) STORED COMMENT '存活行的编号,用于唯一约束' AFTER code,
    ADD UNIQUE KEY uk_eng_meter_code_active (code_active_key);

-- 同一块表同一账期只允许一条抄表记录 —— 让批量导入可重复执行不产生重复行。
-- 同样只约束存活行;period 为空时整体为 NULL 即不约束,与既有 11 条(period 全空)兼容。
ALTER TABLE eng_reading
    ADD COLUMN reading_active_key VARCHAR(40)
        GENERATED ALWAYS AS (IF(deleted = 0 AND period IS NOT NULL, CONCAT(meter_id, ':', period), NULL)) STORED
        COMMENT '存活行的表计+账期,用于唯一约束',
    ADD UNIQUE KEY uk_eng_reading_active (reading_active_key);
