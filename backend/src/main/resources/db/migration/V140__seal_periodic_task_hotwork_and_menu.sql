-- =====================================================================
-- V140:
--   1. 菜单整理:
--      - 物业服务下线 在线商城/物品放行/场地预约;「装修申请」更名「装修及动火」
--      - 「办公」更名「办公管理」并置园区运营首位;下线 企业公告/人才招聘/文章管理/考勤管理
--      - 办公管理新增「印章管理」(/oa/seal)
--   2. srv_decoration 扩展装修与动火双申请表字段(apply_type/apply_no/applicant_unit/
--      apply_date/work_area/form_data_json/seal_id/seal_config_json)
--   3. 新建电子印章库 oa_seal 及模板盖章位置配置 biz_seal_placement
--   4. 新建周期任务可视化年月表 oa_periodic_task 与 oa_periodic_task_log
-- =====================================================================

-- ---------- 1. 菜单更名、下线与新增 ----------
UPDATE sys_menu SET name = '装修及动火'
WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'service/decoration';

UPDATE sys_menu SET name = '办公管理'
WHERE deleted = 0 AND type = 1 AND name = '办公' AND (path IS NULL OR path = '');

UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND (
    TRIM(BOTH '/' FROM path) IN (
        'service/mall', 'service/pass', 'service/site',
        'oa/notice', 'oa/recruit', 'oa/article', 'oa/attendance'
    )
    OR perm LIKE 'service:mall:%'
    OR perm LIKE 'service:product:%'
    OR perm LIKE 'service:pass:%'
    OR perm LIKE 'service:site:%'
    OR perm LIKE 'oa:notice:%'
    OR perm LIKE 'oa:recruit:%'
    OR perm LIKE 'oa:article:%'
    OR perm LIKE 'oa:attendance:%'
);

SET @oa_parent = COALESCE(
    (SELECT id FROM sys_menu WHERE deleted = 0 AND type = 1 AND name = '办公管理' ORDER BY id LIMIT 1),
    (SELECT parent_id FROM sys_menu WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'oa/task' ORDER BY id LIMIT 1),
    0
);

INSERT INTO sys_menu (parent_id, name, type, path, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT @oa_parent, '印章管理', 2, '/oa/seal', 4, 1, 1, 1, 'system', NOW(), 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE TRIM(BOTH '/' FROM path) = 'oa/seal' AND deleted = 0
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT g.role_id, seal.id
FROM sys_role_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('oa/task', 'oa/schedule', 'oa/approval', 'oa/document', 'energy/summary', 'service/decoration')
JOIN sys_menu seal ON TRIM(BOTH '/' FROM seal.path) = 'oa/seal' AND seal.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = g.role_id AND e.menu_id = seal.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT r.id, seal.id
FROM sys_role r
JOIN sys_menu seal ON TRIM(BOTH '/' FROM seal.path) = 'oa/seal' AND seal.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = r.id AND e.menu_id = seal.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT g.user_id, seal.id
FROM sys_user_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('oa/task', 'oa/schedule', 'oa/approval', 'oa/document', 'energy/summary', 'service/decoration')
JOIN sys_menu seal ON TRIM(BOTH '/' FROM seal.path) = 'oa/seal' AND seal.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_menu e WHERE e.user_id = g.user_id AND e.menu_id = seal.id);

-- ---------- 2. 装修及动火申请扩展字段 ----------
ALTER TABLE srv_decoration
    ADD COLUMN apply_type       VARCHAR(16)  NOT NULL DEFAULT 'DECORATION' COMMENT '申请类型:DECORATION装修报批 / HOT_WORK动火作业' AFTER id,
    ADD COLUMN apply_no         VARCHAR(64)  NULL COMMENT '申请编号' AFTER apply_type,
    ADD COLUMN applicant_unit   VARCHAR(128) NULL COMMENT '申请单位/承租单位全称' AFTER tenant_ref_id,
    ADD COLUMN apply_date       DATE         NULL COMMENT '申请日期' AFTER applicant_unit,
    ADD COLUMN work_area        VARCHAR(255) NULL COMMENT '装修部位/动火区域摘要' AFTER room_id,
    ADD COLUMN form_data_json   MEDIUMTEXT   NULL COMMENT '完整申请表JSON(对应正反面PDF字段)' AFTER scope,
    ADD COLUMN seal_id          BIGINT       NULL COMMENT '加盖电子印章ID' AFTER form_data_json,
    ADD COLUMN seal_config_json TEXT         NULL COMMENT '申请表盖章位置与缩放配置JSON' AFTER seal_id;

