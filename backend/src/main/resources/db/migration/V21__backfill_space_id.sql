-- V21__backfill_space_id.sql
-- 历史业务数据回填 space_id：把既有 roomId/关联链路 映射到 sys_space 节点。
-- 前提：sys_space 已经 reconcile 过（room/building/floor/project 节点已存在）。
-- 幂等：仅 UPDATE space_id 为 NULL 的行，可重复执行、可在任意次启动后重跑。

-- 1) biz_contract_room：房源关系表本身有 room_id，直接映射 room 级节点
UPDATE biz_contract_room r
JOIN sys_space s ON s.ref_type = 'room' AND s.ref_id = r.room_id AND s.deleted = 0
SET r.space_id = s.id
WHERE r.room_id IS NOT NULL AND r.space_id IS NULL;

-- 2) pm_work_order：有 room_id，直接映射 room 级节点（room_id 为空的工单保持 space_id 为空）
UPDATE pm_work_order w
JOIN sys_space s ON s.ref_type = 'room' AND s.ref_id = w.room_id AND s.deleted = 0
SET w.space_id = s.id
WHERE w.room_id IS NOT NULL AND w.space_id IS NULL;

-- 3) eng_reading：本身无 room_id，经 eng_meter.room_id 间接关联到 room 级节点
UPDATE eng_reading rd
JOIN eng_meter m ON rd.meter_id = m.id
JOIN sys_space s ON s.ref_type = 'room' AND s.ref_id = m.room_id AND s.deleted = 0
SET rd.space_id = s.id
WHERE m.room_id IS NOT NULL AND rd.space_id IS NULL;

-- 4) biz_contract：实测该表【无 room_id 列】——房源关联走 N:M 关系表 biz_contract_room，
--    合同表本身不存在设计文档假设的"主房源 roomId"字段。为避免在无授权业务规则的情况下
--    臆造"选哪条 biz_contract_room 记录作为主房源"的逻辑，本次改为按 project_id 回填到
--    PROJECT 级空间节点（粒度从 room 降级为 project，如实记录于任务7报告，供后续如需要
--    room 级 contract.space_id 时另行定义"主房源"选取规则再迁移）。
UPDATE biz_contract c
JOIN sys_space s ON s.ref_type = 'project' AND s.ref_id = c.project_id AND s.deleted = 0
SET c.space_id = s.id
WHERE c.project_id IS NOT NULL AND c.space_id IS NULL;

-- 5) iot_device：实测该表【无 room_id 列】，仅有 project_id / building_id。为如实反映真实
--    可用信息，回填到 BUILDING 级空间节点（粒度从 room 降级为 building，同上如实记录）。
UPDATE iot_device d
JOIN sys_space s ON s.ref_type = 'building' AND s.ref_id = d.building_id AND s.deleted = 0
SET d.space_id = s.id
WHERE d.building_id IS NOT NULL AND d.space_id IS NULL;
