-- V23__alarm_lifecycle.sql IoT 告警生命周期升级（#13 Task1）
-- 给 iot_alarm 加：受理人 / 累计次数 / 首末次时间 / 活动标记，并补去重唯一键。

-- 1) 加列
ALTER TABLE iot_alarm
    ADD COLUMN assignee    VARCHAR(64) NULL COMMENT '受理人',
    ADD COLUMN occur_count INT         NOT NULL DEFAULT 1 COMMENT '累计发生次数',
    ADD COLUMN first_time  DATETIME    NULL COMMENT '首次发生时间',
    ADD COLUMN last_time   DATETIME    NULL COMMENT '最后发生时间',
    ADD COLUMN active      TINYINT     NULL COMMENT '活动标记:1活动,关闭/消解置NULL';

-- 2) 回填既有数据
-- 2a) 首末次时间：全部行回填为 alarm_time（occur_count 已有默认值 1，无需回填）
UPDATE iot_alarm
SET first_time = alarm_time,
    last_time  = alarm_time
WHERE first_time IS NULL;

-- 2b) 活动标记：仅非终态(status NOT IN (5关闭,6误报))的行才可能是"活动告警"。
--     同一 (device_id, alarm_type) 若存在多条非终态行（历史脏数据/告警风暴遗留），
--     只保留 id 最大（最新）的一条置为 active=1，其余仍置 NULL，避免下一步唯一键冲突。
--     经核查 V8 种子数据（仅 2 条 iot_alarm，device_id 4/7 各一条，无重复组合），
--     此处按最新一条取值的写法对当前数据是恒等更新，同时对未来可能存在的脏数据也安全。
UPDATE iot_alarm a
    JOIN (
        SELECT device_id, alarm_type, MAX(id) AS max_id
        FROM iot_alarm
        WHERE status NOT IN (5, 6)
        GROUP BY device_id, alarm_type
    ) t ON a.id = t.max_id
SET a.active = 1;

-- 3) 回填完成、脏数据已去重后，才能加唯一键（顺序不可颠倒，否则历史重复行会导致迁移失败）
ALTER TABLE iot_alarm
    ADD UNIQUE KEY uk_active (device_id, alarm_type, active);
