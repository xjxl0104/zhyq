-- =====================================================================
-- V13 批2:惠企九项
-- =====================================================================

-- 物品放行
CREATE TABLE srv_pass (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pass_no VARCHAR(64) NOT NULL COMMENT '放行单号',
    item VARCHAR(255) NOT NULL COMMENT '物品',
    qty INT NOT NULL DEFAULT 1,
    carrier VARCHAR(64) COMMENT '携带人',
    phone VARCHAR(20),
    plate_no VARCHAR(32),
    tenant_ref_id BIGINT,
    authorizer VARCHAR(32) COMMENT '授权人',
    valid_until DATETIME COMMENT '有效期',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1待核验 2已放行 3已失效',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '物品放行';

-- 场地预约
CREATE TABLE srv_site_booking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_name VARCHAR(128) NOT NULL COMMENT '场地:中心广场/多功能厅/路演厅/球场',
    booker VARCHAR(64), phone VARCHAR(20),
    tenant_ref_id BIGINT,
    start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
    purpose VARCHAR(255) COMMENT '用途',
    fee DECIMAL(12,2) DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1待审批 2已通过 3已取消',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_site (site_name)
) COMMENT '场地预约';

-- 装修申请
CREATE TABLE srv_decoration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_ref_id BIGINT NOT NULL COMMENT '申请租客',
    room_id BIGINT COMMENT '装修房间',
    contractor VARCHAR(128) COMMENT '施工单位',
    contact VARCHAR(64), phone VARCHAR(20),
    start_date DATE, end_date DATE,
    scope VARCHAR(500) COMMENT '装修范围说明',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1待审批 2施工中 3已完工 4已驳回',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '装修申请';

-- 园区论坛
CREATE TABLE srv_forum_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    author VARCHAR(64),
    tenant_ref_id BIGINT,
    category VARCHAR(32) DEFAULT '闲聊' COMMENT '闲聊/求助/二手/活动',
    reply_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1待审核 2已发布 3已下架',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_forum (status)
) COMMENT '论坛帖子';

CREATE TABLE srv_forum_reply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    content VARCHAR(1000),
    author VARCHAR(64),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_reply (post_id)
) COMMENT '论坛回复';

-- 出入证
CREATE TABLE srv_pass_card (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_no VARCHAR(64) NOT NULL COMMENT '证件号',
    holder VARCHAR(64) NOT NULL COMMENT '持有人',
    phone VARCHAR(20),
    tenant_ref_id BIGINT,
    card_type VARCHAR(16) NOT NULL DEFAULT '员工' COMMENT '员工/临时/车辆',
    valid_start DATE, valid_end DATE,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 2已挂失 3已注销',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_card (card_no)
) COMMENT '出入证';

