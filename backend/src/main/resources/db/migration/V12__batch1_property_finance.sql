-- =====================================================================
-- V12 批1:物业八小件 + 财务补全 + 通用业务配置
-- =====================================================================

-- 通用业务配置(财务设置/合同设置/招商配置/生态配置 共用)
CREATE TABLE biz_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    module VARCHAR(32) NOT NULL COMMENT '模块:finance/contract/crm/ecosystem',
    skey   VARCHAR(64) NOT NULL COMMENT '配置键',
    svalue VARCHAR(500) COMMENT '配置值',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_setting (module, skey)
) COMMENT '业务配置';

-- 投诉建议/意见反馈(type 区分)
CREATE TABLE pm_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ftype VARCHAR(16) NOT NULL DEFAULT '投诉' COMMENT '投诉/意见',
    title VARCHAR(255) NOT NULL,
    content VARCHAR(1000),
    tenant_ref_id BIGINT COMMENT '提交租客',
    contact VARCHAR(64), phone VARCHAR(20),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1待处理 2处理中 3已办结',
    reply VARCHAR(500) COMMENT '处理回复',
    handler VARCHAR(32) COMMENT '处理人',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_fb (ftype, status)
) COMMENT '投诉与意见反馈';

-- 投票问卷
CREATE TABLE pm_survey (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    stype VARCHAR(16) NOT NULL DEFAULT '投票' COMMENT '投票/问卷',
    options TEXT COMMENT '选项JSON,如 [{"label":"满意","votes":12}]',
    deadline DATETIME,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1进行中 2已结束',
    votes INT NOT NULL DEFAULT 0 COMMENT '参与人数',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '投票问卷';

-- 物业活动
CREATE TABLE pm_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(1000),
    location VARCHAR(255),
    start_time DATETIME, end_time DATETIME,
    enroll_count INT NOT NULL DEFAULT 0 COMMENT '报名人数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1报名中 2进行中 3已结束',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '物业活动';

-- 安防巡更记录
CREATE TABLE pm_patrol (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_name VARCHAR(128) NOT NULL COMMENT '巡更路线',
    point VARCHAR(128) COMMENT '点位',
    patroller VARCHAR(32) COMMENT '巡更人',
    patrol_time DATETIME,
    result VARCHAR(16) NOT NULL DEFAULT '正常' COMMENT '正常/异常',
    remark VARCHAR(500),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_patrol (result)
) COMMENT '安防巡更';

-- 保洁/绿化/品质 检查(ctype 区分)
CREATE TABLE pm_check (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ctype VARCHAR(16) NOT NULL COMMENT '保洁/绿化/品质',
    location VARCHAR(255) NOT NULL COMMENT '检查位置',
    checker VARCHAR(32) COMMENT '检查人',
    check_time DATETIME,
    score TINYINT COMMENT '评分1-10',
    issues VARCHAR(500) COMMENT '发现问题',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1合格 2待整改 3已整改',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_check (ctype, status)
) COMMENT '三检(保洁绿化品质)';

-- 收据
CREATE TABLE fin_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_no VARCHAR(64) NOT NULL COMMENT '收据号',
    payment_id BIGINT COMMENT '关联收款单',
    bill_id BIGINT,
    tenant_ref_id BIGINT,
    amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    payee VARCHAR(64) COMMENT '收款人',
    print_count INT NOT NULL DEFAULT 0 COMMENT '打印次数',
    last_print_time DATETIME,
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_receipt_no (receipt_no)
) COMMENT '收据';

-- 收据打印日志
CREATE TABLE fin_receipt_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    operator VARCHAR(32),
    print_time DATETIME,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_rlog (receipt_id)
) COMMENT '收据打印日志';

-- 收款通知
CREATE TABLE fin_pay_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_no VARCHAR(64) NOT NULL,
    bill_id BIGINT NOT NULL,
    tenant_ref_id BIGINT,
    amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '通知金额(欠款)',
    send_channel VARCHAR(32) DEFAULT '站内信' COMMENT '站内信/短信/微信',
    send_time DATETIME,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1待发送 2已发送',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_pn (bill_id)
) COMMENT '收款通知';

-- ===== 种子 =====
INSERT INTO biz_setting (module,skey,svalue,remark,tenant_id,create_by,create_time,version,deleted) VALUES
 ('finance','late_fee_rate','0.0005','滞纳金日利率(万5)',1,'system',NOW(),1,0),
 ('finance','default_tax_rate','6','默认税率%',1,'system',NOW(),1,0),
 ('finance','receipt_prefix','SJ','收据号前缀',1,'system',NOW(),1,0),
 ('contract','code_prefix','HT','合同编号前缀',1,'system',NOW(),1,0),
 ('contract','expire_remind_days','90','到期提醒提前天数',1,'system',NOW(),1,0),
 ('crm','sea_recycle_days','15','公海回收天数',1,'system',NOW(),1,0),
 ('ecosystem','wecom_corp_id','','企业微信CorpID(未配置)',1,'system',NOW(),1,0),
 ('ecosystem','dingtalk_app_key','','钉钉AppKey(未配置)',1,'system',NOW(),1,0),
 ('ecosystem','mp_app_id','','微信小程序AppID(未配置)',1,'system',NOW(),1,0);

