-- Additive only: existing work orders retain their free-text locations.
ALTER TABLE pm_work_order
    ADD COLUMN floor_id BIGINT NULL COMMENT '维修楼层',
    ADD COLUMN floor_plan_file_id BIGINT NULL COMMENT '报修时的平面图版本，引用 sys_file',
    ADD COLUMN plan_x DECIMAL(9,8) NULL COMMENT '图上横向相对坐标 0..1',
    ADD COLUMN plan_y DECIMAL(9,8) NULL COMMENT '图上纵向相对坐标 0..1',
    ADD INDEX idx_work_order_floor (floor_id),
    ADD INDEX idx_work_order_floor_plan (floor_plan_file_id);
