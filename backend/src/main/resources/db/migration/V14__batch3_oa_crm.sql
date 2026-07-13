-- =====================================================================
-- V14 批3:办公六件 + 招商深化
-- =====================================================================

-- 日程
CREATE TABLE oa_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    stype VARCHAR(16) DEFAULT '会议' COMMENT '会议/拜访/其他',
    owner VARCHAR(32) COMMENT '负责人',
    location VARCHAR(128),
    start_time DATETIME, end_time DATETIME,
    remind TINYINT NOT NULL DEFAULT 0 COMMENT '0不提醒 1提醒',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_sched (owner, start_time)
) COMMENT '日程';

-- 人才招聘
CREATE TABLE oa_recruit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_name VARCHAR(128) NOT NULL COMMENT '职位',
    dept VARCHAR(64),
    headcount INT NOT NULL DEFAULT 1,
    salary_range VARCHAR(64) COMMENT '薪资范围',
    applicants INT NOT NULL DEFAULT 0 COMMENT '投递数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1招聘中 2已关闭',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '人才招聘';

-- 文章管理
CREATE TABLE oa_article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(32) DEFAULT '新闻' COMMENT '新闻/通知/知识库',
    author VARCHAR(64),
    content TEXT,
    views INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1草稿 2已发布',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '文章';

-- 考勤(同步结果,规格书:首期只同步不自研排班)
CREATE TABLE oa_attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(64) NOT NULL,
    att_date DATE NOT NULL,
    checkin DATETIME, checkout DATETIME,
    att_status VARCHAR(16) NOT NULL DEFAULT '正常' COMMENT '正常/迟到/早退/缺勤/外勤',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_att (user_name, att_date)
) COMMENT '考勤结果';

-- 公文
CREATE TABLE oa_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_no VARCHAR(64) NOT NULL COMMENT '文号',
    title VARCHAR(255) NOT NULL,
    doc_type VARCHAR(16) NOT NULL DEFAULT '发文' COMMENT '收文/发文',
    from_unit VARCHAR(128) COMMENT '来文单位/拟稿部门',
    content TEXT,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1拟稿 2核稿 3签发 4归档',
    sign_by VARCHAR(32) COMMENT '签发人',
    sign_time DATETIME,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '公文';

-- 流程定义(定义管理,不做运行引擎;审批实例走 biz_approval)
CREATE TABLE oa_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_name VARCHAR(128) NOT NULL,
    biz_type VARCHAR(32) COMMENT '关联业务:contract/refund/adjust/decoration',
    steps TEXT COMMENT '步骤JSON,如 [{"step":"部门经理"},{"step":"财务"},{"step":"总经理"}]',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '流程定义';

-- 意向客户(线索转化而来)
CREATE TABLE crm_customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL COMMENT '客户/公司名',
    contact VARCHAR(64), phone VARCHAR(20),
    industry VARCHAR(64),
    demand_area VARCHAR(32) COMMENT '需求面积段',
    intent_level VARCHAR(8) DEFAULT 'B' COMMENT '意向等级 A/B/C',
    source_lead_id BIGINT COMMENT '来源线索',
    owner VARCHAR(32) COMMENT '负责人',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1跟进中 2已签约 3已流失',
    remark VARCHAR(500),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_cust (status, intent_level)
) COMMENT '意向客户';

-- 销售计划
CREATE TABLE crm_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    owner VARCHAR(32),
    period VARCHAR(16) COMMENT '周期,如 2026-Q3',
    target_amount DECIMAL(14,2) DEFAULT 0 COMMENT '目标签约额',
    achieved_amount DECIMAL(14,2) DEFAULT 0 COMMENT '已达成',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1进行中 2已完成',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '销售计划';

-- 佣金
CREATE TABLE crm_commission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL COMMENT '渠道',
    contract_id BIGINT NOT NULL COMMENT '关联合同',
    base_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '计佣基数(首年租金)',
    rate DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '佣金比例%',
    commission DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '佣金额',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1待结算 2已结算 3已作废',
    settle_time DATETIME,
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_comm (channel_id, status)
) COMMENT '佣金';

-- ===== 种子 =====
INSERT INTO oa_schedule (title,stype,owner,location,start_time,end_time,remind,tenant_id,create_by,create_time,version,deleted) VALUES
 ('季度经营分析会','会议','张总','B栋大会议室',DATE_ADD(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY)+INTERVAL 2 HOUR,1,1,'system',NOW(),1,0),
 ('拜访星辰生物','拜访','招商小李','客户公司',DATE_ADD(NOW(),INTERVAL 2 DAY),DATE_ADD(NOW(),INTERVAL 2 DAY)+INTERVAL 1 HOUR,1,1,'system',NOW(),1,0),
 ('消防演练','其他','物业小赵','中心广场',DATE_ADD(NOW(),INTERVAL 5 DAY),DATE_ADD(NOW(),INTERVAL 5 DAY)+INTERVAL 3 HOUR,0,1,'system',NOW(),1,0);