-- ---------- 3. 电子印章管理 & 模板印章排版 ----------
CREATE TABLE oa_seal (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(128) NOT NULL COMMENT '印章名称',
    category         VARCHAR(32)  NOT NULL DEFAULT '物业专用章' COMMENT '印章分类',
    org_name         VARCHAR(128) NULL COMMENT '所属单位/部门',
    image_data       MEDIUMTEXT   NOT NULL COMMENT '透明底PNG DataURI(base64)',
    default_width_mm INT          NOT NULL DEFAULT 42 COMMENT '默认盖章宽度(mm)',
    is_default       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认印章',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark           VARCHAR(500) NULL,
    tenant_id        BIGINT       NOT NULL DEFAULT 1,
    create_by        VARCHAR(32)  NULL,
    create_time      DATETIME     NULL,
    update_by        VARCHAR(32)  NULL,
    update_time      DATETIME     NULL,
    version          INT          NOT NULL DEFAULT 1,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_oa_seal_status (status, is_default)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '电子印章库';

CREATE TABLE biz_seal_placement (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    template_key VARCHAR(64)   NOT NULL COMMENT '模板标识:UTILITY_NOTICE/LEASE_RENT_NOTICE/LEASE_PROPERTY_NOTICE等',
    seal_id      BIGINT        NULL COMMENT '选用印章ID(NULL表示不盖章)',
    enabled      TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用盖章 0关闭',
    pos_x        DECIMAL(8,4)  NOT NULL DEFAULT 22.0000 COMMENT '中心点横坐标(%)',
    pos_y        DECIMAL(8,4)  NOT NULL DEFAULT 88.0000 COMMENT '中心点纵坐标(%)',
    scale        DECIMAL(8,4)  NOT NULL DEFAULT 1.0000 COMMENT '缩放倍率(0.4~2.5)',
    rotation     DECIMAL(8,2)  NOT NULL DEFAULT 0.00 COMMENT '旋转角度(deg)',
    opacity      DECIMAL(4,2)  NOT NULL DEFAULT 0.90 COMMENT '透明度(0.4~1.0)',
    tenant_id    BIGINT        NOT NULL DEFAULT 1,
    create_by    VARCHAR(32)   NULL,
    create_time  DATETIME      NULL,
    update_by    VARCHAR(32)   NULL,
    update_time  DATETIME      NULL,
    version      INT           NOT NULL DEFAULT 1,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    active_key   VARCHAR(64) GENERATED ALWAYS AS (IF(deleted = 0, template_key, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seal_placement_active (active_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '单据模板电子印章排版配置';

-- ---------- 4. 可视化周期任务年月表 ----------
CREATE TABLE oa_periodic_task (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(128) NOT NULL COMMENT '任务名称',
    category      VARCHAR(32)  NOT NULL DEFAULT '消防安全' COMMENT '类别:消防安全/特种设备/环保保洁/设施维保/行政合规/其他',
    cycle_type    VARCHAR(24)  NOT NULL DEFAULT 'MONTHLY' COMMENT '周期:MONTHLY/BIMONTHLY/QUARTERLY/SEMIANNUAL/ANNUAL/CUSTOM',
    months_json   VARCHAR(128) NOT NULL DEFAULT '[1,2,3,4,5,6,7,8,9,10,11,12]' COMMENT '计划执行月份JSON数组',
    node_day      INT          NOT NULL DEFAULT 15 COMMENT '计划节点日(1-28)',
    owner         VARCHAR(64)  NULL COMMENT '责任人/班组',
    vendor        VARCHAR(128) NULL COMMENT '维保/协作单位',
    color         VARCHAR(16)  NULL DEFAULT '#4f46e5' COMMENT '节点色标',
    description   VARCHAR(500) NULL COMMENT '标准节点要求/验收标准',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    sort          INT          NOT NULL DEFAULT 10,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32)  NULL,
    create_time   DATETIME     NULL,
    update_by     VARCHAR(32)  NULL,
    update_time   DATETIME     NULL,
    version       INT          NOT NULL DEFAULT 1,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_periodic_task_cat (category, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '园区周期性可视化任务计划';

CREATE TABLE oa_periodic_task_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    task_id        BIGINT       NOT NULL,
    year_no        INT          NOT NULL,
    month_no       INT          NOT NULL,
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待执行 1进行中 2已完成 3异常/逾期',
    planned_date   DATE         NULL,
    completed_date DATE         NULL,
    executor       VARCHAR(64)  NULL,
    remark         VARCHAR(500) NULL,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32)  NULL,
    create_time    DATETIME     NULL,
    update_by      VARCHAR(32)  NULL,
    update_time    DATETIME     NULL,
    version        INT          NOT NULL DEFAULT 1,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    active_key     VARCHAR(48) GENERATED ALWAYS AS (IF(deleted = 0, CONCAT(task_id, '|', year_no, '|', month_no), NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_periodic_task_log_active (active_key),
    KEY idx_periodic_task_log_ym (year_no, month_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '周期性任务月度执行节点记录';

-- 预置园区常用周期性任务模板(仅当表为空时写入,用户可随时增删改)
INSERT INTO oa_periodic_task (name, category, cycle_type, months_json, node_day, owner, vendor, color, description, status, sort, tenant_id, create_by, create_time, version, deleted)
SELECT * FROM (
    SELECT '消防设施月度检测' AS name, '消防安全' AS category, 'MONTHLY' AS cycle_type, '[1,2,3,4,5,6,7,8,9,10,11,12]' AS months_json, 10 AS node_day, '消防安全主管' AS owner, '消防维保单位' AS vendor, '#ef4444' AS color, '每月检查消防泵房、喷淋压力、烟感报警及灭火器有效期并留存记录' AS description, 1 AS status, 10 AS sort, 1 AS tenant_id, 'system' AS create_by, NOW() AS create_time, 1 AS version, 0 AS deleted
    UNION ALL
    SELECT '电梯月度安全维保', '特种设备', 'MONTHLY', '[1,2,3,4,5,6,7,8,9,10,11,12]', 15, '工程主管', '电梯维保公司', '#3b82f6', '每月两次例行保养汇总归档，核对维保单与年检标识', 1, 20, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT '配电房季度巡检保养', '设施维保', 'QUARTERLY', '[3,6,9,12]', 20, '强电班组', '电力维保单位', '#f59e0b', '每季度核查高低压柜温升、电容补偿及绝缘工器具', 1, 30, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT '化粪池与隔油池清理', '环保保洁', 'SEMIANNUAL', '[6,12]', 15, '保洁主管', '专业清掏单位', '#10b981', '每半年清掏抽吸并消杀，留存清运联单与前后照片', 1, 40, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT '防雷设施年度检测', '行政合规', 'ANNUAL', '[4]', 15, '工程主管', '防雷检测机构', '#8b5cf6', '雷雨季前完成全园防雷接地电阻检测并取得检测报告', 1, 50, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT '消防设施年度第三方检测', '消防安全', 'ANNUAL', '[11]', 20, '物业项目经理', '第三方消防检测机构', '#ec4899', '年度全系统消防联动测试并出具正式年度消防检测报告', 1, 60, 1, 'system', NOW(), 1, 0
) seed
WHERE NOT EXISTS (SELECT 1 FROM oa_periodic_task WHERE deleted = 0 LIMIT 1);