INSERT INTO pm_feedback (ftype,title,content,tenant_ref_id,contact,phone,status,reply,handler,tenant_id,create_by,create_time,version,deleted) VALUES
 ('投诉','电梯等待时间过长','A栋早高峰电梯排队超过10分钟',1,'刘明','13900000001',2,'已联系电梯公司调整调度策略','物业小赵',1,'system',DATE_SUB(NOW(),INTERVAL 2 DAY),1,0),
 ('投诉','地库照明昏暗','负一层B区多盏灯不亮',3,'孙丽','13900000003',1,NULL,NULL,1,'system',NOW(),1,0),
 ('意见','建议增设快递柜','B栋大堂快递堆放杂乱,建议增设智能快递柜',2,'陈刚','13900000002',3,'已采纳,快递柜下月安装','物业小赵',1,'system',DATE_SUB(NOW(),INTERVAL 5 DAY),1,0);

INSERT INTO pm_survey (title,stype,options,deadline,status,votes,tenant_id,create_by,create_time,version,deleted) VALUES
 ('园区食堂满意度调查','问卷','[{"label":"非常满意","votes":18},{"label":"满意","votes":32},{"label":"一般","votes":12},{"label":"不满意","votes":5}]',DATE_ADD(NOW(),INTERVAL 7 DAY),1,67,1,'system',NOW(),1,0),
 ('是否支持周末开放屋顶花园','投票','[{"label":"支持","votes":45},{"label":"不支持","votes":8}]',DATE_SUB(NOW(),INTERVAL 1 DAY),2,53,1,'system',DATE_SUB(NOW(),INTERVAL 10 DAY),1,0);

INSERT INTO pm_activity (title,content,location,start_time,end_time,enroll_count,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('园区夏日消暑节','冷饮市集+乐队演出','中心广场',DATE_ADD(NOW(),INTERVAL 10 DAY),DATE_ADD(NOW(),INTERVAL 11 DAY),86,1,1,'system',NOW(),1,0),
 ('入驻企业篮球赛','八队循环赛','园区球场',DATE_SUB(NOW(),INTERVAL 20 DAY),DATE_SUB(NOW(),INTERVAL 15 DAY),120,3,1,'system',DATE_SUB(NOW(),INTERVAL 30 DAY),1,0);

INSERT INTO pm_patrol (route_name,point,patroller,patrol_time,result,remark,tenant_id,create_by,create_time,version,deleted) VALUES
 ('园区周界巡更','东门',  '保安甲',DATE_SUB(NOW(),INTERVAL 2 HOUR),'正常',NULL,1,'system',NOW(),1,0),
 ('园区周界巡更','地库入口','保安甲',DATE_SUB(NOW(),INTERVAL 1 HOUR),'异常','道闸感应迟缓,已报修',1,'system',NOW(),1,0),
 ('A栋楼内巡更','消防通道','保安乙',DATE_SUB(NOW(),INTERVAL 3 HOUR),'正常',NULL,1,'system',NOW(),1,0);

INSERT INTO pm_check (ctype,location,checker,check_time,score,issues,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('保洁','A栋大堂','保洁主管',DATE_SUB(NOW(),INTERVAL 1 DAY),9,NULL,1,1,'system',NOW(),1,0),
 ('绿化','中心广场绿化带','绿化员',DATE_SUB(NOW(),INTERVAL 2 DAY),6,'部分灌木枯黄需补种',2,1,'system',NOW(),1,0),
 ('品质','B栋公共卫生间','品质专员',DATE_SUB(NOW(),INTERVAL 3 DAY),7,'洗手台水渍未及时清理',3,1,'system',NOW(),1,0);

INSERT INTO fin_receipt (receipt_no,payment_id,bill_id,tenant_ref_id,amount,payee,print_count,last_print_time,tenant_id,create_by,create_time,version,deleted) VALUES
 ('SJ2026070001',1,5,2,50000.00,'财务小王',1,DATE_SUB(NOW(),INTERVAL 1 DAY),1,'system',DATE_SUB(NOW(),INTERVAL 1 DAY),1,0),
 ('SJ2026070002',2,5,2,74800.00,'财务小王',0,NULL,1,'system',NOW(),1,0);

INSERT INTO fin_receipt_log (receipt_id,operator,print_time,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,'财务小王',DATE_SUB(NOW(),INTERVAL 1 DAY),1,'system',NOW(),1,0);

INSERT INTO fin_pay_notice (notice_no,bill_id,tenant_ref_id,amount,send_channel,send_time,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('TZ2026070001',3,1,108540.00,'站内信',DATE_SUB(NOW(),INTERVAL 1 DAY),2,1,'system',DATE_SUB(NOW(),INTERVAL 1 DAY),1,0),
 ('TZ2026070002',5,2,124800.00,'短信',NULL,1,1,'system',NOW(),1,0);
