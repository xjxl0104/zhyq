-- =====================================================================
-- V15 批4:物联深化(点位/通道/厂商) + 系统(运营资源/消息中心) + 能耗补充种子
-- =====================================================================

-- 点位
CREATE TABLE iot_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL COMMENT '点位名称',
    project_id BIGINT, building_id BIGINT,
    floor_name VARCHAR(32) COMMENT '楼层',
    location VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '设备点位';

-- 通道
CREATE TABLE iot_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '所属设备',
    channel_no INT NOT NULL DEFAULT 1,
    name VARCHAR(128),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1在线 0离线',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_ch (device_id)
) COMMENT '设备通道';

-- 厂商平台配置
CREATE TABLE iot_vendor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL COMMENT '厂商:海康/大华/TB平台',
    platform VARCHAR(64) COMMENT '平台类型',
    api_url VARCHAR(255),
    app_key VARCHAR(128),
    app_secret VARCHAR(128) COMMENT '密钥(演示明文,生产须加密)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '厂商平台配置';

-- 运营资源
CREATE TABLE sys_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rtype VARCHAR(32) NOT NULL DEFAULT '轮播图' COMMENT '轮播图/导航/公告位/活动位',
    title VARCHAR(255) NOT NULL,
    image_url VARCHAR(500),
    link VARCHAR(255) COMMENT '跳转地址',
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '运营资源';

-- 消息模板
CREATE TABLE sys_msg_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL COMMENT '模板编码',
    name VARCHAR(128) NOT NULL,
    channel VARCHAR(32) NOT NULL DEFAULT '站内信' COMMENT '站内信/短信/邮件',
    content VARCHAR(1000) COMMENT '内容,变量用{var}',
    status TINYINT NOT NULL DEFAULT 1,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_tpl (code)
) COMMENT '消息模板';

-- 消息发送记录
CREATE TABLE sys_msg_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(64),
    receiver VARCHAR(128) NOT NULL,
    channel VARCHAR(32),
    content VARCHAR(1000),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1成功 2失败',
    fail_reason VARCHAR(255),
    send_time DATETIME,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '消息记录';

