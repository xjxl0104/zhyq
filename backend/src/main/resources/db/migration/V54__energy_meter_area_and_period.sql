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

-- 表计编号唯一(恢复 V9 被降级的约束)。带 deleted 以便软删后编号可释放重用:
-- 表计编号来自现场铭牌、由人工录入,不是服务端发号,与供应商编号那种自动发号语义不同。
CREATE UNIQUE INDEX uk_eng_meter_code ON eng_meter (code, deleted);

-- 同一块表同一账期只允许一条抄表记录 —— 让批量导入可重复执行不产生重复行
CREATE UNIQUE INDEX uk_eng_reading_meter_period ON eng_reading (meter_id, period, deleted);
