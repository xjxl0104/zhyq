-- 物业日常巡检/检查统一体系：覆盖 设备巡检(EQUIPMENT)、安防巡更(PATROL)、保洁检查(CLEANING)、绿化检查(GREENING)
-- 1) 巡检人花名册（姓名、电话、职位、负责条线）
CREATE TABLE IF NOT EXISTS pm_inspector (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT       DEFAULT 1,
    name          VARCHAR(64)  NOT NULL COMMENT '巡检人姓名',
    phone         VARCHAR(32)  DEFAULT '' COMMENT '联系电话',
    position      VARCHAR(64)  DEFAULT '' COMMENT '职位/岗位',
    module_scope  VARCHAR(128) DEFAULT 'ALL' COMMENT '负责模块:ALL或逗号分隔EQUIPMENT,PATROL,CLEANING,GREENING',
    status        TINYINT      DEFAULT 1 COMMENT '1在岗 0停用',
    remark        VARCHAR(255) DEFAULT '',
    create_by     VARCHAR(64)  DEFAULT 'system',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(64)  DEFAULT 'system',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT          DEFAULT 0,
    deleted       TINYINT      DEFAULT 0,
    KEY idx_pm_inspector_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业巡检人员库';

-- 2) 巡检模板（固定检查内容模板 + 巡检人 + 24小时周期内频率配置）
CREATE TABLE IF NOT EXISTS pm_routine_template (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT       DEFAULT 1,
    module_type        VARCHAR(32)  NOT NULL COMMENT 'EQUIPMENT|PATROL|CLEANING|GREENING',
    name               VARCHAR(128) NOT NULL COMMENT '模板/任务名称',
    area               VARCHAR(128) DEFAULT '' COMMENT '默认巡检区域/路线',
    inspector_id       BIGINT       DEFAULT NULL COMMENT '关联巡检人ID',
    inspector_name     VARCHAR(64)  DEFAULT '' COMMENT '巡检人姓名快照',
    inspector_phone    VARCHAR(32)  DEFAULT '' COMMENT '巡检人电话快照',
    inspector_position VARCHAR(64)  DEFAULT '' COMMENT '巡检人职位快照',
    freq_type          VARCHAR(32)  DEFAULT 'DAILY_SLOTS' COMMENT 'DAILY_SLOTS每日固定时段 | INTERVAL_HOURS 24小时内每隔N小时',
    interval_hours     INT          DEFAULT 24 COMMENT '24小时周期内间隔小时(2/4/6/8/12/24)',
    start_time         VARCHAR(8)   DEFAULT '08:00' COMMENT '每日开始时间HH:mm',
    end_time           VARCHAR(8)   DEFAULT '20:00' COMMENT '每日截止时间HH:mm',
    time_slots         VARCHAR(255) DEFAULT '09:00' COMMENT '逗号分隔打卡时段如09:00,15:00,21:00',
    weekdays           VARCHAR(32)  DEFAULT '1,2,3,4,5,6,7' COMMENT '执行星期1-7(1周一..7周日)',
    status             TINYINT      DEFAULT 1 COMMENT '1启用 0停用',
    sort_order         INT          DEFAULT 10,
    remark             VARCHAR(255) DEFAULT '',
    create_by          VARCHAR(64)  DEFAULT 'system',
    create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by          VARCHAR(64)  DEFAULT 'system',
    update_time        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version            INT          DEFAULT 0,
    deleted            TINYINT      DEFAULT 0,
    KEY idx_pm_rt_mod (module_type, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业巡检固定模板';

-- 3) 巡检模板检查项清单（预设检查项，支持随时增删改排序）
CREATE TABLE IF NOT EXISTS pm_routine_template_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT       DEFAULT 1,
    template_id   BIGINT       NOT NULL,
    seq_no        INT          DEFAULT 1,
    item_name     VARCHAR(200) NOT NULL COMMENT '检查项内容',
    area_point    VARCHAR(128) DEFAULT '' COMMENT '具体区域/点位',
    standard      VARCHAR(255) DEFAULT '' COMMENT '检查标准/要求',
    require_photo TINYINT      DEFAULT 1 COMMENT '是否必拍(默认1)',
    min_photos    INT          DEFAULT 1 COMMENT '最少拍照张数',
    create_by     VARCHAR(64)  DEFAULT 'system',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(64)  DEFAULT 'system',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT          DEFAULT 0,
    deleted       TINYINT      DEFAULT 0,
    KEY idx_pm_rti_tpl (template_id, deleted, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检模板检查项';

-- 4) 每日/每班次巡检任务实例（严格由系统自动判定完成，禁止手动勾完成；未完成自动标红）
CREATE TABLE IF NOT EXISTS pm_routine_task (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT       DEFAULT 1,
    module_type        VARCHAR(32)  NOT NULL COMMENT 'EQUIPMENT|PATROL|CLEANING|GREENING',
    template_id        BIGINT       NOT NULL,
    template_name      VARCHAR(128) NOT NULL,
    area               VARCHAR(128) DEFAULT '',
    task_date          DATE         NOT NULL COMMENT '任务归属日期',
    slot_label         VARCHAR(64)  NOT NULL COMMENT '班次/时段标识如 09:00 班次',
    scheduled_start    DATETIME     NOT NULL COMMENT '计划开始时间',
    deadline_time      DATETIME     NOT NULL COMMENT '截止时间(超时未完成标红逾期)',
    inspector_id       BIGINT       DEFAULT NULL,
    inspector_name     VARCHAR(64)  DEFAULT '',
    inspector_phone    VARCHAR(32)  DEFAULT '',
    inspector_position VARCHAR(64)  DEFAULT '',
    total_items        INT          DEFAULT 0,
    completed_items    INT          DEFAULT 0,
    issue_count        INT          DEFAULT 0,
    status             VARCHAR(24)  DEFAULT 'PENDING' COMMENT 'PENDING待巡检|IN_PROGRESS巡检中|COMPLETED已自动完成|OVERDUE逾期未完成',
    completed_time     DATETIME     DEFAULT NULL,
    create_by          VARCHAR(64)  DEFAULT 'system',
    create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by          VARCHAR(64)  DEFAULT 'system',
    update_time        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version            INT          DEFAULT 0,
    deleted            TINYINT      DEFAULT 0,
    KEY idx_pm_rtask_mod_date (module_type, task_date, deleted),
    KEY idx_pm_rtask_tpl_slot (template_id, task_date, slot_label, deleted),
    KEY idx_pm_rtask_inspector (inspector_id, task_date, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业巡检任务实例';

-- 5) 巡检任务检查项现场打卡记录（强制即时拍照+水印）
CREATE TABLE IF NOT EXISTS pm_routine_task_item (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT       DEFAULT 1,
    task_id          BIGINT       NOT NULL,
    template_item_id BIGINT       DEFAULT NULL,
    seq_no           INT          DEFAULT 1,
    item_name        VARCHAR(200) NOT NULL,
    area_point       VARCHAR(128) DEFAULT '',
    standard         VARCHAR(255) DEFAULT '',
    require_photo    TINYINT      DEFAULT 1,
    min_photos       INT          DEFAULT 1,
    checked          TINYINT      DEFAULT 0 COMMENT '0未拍照完成 1已拍照完成(仅上传即时照片后自动置1)',
    result           VARCHAR(24)  DEFAULT 'NORMAL' COMMENT 'NORMAL正常|ABNORMAL发现异常',
    issue_category   VARCHAR(64)  DEFAULT '' COMMENT '异常分类',
    issue_desc       VARCHAR(500) DEFAULT '' COMMENT '异常问题描述',
    photo_file_ids   VARCHAR(500) DEFAULT '' COMMENT '带水印照片sys_file.id逗号分隔',
    photo_urls_json  TEXT         COMMENT 'JSON数组:[{fileId,url,capturedAt}]',
    captured_by      VARCHAR(64)  DEFAULT '' COMMENT '实际打卡人',
    captured_time    DATETIME     DEFAULT NULL COMMENT '即时拍照上传时间',
    work_order_id    BIGINT       DEFAULT NULL COMMENT '异常转派的报修工单ID',
    work_order_code  VARCHAR(64)  DEFAULT '' COMMENT '异常转派的报修工单号',
    create_by        VARCHAR(64)  DEFAULT 'system',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(64)  DEFAULT 'system',
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version          INT          DEFAULT 0,
    deleted          TINYINT      DEFAULT 0,
    KEY idx_pm_rti_task (task_id, deleted, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检任务检查项打卡明细';

-- 预置4个条线标准开箱即用巡检模板（管理员随时可编辑/增删）
INSERT INTO pm_inspector (name, phone, position, module_scope, status, remark)
SELECT '工程值班员', '13800000001', '工程巡检员', 'EQUIPMENT', 1, '默认预置巡检人，请按实际人员修改'
WHERE NOT EXISTS (SELECT 1 FROM pm_inspector WHERE deleted = 0);

INSERT INTO pm_inspector (name, phone, position, module_scope, status, remark)
SELECT '安防队长', '13800000002', '安防巡更员', 'PATROL', 1, '默认预置巡检人，请按实际人员修改'
WHERE NOT EXISTS (SELECT 1 FROM pm_inspector WHERE name = '安防队长' AND deleted = 0);

INSERT INTO pm_inspector (name, phone, position, module_scope, status, remark)
SELECT '保洁主管', '13800000003', '保洁质检员', 'CLEANING', 1, '默认预置巡检人，请按实际人员修改'
WHERE NOT EXISTS (SELECT 1 FROM pm_inspector WHERE name = '保洁主管' AND deleted = 0);

INSERT INTO pm_inspector (name, phone, position, module_scope, status, remark)
SELECT '绿化管理员', '13800000004', '绿化养护员', 'GREENING', 1, '默认预置巡检人，请按实际人员修改'
WHERE NOT EXISTS (SELECT 1 FROM pm_inspector WHERE name = '绿化管理员' AND deleted = 0);

INSERT INTO pm_routine_template (module_type, name, area, inspector_id, inspector_name, inspector_phone, inspector_position, freq_type, interval_hours, start_time, end_time, time_slots, weekdays, status, sort_order, remark)
SELECT 'EQUIPMENT', '配电与机电设备每日巡检', '配电房 / 水泵房 / 货梯机房', i.id, i.name, i.phone, i.position, 'DAILY_SLOTS', 12, '08:00', '20:00', '09:00,16:00', '1,2,3,4,5,6,7', 1, 1, '每日上下午各巡检一次，覆盖配电、消防水泵及货梯核心机房'
FROM pm_inspector i WHERE i.name = '工程值班员' AND i.deleted = 0
AND NOT EXISTS (SELECT 1 FROM pm_routine_template WHERE module_type = 'EQUIPMENT' AND deleted = 0) LIMIT 1;

INSERT INTO pm_routine_template (module_type, name, area, inspector_id, inspector_name, inspector_phone, inspector_position, freq_type, interval_hours, start_time, end_time, time_slots, weekdays, status, sort_order, remark)
SELECT 'PATROL', '园区24小时定时安防巡更', '园区大门 / 周界围墙 / 卸货平台 / 消防通道', i.id, i.name, i.phone, i.position, 'INTERVAL_HOURS', 4, '00:00', '24:00', '00:00,04:00,08:00,12:00,16:00,20:00', '1,2,3,4,5,6,7', 1, 1, '24小时周期内每4小时巡更打卡一次'
FROM pm_inspector i WHERE i.name = '安防队长' AND i.deleted = 0
AND NOT EXISTS (SELECT 1 FROM pm_routine_template WHERE module_type = 'PATROL' AND deleted = 0) LIMIT 1;

INSERT INTO pm_routine_template (module_type, name, area, inspector_id, inspector_name, inspector_phone, inspector_position, freq_type, interval_hours, start_time, end_time, time_slots, weekdays, status, sort_order, remark)
SELECT 'CLEANING', '园区公共区与卫生间每日保洁检查', '主干道 / 货梯厅 / 公共卫生间 / 垃圾房', i.id, i.name, i.phone, i.position, 'DAILY_SLOTS', 12, '08:00', '19:00', '10:00,16:00', '1,2,3,4,5,6,7', 1, 1, '每日上下午各检查一次卫生达标情况'
FROM pm_inspector i WHERE i.name = '保洁主管' AND i.deleted = 0
AND NOT EXISTS (SELECT 1 FROM pm_routine_template WHERE module_type = 'CLEANING' AND deleted = 0) LIMIT 1;

INSERT INTO pm_routine_template (module_type, name, area, inspector_id, inspector_name, inspector_phone, inspector_position, freq_type, interval_hours, start_time, end_time, time_slots, weekdays, status, sort_order, remark)
SELECT 'GREENING', '园区绿化带与乔灌木每日养护检查', '园区入口景观 / 东侧绿化带 / 停车场绿篱', i.id, i.name, i.phone, i.position, 'DAILY_SLOTS', 24, '08:00', '18:00', '10:00', '1,2,3,4,5,6,7', 1, 1, '每日检查绿化浇灌、修剪、病虫害与杂草清理'
FROM pm_inspector i WHERE i.name = '绿化管理员' AND i.deleted = 0
AND NOT EXISTS (SELECT 1 FROM pm_routine_template WHERE module_type = 'GREENING' AND deleted = 0) LIMIT 1;

-- 预置各模板默认检查项
INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 1, '高低压配电柜电压电流及温升检查', '配电房', '三相电压电流正常，无异响异味，室内温湿度达标', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'EQUIPMENT' AND t.deleted = 0
AND NOT EXISTS (SELECT 1 FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0);

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 2, '消防水泵控制柜与管网水压检查', '消防水泵房', '控制柜处于自动状态，水压表压力在绿色正常区间，无渗漏', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'EQUIPMENT' AND t.deleted = 0
AND (SELECT COUNT(*) FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0) = 1;

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 3, '货梯运行状态及机房通风检查', '货梯机房', '运行平稳无异常抖动，紧急通话有效，机房通风良好无杂物', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'EQUIPMENT' AND t.deleted = 0
AND (SELECT COUNT(*) FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0) = 2;

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 1, '园区主出入口道闸与值守核查', '园区大门', '道闸起落正常，来访车辆登记规范，无占道堵门', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'PATROL' AND t.deleted = 0
AND NOT EXISTS (SELECT 1 FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0);

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 2, '周界围墙照明与监控无死角巡查', '园区周界', '周界围栏完好无翻越痕迹，夜视照明与摄像头状态正常', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'PATROL' AND t.deleted = 0
AND (SELECT COUNT(*) FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0) = 1;

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 3, '消防通道畅通及消火栓器材巡查', '仓库外圈消防通道', '通道严禁堆货停放车辆，消火栓箱封条完好压力正常', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'PATROL' AND t.deleted = 0
AND (SELECT COUNT(*) FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0) = 2;

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 1, '主干道及卸货平台地面清扫检查', '园区主干道/平台', '路面无散落纸箱包装垃圾、无油污水渍', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'CLEANING' AND t.deleted = 0
AND NOT EXISTS (SELECT 1 FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0);

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 2, '各楼层货梯厅及公共卫生间保洁检查', '1-8层公共区', '地面干爽无异味，洗手台镜面洁净，耗材充足', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'CLEANING' AND t.deleted = 0
AND (SELECT COUNT(*) FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0) = 1;

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 3, '生活/工业垃圾房清运与消杀检查', '园区垃圾房', '垃圾桶盖闭合、无满溢渗漏，周边地面已冲洗消杀', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'CLEANING' AND t.deleted = 0
AND (SELECT COUNT(*) FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0) = 2;

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 1, '入口景观绿植长势与浇水检查', '园区入口景观带', '土壤湿润无枯萎黄叶，无倒伏断枝', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'GREENING' AND t.deleted = 0
AND NOT EXISTS (SELECT 1 FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0);

INSERT INTO pm_routine_template_item (template_id, seq_no, item_name, area_point, standard, require_photo, min_photos)
SELECT t.id, 2, '绿化带杂草清理及绿篱修剪检查', '园区东侧及停车场绿带', '绿篱平整无杂草垃圾，无明显病虫害迹象', 1, 1
FROM pm_routine_template t WHERE t.module_type = 'GREENING' AND t.deleted = 0
AND (SELECT COUNT(*) FROM pm_routine_template_item WHERE template_id = t.id AND deleted = 0) = 1;
