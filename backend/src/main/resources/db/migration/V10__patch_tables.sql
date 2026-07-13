-- =====================================================================
-- V10 查缺补漏新增表:资产、应用中心、租客站内信
-- (审批 biz_approval / 巡检 pm_inspection_plan / 菜单 sys_menu /
--  流水 fin_flow / 发票 fin_invoice 已在 V1~V7 建好,本次仅补业务补充表)
-- 统一审计字段 + 逻辑删除 + 乐观锁
-- =====================================================================

-- 资产管理(规格书 §11)
CREATE TABLE pm_asset (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(64)  NOT NULL COMMENT '资产编码',
    name           VARCHAR(128) NOT NULL COMMENT '资产名称',
    category       VARCHAR(64)  COMMENT '分类:办公设备/机电/安防/家具/IT',
    project_id     BIGINT,
    building_id    BIGINT,
    location       VARCHAR(255) COMMENT '存放位置',
    price          DECIMAL(14,2) DEFAULT 0 COMMENT '采购价',
    purchase_date  DATE         COMMENT '采购日期',
    warranty_end   DATE         COMMENT '保修到期',
    owner          VARCHAR(64)  COMMENT '责任人',
    -- 状态:1在用 2闲置 3维修 4报废
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '资产状态',
    remark         VARCHAR(255),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_asset_code (code), KEY idx_asset_status (status)
) COMMENT '资产';

-- 应用中心(规格书 §15)
CREATE TABLE sys_app (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(64)  NOT NULL COMMENT '应用名称',
    category       VARCHAR(32)  NOT NULL COMMENT '分类:楼宇/租客/招商/合同/财务/内部办公/物业服务/楼宇运营/智能硬件',
    icon           VARCHAR(64)  COMMENT '图标名',
    path           VARCHAR(128) COMMENT '跳转路由',
    description    VARCHAR(255) COMMENT '用途说明',
    sort           INT          NOT NULL DEFAULT 0,
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1上架 0停用',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_app_cat (category)
) COMMENT '应用中心';

-- 用户应用收藏(规格书 §15:添加常用)
CREATE TABLE sys_app_favorite (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    app_id         BIGINT       NOT NULL,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_fav_user (user_id)
) COMMENT '应用收藏';

-- 租客站内信(规格书 §8:站内信管理)
CREATE TABLE tenant_message (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(255) NOT NULL COMMENT '标题',
    content        TEXT         COMMENT '正文',
    tenant_ref_id  BIGINT       COMMENT '接收租客(空=全部租客)',
    channel        VARCHAR(32)  DEFAULT '站内信' COMMENT '渠道:站内信/短信/微信',
    -- 状态:1草稿 2已发送
    status         TINYINT      NOT NULL DEFAULT 1,
    read_flag      TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    send_time      DATETIME,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_msg_tenant (tenant_ref_id)
) COMMENT '租客站内信';

-- =====================================================================
-- 种子数据
-- =====================================================================
INSERT INTO pm_asset (code,name,category,project_id,building_id,location,price,purchase_date,warranty_end,owner,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('ZC001','戴尔投影仪 M900','办公设备',1,1,'A栋301会议室',6800.00,DATE '2025-03-10',DATE '2028-03-10','物业小赵',1,1,'system',NOW(),1,0),
 ('ZC002','中央空调外机','机电',1,1,'A栋楼顶',85000.00,DATE '2023-06-01',DATE '2028-06-01','工程小钱',1,1,'system',NOW(),1,0),
 ('ZC003','海康球机摄像头×20','安防',1,2,'B栋各层',24000.00,DATE '2024-01-15',DATE '2027-01-15','设备管理员',1,1,'system',NOW(),1,0),
 ('ZC004','会议桌椅套装','家具',1,1,'B栋大会议室',12000.00,DATE '2024-09-20',NULL,'物业小赵',2,1,'system',NOW(),1,0),
 ('ZC005','旧服务器机柜','IT',2,3,'主塔楼机房',15000.00,DATE '2020-05-01',NULL,'IT运维',4,1,'system',NOW(),1,0);

INSERT INTO sys_app (name,category,icon,path,description,sort,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('租控管理','楼宇','OfficeBuilding','/building/room','房源状态与租控图',1,1,1,'system',NOW(),1,0),
 ('项目管理','楼宇','Grid','/building/project','项目楼宇档案',2,1,1,'system',NOW(),1,0),
 ('线索管理','招商','Promotion','/crm/lead','招商线索跟进转化',3,1,1,'system',NOW(),1,0),
 ('租客列表','租客','User','/tenant/list','企业与个人租客',4,1,1,'system',NOW(),1,0),
 ('合同列表','合同','Document','/contract/list','合同全生命周期',5,1,1,'system',NOW(),1,0),
 ('所有账单','财务','Money','/finance/bill','账单收款管理',6,1,1,'system',NOW(),1,0),
 ('财务报表','财务','TrendCharts','/finance/report','账龄收缴率',7,1,1,'system',NOW(),1,0),
 ('物业报修','物业服务','Tools','/property/workorder','报修工单全流程',8,1,1,'system',NOW(),1,0),
 ('会议室预约','物业服务','Calendar','/property/meeting','会议室预约核销',9,1,1,'system',NOW(),1,0),
 ('资产管理','物业服务','Box','/property/asset','资产台账',10,1,1,'system',NOW(),1,0),
 ('智能表计','楼宇运营','Odometer','/energy/meter','能耗抄表',11,1,1,'system',NOW(),1,0),
 ('智能硬件','智能硬件','Cpu','/iot/device','设备统一接入',12,1,1,'system',NOW(),1,0),
 ('预警事件','智能硬件','Warning','/iot/alarm','设备告警处置',13,1,1,'system',NOW(),1,0),
 ('审批中心','内部办公','Stamp','/oa/approval','统一审批待办',14,1,1,'system',NOW(),1,0),
 ('任务管理','内部办公','List','/oa/task','工作任务',15,1,1,'system',NOW(),1,0),
 ('数据看板','楼宇运营','DataLine','/data/center','经营驾驶舱',16,1,1,'system',NOW(),1,0);

INSERT INTO tenant_message (title,content,tenant_ref_id,channel,status,read_flag,send_time,tenant_id,create_by,create_time,version,deleted) VALUES
 ('7月物业费缴纳提醒','贵司7月物业费账单已生成,请于20日前缴纳。',1,'站内信',2,0,NOW(),1,'system',NOW(),1,0),
 ('园区停电通知','本周六上午9-11点A栋计划检修停电,请提前做好准备。',NULL,'站内信',2,1,DATE_SUB(NOW(),INTERVAL 2 DAY),1,'system',NOW(),1,0),
 ('合同续签提醒','贵司合同将于90天后到期,请及时联系招商经理办理续签。',2,'短信',2,0,DATE_SUB(NOW(),INTERVAL 1 DAY),1,'system',NOW(),1,0);

-- 巡检计划补充种子(表已在 V6 建好,之前无数据)
INSERT INTO pm_inspection_plan (name,project_id,cycle,route,points,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('A栋日常安全巡检',1,'每日','A栋1F大堂→电梯厅→各层消防栓→楼顶','大堂,电梯,消防栓×12,配电房',1,1,'system',NOW(),1,0),
 ('园区周界安防巡更',1,'每日','东门→南门→西门→北门→地库入口','周界5点位',1,1,'system',NOW(),1,0),
 ('B栋机电月度巡检',1,'每月','水泵房→配电室→空调机房→电梯机房','机电4房',1,1,'system',NOW(),1,0);