-- 申报服务(政策申报)
CREATE TABLE srv_declare (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '申报项目',
    dtype VARCHAR(32) COMMENT '类型:高企认定/专精特新/研发补贴/人才补贴',
    tenant_ref_id BIGINT COMMENT '申报企业',
    materials VARCHAR(500) COMMENT '材料清单',
    deadline DATE,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1材料准备 2已提交 3已通过 4未通过',
    remark VARCHAR(255),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '申报服务';

-- 知产服务
CREATE TABLE srv_ip (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '知产名称',
    ip_type VARCHAR(16) NOT NULL DEFAULT '专利' COMMENT '专利/商标/版权/软著',
    tenant_ref_id BIGINT,
    agency VARCHAR(128) COMMENT '代理机构',
    apply_date DATE,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1申请中 2已受理 3已授权 4已驳回',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '知产服务';

-- 政策服务(政策库)
CREATE TABLE srv_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(128) COMMENT '发布单位',
    ptype VARCHAR(32) COMMENT '类型:税收/人才/研发/租金补贴',
    industry VARCHAR(64) COMMENT '适用行业(空=通用)',
    publish_date DATE,
    deadline DATE COMMENT '申报截止',
    content TEXT,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 0已过期',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '政策库';

-- ===== 种子 =====
INSERT INTO srv_pass (pass_no,item,qty,carrier,phone,plate_no,tenant_ref_id,authorizer,valid_until,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('FX2026001','服务器设备×2',2,'王工','13600000011','粤B88888',1,'刘明',DATE_ADD(NOW(),INTERVAL 1 DAY),1,1,'system',NOW(),1,0),
 ('FX2026002','办公桌椅一批',10,'搬家公司张师傅','13600000012','粤BD1234',2,'陈刚',DATE_SUB(NOW(),INTERVAL 1 DAY),2,1,'system',DATE_SUB(NOW(),INTERVAL 2 DAY),1,0);

INSERT INTO srv_site_booking (site_name,booker,phone,tenant_ref_id,start_time,end_time,purpose,fee,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('多功能厅','刘明','13900000001',1,DATE_ADD(NOW(),INTERVAL 3 DAY),DATE_ADD(NOW(),INTERVAL 3 DAY)+INTERVAL 4 HOUR,'产品发布会',2000.00,2,1,'system',NOW(),1,0),
 ('中心广场','孙丽','13900000003',3,DATE_ADD(NOW(),INTERVAL 7 DAY),DATE_ADD(NOW(),INTERVAL 7 DAY)+INTERVAL 8 HOUR,'招聘市集',0.00,1,1,'system',NOW(),1,0);

INSERT INTO srv_decoration (tenant_ref_id,room_id,contractor,contact,phone,start_date,end_date,scope,status,tenant_id,create_by,create_time,version,deleted) VALUES
 (3,8,'广美装饰工程','李工','13600000021',DATE_ADD(CURDATE(),INTERVAL 5 DAY),DATE_ADD(CURDATE(),INTERVAL 35 DAY),'隔断改造+地面翻新,涉及消防喷淋改位',1,1,'system',NOW(),1,0),
 (1,1,'自装','刘明','13900000001',DATE_SUB(CURDATE(),INTERVAL 60 DAY),DATE_SUB(CURDATE(),INTERVAL 30 DAY),'墙面刷新',3,1,'system',DATE_SUB(NOW(),INTERVAL 60 DAY),1,0);

INSERT INTO srv_forum_post (title,content,author,tenant_ref_id,category,reply_count,like_count,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('求推荐园区附近好吃的午餐','刚入驻,求老租户带路','新来的程序员',1,'求助',2,5,2,1,'system',DATE_SUB(NOW(),INTERVAL 1 DAY),1,0),
 ('转让九成新人体工学椅','搬办公室便宜出,300一把共5把','行政小妹',2,'二手',0,3,2,1,'system',NOW(),1,0),
 ('这是一条待审核的帖子','测试内容审核流程','匿名用户',NULL,'闲聊',0,0,1,1,'system',NOW(),1,0);

INSERT INTO srv_forum_reply (post_id,content,author,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,'B栋后门的湘菜馆强烈推荐','老租户甲',1,'system',NOW(),1,0),
 (1,'食堂三楼窗口性价比最高','老租户乙',1,'system',NOW(),1,0);

INSERT INTO srv_pass_card (card_no,holder,phone,tenant_ref_id,card_type,valid_start,valid_end,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('MG0001','刘明','13900000001',1,'员工',DATE '2026-01-01',DATE '2027-12-31',1,1,'system',NOW(),1,0),
 ('MG0002','王芳','13900000011',1,'员工',DATE '2026-01-01',DATE '2027-12-31',2,1,'system',NOW(),1,0),
 ('LS0001','装修施工队','13600000021',3,'临时',CURDATE(),DATE_ADD(CURDATE(),INTERVAL 40 DAY),1,1,'system',NOW(),1,0);

INSERT INTO srv_declare (title,dtype,tenant_ref_id,materials,deadline,status,remark,tenant_id,create_by,create_time,version,deleted) VALUES
 ('2026年度高新技术企业认定','高企认定',1,'营业执照/审计报告/知产证明/研发费用归集表',DATE_ADD(CURDATE(),INTERVAL 45 DAY),1,NULL,1,'system',NOW(),1,0),
 ('专精特新中小企业申报','专精特新',2,'企业简介/财务报表/主导产品说明',DATE_SUB(CURDATE(),INTERVAL 10 DAY),3,'已获批,奖励30万',1,'system',DATE_SUB(NOW(),INTERVAL 90 DAY),1,0);

INSERT INTO srv_ip (title,ip_type,tenant_ref_id,agency,apply_date,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('一种基于AI的园区能耗预测方法','专利',1,'中知专利代理',DATE_SUB(CURDATE(),INTERVAL 120 DAY),2,1,'system',NOW(),1,0),
 ('云智能LOGO','商标',1,'八戒知产',DATE_SUB(CURDATE(),INTERVAL 200 DAY),3,1,'system',NOW(),1,0),
 ('绿源环保管理系统V1.0','软著',2,'自行申请',DATE_SUB(CURDATE(),INTERVAL 30 DAY),1,1,'system',NOW(),1,0);

INSERT INTO srv_policy (title,source,ptype,industry,publish_date,deadline,content,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('高新技术企业所得税减免15%','市科技局','税收',NULL,DATE_SUB(CURDATE(),INTERVAL 100 DAY),NULL,'认定为高企的按15%征收企业所得税。',1,1,'system',NOW(),1,0),
 ('园区人才公寓租金补贴','区人社局','人才',NULL,DATE_SUB(CURDATE(),INTERVAL 30 DAY),DATE_ADD(CURDATE(),INTERVAL 60 DAY),'硕士及以上学历入驻企业员工可申请每月800元租金补贴。',1,1,'system',NOW(),1,0),
 ('人工智能企业研发费用加计扣除','市财政局','研发','人工智能',DATE_SUB(CURDATE(),INTERVAL 200 DAY),DATE_SUB(CURDATE(),INTERVAL 10 DAY),'AI企业研发费用按120%加计扣除,申报已截止。',0,1,'system',NOW(),1,0);
