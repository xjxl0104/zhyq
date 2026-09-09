-- V52__crm_lead_registry.sql 线索管理对齐《云仓产业园客户信息收集与回访登记表》
--
-- 背景:crm_lead 自 V3 起只有 8 个业务字段,与园区实际使用的登记表(22 列)差距很大,
-- 招商同事仍在用 Excel 登记。本迁移把线索表扩到登记表口径,跟进记录扩到回访表口径。
--
-- 两处口径变化:
--   1. status 由 8 态(1新建 2待分配 3跟进中 4意向 5已转化 6无效 7公海 8审核中)
--      改为登记表的 6 态。5/6 与旧值语义一致故编码不变,其余按语义映射(见下方 UPDATE)。
--   2. 跟进负责人由关联用户的 owner_id 改为自由文本 owner_name —— 登记表里填的是
--      「丁超」这类姓名,并非系统账号。owner_id 保留不动,老数据与既有统计不受影响。

-- ==================== 线索:补齐登记表字段 ====================
ALTER TABLE crm_lead
    ADD COLUMN lead_no          VARCHAR(32)  NULL COMMENT '客户编号 KH-0001,服务端生成' AFTER id,
    ADD COLUMN register_date    DATE         NULL COMMENT '登记日期',
    ADD COLUMN customer_type    VARCHAR(64)  NULL COMMENT '客户类型:意向租仓客户(找仓)/云仓服务商/货主·电商卖家/其他',
    ADD COLUMN coop_mode        VARCHAR(64)  NULL COMMENT '意向合作方式:仓库整租/仓库分租/云仓仓储外包/一件代发/仓配一体化/…',
    ADD COLUMN goods_type       VARCHAR(128) NULL COMMENT '主营品类/货物类型',
    ADD COLUMN order_volume     VARCHAR(64)  NULL COMMENT '日均单量/月发货量',
    ADD COLUMN coop_period      VARCHAR(64)  NULL COMMENT '意向合作周期',
    ADD COLUMN budget_price     VARCHAR(64)  NULL COMMENT '预算/租金心理价位',
    ADD COLUMN intent_park      VARCHAR(128) NULL COMMENT '意向园区或对接仓库',
    ADD COLUMN region           VARCHAR(128) NULL COMMENT '客户所在地区',
    ADD COLUMN grade            VARCHAR(32)  NULL COMMENT '客户等级:A-高意向高价值/B-中等意向/C-低意向长期培育',
    ADD COLUMN owner_name       VARCHAR(64)  NULL COMMENT '跟进负责人(自由文本,登记表口径)',
    ADD COLUMN last_follow_date DATE         NULL COMMENT '最近跟进日期(由跟进记录自动回写)',
    ADD COLUMN follow_count     INT          NOT NULL DEFAULT 0 COMMENT '累计跟进次数(由跟进记录自动回写)';

-- 客户编号唯一。允许 NULL:MySQL 唯一索引不约束 NULL,老数据补号前不会互相冲突
ALTER TABLE crm_lead ADD UNIQUE KEY uk_lead_no (lead_no);
ALTER TABLE crm_lead ADD KEY idx_lead_grade (grade);

-- 状态映射到登记表 6 态。先做 7/8/4/3/2/1,再收尾,避免中间态互相覆盖:
--   旧 3跟进中 → 新 2跟进中(编码同,无需动)
--   旧 5已转化 → 新 5已签约/已成交(编码同,无需动)
--   旧 6无效   → 新 6已流失/暂缓(编码同,无需动)
UPDATE crm_lead SET status = 1 WHERE status IN (2, 7);  -- 待分配 / 公海 → 待跟进
UPDATE crm_lead SET status = 2 WHERE status IN (4, 8);  -- 意向 / 审核中 → 跟进中
-- 旧 1新建 本就落在新 1待跟进,不动

-- 老数据补客户编号:按 id 顺序发号,与服务端 KH-%04d 形制一致
SET @kh := 0;
UPDATE crm_lead
SET lead_no = CONCAT('KH-', LPAD((@kh := @kh + 1), 4, '0'))
WHERE lead_no IS NULL AND deleted = 0
ORDER BY id;

-- 登记日期缺省用创建日期,避免列表里一片空白
UPDATE crm_lead SET register_date = DATE(create_time)
WHERE register_date IS NULL AND create_time IS NOT NULL;

-- ==================== 跟进记录:补齐回访表字段 ====================
ALTER TABLE crm_follow
    ADD COLUMN follow_no     VARCHAR(32)  NULL COMMENT '跟进编号 GF-0001,服务端生成' AFTER id,
    ADD COLUMN follow_date   DATE         NULL COMMENT '跟进日期',
    ADD COLUMN intent_change VARCHAR(32)  NULL COMMENT '客户意向变化:明显升温/持平/降温/已成交/已流失',
    ADD COLUMN issue         VARCHAR(500) NULL COMMENT '遇到的问题/需协调事项',
    ADD COLUMN result        VARCHAR(500) NULL COMMENT '跟进结果/待办事项';

ALTER TABLE crm_follow ADD UNIQUE KEY uk_follow_no (follow_no);

SET @gf := 0;
UPDATE crm_follow
SET follow_no = CONCAT('GF-', LPAD((@gf := @gf + 1), 4, '0'))
WHERE follow_no IS NULL AND deleted = 0
ORDER BY id;

UPDATE crm_follow SET follow_date = DATE(create_time)
WHERE follow_date IS NULL AND create_time IS NOT NULL;

-- 老数据回填线索的跟进统计,与新增跟进时的回写口径一致
UPDATE crm_lead l
JOIN (
    SELECT lead_id, COUNT(*) AS c, MAX(follow_date) AS d
    FROM crm_follow WHERE deleted = 0 GROUP BY lead_id
) f ON f.lead_id = l.id
SET l.follow_count = f.c, l.last_follow_date = f.d;

-- ==================== 权限点 ====================
-- 沿用 V30 的幂等三件套。线索原先无权限点,本次补上,导入单独设点(它能批量写库)
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'crm:lead:query' AS p, '线索-查询' AS nm
    UNION ALL SELECT 'crm:lead:add','线索-新增'
    UNION ALL SELECT 'crm:lead:edit','线索-修改'
    UNION ALL SELECT 'crm:lead:delete','线索-删除'
    UNION ALL SELECT 'crm:lead:import','线索-导入'
    UNION ALL SELECT 'crm:follow:query','跟进记录-查询'
    UNION ALL SELECT 'crm:follow:add','跟进记录-新增'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.type = 3 AND m.perm IS NOT NULL AND m.perm <> '' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'admin' AND r.deleted = 0
WHERE u.username = 'admin' AND u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);