INSERT INTO oa_recruit (post_name,dept,headcount,salary_range,applicants,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('招商专员','招商中心',2,'8k-12k',15,1,1,'system',NOW(),1,0),
 ('物业维修工程师','物业服务部',1,'6k-9k',8,1,1,'system',NOW(),1,0),
 ('前台行政','综合部',1,'5k-7k',32,2,1,'system',DATE_SUB(NOW(),INTERVAL 30 DAY),1,0);

INSERT INTO oa_article (title,category,author,content,views,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('园区获评市级示范智慧园区','新闻','宣传部','近日,园区凭借数字化运营体系获评…',326,2,1,'system',DATE_SUB(NOW(),INTERVAL 3 DAY),1,0),
 ('停车场收费标准调整通知','通知','综合部','自下月起,地库月卡调整为…',518,2,1,'system',DATE_SUB(NOW(),INTERVAL 1 DAY),1,0),
 ('会议室使用规范(草稿)','知识库','行政','预约后未使用超过2次将限制…',0,1,1,'system',NOW(),1,0);

INSERT INTO oa_attendance (user_name,att_date,checkin,checkout,att_status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('招商小李',CURDATE(),CONCAT(CURDATE(),' 08:52:00'),NULL,'正常',1,'system',NOW(),1,0),
 ('财务小王',CURDATE(),CONCAT(CURDATE(),' 09:18:00'),NULL,'迟到',1,'system',NOW(),1,0),
 ('物业小赵',DATE_SUB(CURDATE(),INTERVAL 1 DAY),CONCAT(DATE_SUB(CURDATE(),INTERVAL 1 DAY),' 08:45:00'),CONCAT(DATE_SUB(CURDATE(),INTERVAL 1 DAY),' 18:05:00'),'正常',1,'system',NOW(),1,0),
 ('工程小钱',DATE_SUB(CURDATE(),INTERVAL 1 DAY),NULL,NULL,'缺勤',1,'system',NOW(),1,0);

INSERT INTO oa_document (doc_no,title,doc_type,from_unit,content,status,sign_by,sign_time,tenant_id,create_by,create_time,version,deleted) VALUES
 ('园发〔2026〕12号','关于开展夏季安全生产大检查的通知','发文','综合部','各部门:为切实做好…',3,'张总',DATE_SUB(NOW(),INTERVAL 2 DAY),1,'system',DATE_SUB(NOW(),INTERVAL 5 DAY),1,0),
 ('区科函〔2026〕88号','关于申报区级孵化器认定的函','收文','区科技局','请符合条件的园区于…',2,NULL,NULL,1,'system',DATE_SUB(NOW(),INTERVAL 1 DAY),1,0);

INSERT INTO oa_flow (flow_name,biz_type,steps,status,remark,tenant_id,create_by,create_time,version,deleted) VALUES
 ('合同审批流','contract','[{"step":"招商经理"},{"step":"财务"},{"step":"园区负责人"}]',1,'合同生效前审批链',1,'system',NOW(),1,0),
 ('退款审批流','refund','[{"step":"财务"},{"step":"园区负责人"}]',1,NULL,1,'system',NOW(),1,0),
 ('装修审批流','decoration','[{"step":"物业调度"},{"step":"工程部"},{"step":"消防专员"}]',1,NULL,1,'system',NOW(),1,0);

INSERT INTO crm_customer (name,contact,phone,industry,demand_area,intent_level,source_lead_id,owner,status,remark,tenant_id,create_by,create_time,version,deleted) VALUES
 ('海纳数据','吴总','13700000002','大数据','500-800㎡','A',2,'招商小李',1,'预算充足,两周内定',1,'system',NOW(),1,0),
 ('恒信咨询','冯女士','13700000004','咨询服务','200-300㎡','A',4,'招商小李',2,'已签约HT2026003',1,'system',DATE_SUB(NOW(),INTERVAL 30 DAY),1,0),
 ('微光文创','郑先生','13700000003','文化创意','100-200㎡','C',3,'招商小李',3,'预算不足暂缓',1,'system',DATE_SUB(NOW(),INTERVAL 10 DAY),1,0);

INSERT INTO crm_plan (title,owner,period,target_amount,achieved_amount,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('2026 Q3 招商冲刺','招商小李','2026-Q3',1500000.00,682800.00,1,1,'system',NOW(),1,0),
 ('2026 Q2 去化计划','招商小李','2026-Q2',1000000.00,1124800.00,2,1,'system',DATE_SUB(NOW(),INTERVAL 90 DAY),1,0);

INSERT INTO crm_commission (channel_id,contract_id,base_amount,rate,commission,status,settle_time,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,3,374400.00,2.50,9360.00,1,NULL,1,'system',NOW(),1,0),
 (2,2,499200.00,1.80,8985.60,2,DATE_SUB(NOW(),INTERVAL 15 DAY),1,'system',DATE_SUB(NOW(),INTERVAL 20 DAY),1,0);
