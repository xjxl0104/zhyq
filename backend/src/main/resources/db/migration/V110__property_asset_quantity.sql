-- V110 物业资产台账:增加数量、单位
-- 盘点清单里同一种资产常有多件(如文件柜 4 个),台账原先一条只能代表一件。
-- 纯新增两列,不动已有数据;已有资产数量默认 1。
ALTER TABLE pm_asset
    ADD COLUMN quantity INT         NOT NULL DEFAULT 1 COMMENT '数量(件)' AFTER category,
    ADD COLUMN unit     VARCHAR(16) NULL               COMMENT '单位:台/套/个/架…' AFTER quantity;