-- ===== 种子 =====
INSERT INTO iot_point (name,project_id,building_id,floor_name,location,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('A栋大堂点位',1,1,'1F','A栋大堂东南角',1,1,'system',NOW(),1,0),
 ('B栋入口点位',1,2,'1F','B栋正门',1,1,'system',NOW(),1,0),
 ('地库道闸点位',1,1,'B1','负一层入口',1,1,'system',NOW(),1,0);

INSERT INTO iot_channel (device_id,channel_no,name,status,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,1,'大堂全景',1,1,'system',NOW(),1,0),
 (1,2,'大堂特写',1,1,'system',NOW(),1,0),
 (2,1,'B栋入口主通道',0,1,'system',NOW(),1,0);

INSERT INTO iot_vendor (name,platform,api_url,app_key,app_secret,status,remark,tenant_id,create_by,create_time,version,deleted) VALUES
 ('海康威视','iSecure Center','https://hik.example.com/artemis','demo_ak','****',1,'摄像头/门禁接入',1,'system',NOW(),1,0),
 ('ThingsBoard','TB开源平台','https://tb.example.com/api','tb_demo','****',1,'传感器/表计接入',1,'system',NOW(),1,0);

-- 物联补充设备(让分类页有数据)
INSERT INTO iot_device (code,name,category,vendor,project_id,building_id,location,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('CHG001','地库充电桩01','充电桩','特来电',1,1,'负一层C区',1,1,'system',NOW(),1,0),
 ('CHG002','地库充电桩02','充电桩','特来电',1,1,'负一层C区',2,1,'system',NOW(),1,0),
 ('BRK001','A栋总配电空开','空开','正泰',1,1,'A栋配电房',1,1,'system',NOW(),1,0),
 ('FIRE001','B栋烟感主机','消防','海湾',1,2,'B栋消控室',1,1,'system',NOW(),1,0);

INSERT INTO sys_resource (rtype,title,image_url,link,sort,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('轮播图','夏日消暑节活动预告','https://dummyimage.com/750x300/4f46e5/ffffff&text=Summer+Fest','/property/activity',1,1,1,'system',NOW(),1,0),
 ('轮播图','高企申报服务上线','https://dummyimage.com/750x300/16a34a/ffffff&text=Policy','/service/declare',2,1,1,'system',NOW(),1,0),
 ('公告位','7月物业费缴纳通知',NULL,'/finance/bill',1,1,1,'system',NOW(),1,0);

INSERT INTO sys_msg_template (code,name,channel,content,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('BILL_REMIND','账单催缴提醒','站内信','尊敬的{tenant},您有一笔{amount}元账单将于{date}到期,请及时缴纳。',1,1,'system',NOW(),1,0),
 ('CONTRACT_EXPIRE','合同到期提醒','短信','{tenant}您好,合同{code}将于{date}到期,请联系招商经理办理续签。',1,1,'system',NOW(),1,0),
 ('WORKORDER_DONE','工单完成通知','站内信','您报修的{title}已处理完成,请验收并评价。',1,1,'system',NOW(),1,0);

INSERT INTO sys_msg_record (template_code,receiver,channel,content,status,send_time,tenant_id,create_by,create_time,version,deleted) VALUES
 ('BILL_REMIND','云智能科技','站内信','尊敬的云智能科技,您有一笔108000元账单将于本月25日到期,请及时缴纳。',1,DATE_SUB(NOW(),INTERVAL 1 DAY),1,'system',NOW(),1,0),
 ('CONTRACT_EXPIRE','绿源环保','短信','绿源环保您好,合同HT2026002将于2028-02-29到期…',1,DATE_SUB(NOW(),INTERVAL 2 DAY),1,'system',NOW(),1,0),
 ('BILL_REMIND','速达物流','短信','(网关超时)',2,DATE_SUB(NOW(),INTERVAL 3 DAY),1,'system',NOW(),1,0);

-- 能耗补充读数(近6个月,支撑能耗统计趋势)
INSERT INTO eng_reading (meter_id,prev_reading,curr_reading,usage_amount,read_source,read_time,fee,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,9500.00,10100.00,600.00,'自动采集',DATE_SUB(NOW(),INTERVAL 5 MONTH),780.00,1,'system',NOW(),1,0),
 (1,10100.00,10650.00,550.00,'自动采集',DATE_SUB(NOW(),INTERVAL 4 MONTH),715.00,1,'system',NOW(),1,0),
 (1,10650.00,11300.00,650.00,'自动采集',DATE_SUB(NOW(),INTERVAL 3 MONTH),845.00,1,'system',NOW(),1,0),
 (1,11300.00,12000.00,700.00,'自动采集',DATE_SUB(NOW(),INTERVAL 2 MONTH),910.00,1,'system',NOW(),1,0),
 (1,12000.00,12500.00,500.00,'自动采集',DATE_SUB(NOW(),INTERVAL 1 MONTH),650.00,1,'system',NOW(),1,0),
 (2,700.00,760.00,60.00,'自动采集',DATE_SUB(NOW(),INTERVAL 3 MONTH),270.00,1,'system',NOW(),1,0),
 (2,760.00,800.00,40.00,'自动采集',DATE_SUB(NOW(),INTERVAL 2 MONTH),180.00,1,'system',NOW(),1,0),
 (3,8800.00,9300.00,500.00,'自动采集',DATE_SUB(NOW(),INTERVAL 2 MONTH),650.00,1,'system',NOW(),1,0),
 (3,9300.00,9800.00,500.00,'自动采集',DATE_SUB(NOW(),INTERVAL 1 MONTH),650.00,1,'system',NOW(),1,0);
