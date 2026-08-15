-- ver6.5 工单联动与责任单位
-- 背景:
--   1) pm_work_order 原先只用无约束的 source 文本记录来源(如 '巡检计划'/'安防巡更'),
--      不写源记录主键, 导致工单反查不到源巡检/巡更记录, 也无法从源记录跳到工单。
--      仅告警转单例外(已有 source_alarm_id)。本迁移补通用的 source_type + source_id。
--   2) 责任单位此前完全没有字段, 最接近的 assignee 只是处理人姓名文本。
--      本迁移建 pm_responsible_unit 主数据表, 工单以外键引用, 支持导入时按名称 upsert。

-- ---------- 1. 工单来源反查 ----------
ALTER TABLE pm_work_order
    ADD COLUMN source_type VARCHAR(32) NULL COMMENT '来源类型: INSPECTION_PLAN/PATROL/CHECK/FEEDBACK/ALARM/MANUAL' AFTER source,
    ADD COLUMN source_id   BIGINT      NULL COMMENT '来源记录主键, 与 source_type 配对使用' AFTER source_type;

-- 按来源反查工单用
CREATE INDEX idx_wo_source ON pm_work_order (source_type, source_id);

-- 回填历史数据的 source_type(存量记录无法恢复 source_id, 只能标类型)
UPDATE pm_work_order SET source_type = 'INSPECTION_PLAN' WHERE TRIM(source) = '巡检计划' AND source_type IS NULL;
UPDATE pm_work_order SET source_type = 'PATROL'          WHERE TRIM(source) = '安防巡更' AND source_type IS NULL;
UPDATE pm_work_order SET source_type = 'ALARM'           WHERE source_alarm_id IS NOT NULL AND source_type IS NULL;
UPDATE pm_work_order SET source_type = 'ALARM'           WHERE TRIM(source) = '规则引擎' AND source_type IS NULL;
-- 已有 source_alarm_id 的顺带补进通用列, 便于统一查询
UPDATE pm_work_order SET source_id = source_alarm_id WHERE source_alarm_id IS NOT NULL AND source_id IS NULL;

-- ---------- 2. 责任单位主数据 ----------
-- 审计列与 tenant_id 沿用本项目既有约定(见 V6__property.sql 各表)
CREATE TABLE pm_responsible_unit (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(128) NOT NULL COMMENT '单位名称, 导入时按此去重',
    unit_type      VARCHAR(32)  COMMENT '类型:内部部门/外部供应商/物业/施工方',
    contact        VARCHAR(64)  COMMENT '联系人',
    contact_phone  VARCHAR(20),
    service_scope  VARCHAR(255) COMMENT '服务范围/专业,如 电梯/消防/空调',
    project_id     BIGINT       COMMENT '所属园区,NULL 表示全局通用',
    enabled        TINYINT      NOT NULL DEFAULT 1,
    remark         VARCHAR(500),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    -- 同园区内单位名唯一。注意 project_id 可为 NULL,而 MySQL 唯一索引中 NULL 互不相等,
    -- 因此"全局通用"(project_id IS NULL)的同名单位无法靠本约束拦住,
    -- 由 ResponsibleUnitImportService 导入前按名称查重兜住(isNull 分支)。
    UNIQUE KEY uk_unit_name (project_id, name, deleted),
    KEY idx_unit_enabled (enabled, deleted),
    KEY idx_unit_name (name)
) COMMENT '责任单位主数据';

ALTER TABLE pm_work_order
    ADD COLUMN responsible_unit_id BIGINT NULL COMMENT '责任单位, 引用 pm_responsible_unit.id' AFTER assignee;

CREATE INDEX idx_wo_resp_unit ON pm_work_order (responsible_unit_id);
