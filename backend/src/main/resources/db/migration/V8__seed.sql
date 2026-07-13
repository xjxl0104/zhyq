-- =====================================================================
-- V8 种子数据:让驾驶舱、大屏、各列表页有真实可视数据
-- =====================================================================

-- 系统:部门/岗位/角色/用户/字典
INSERT INTO sys_dept (id,parent_id,name,leader,phone,sort,status,data_scope,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,0,'智慧园区运营集团','张总','13800000001',1,1,1,1,'system',NOW(),1,0),
 (2,1,'招商中心','李经理','13800000002',1,1,2,1,'system',NOW(),1,0),
 (3,1,'财务部','王会计','13800000003',2,1,2,1,'system',NOW(),1,0),
 (4,1,'物业服务部','赵主管','13800000004',3,1,2,1,'system',NOW(),1,0),
 (5,1,'工程设备部','钱工','13800000005',4,1,2,1,'system',NOW(),1,0);

INSERT INTO sys_post (code,name,sort,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('GM','总经理',1,1,1,'system',NOW(),1,0),
 ('SALES','招商经理',2,1,1,'system',NOW(),1,0),
 ('FIN','财务专员',3,1,1,'system',NOW(),1,0),
 ('PM','物业调度',4,1,1,'system',NOW(),1,0);

INSERT INTO sys_role (code,name,data_scope,sort,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('admin','平台超级管理员',1,1,1,1,'system',NOW(),1,0),
 ('park_manager','园区负责人',1,2,1,1,'system',NOW(),1,0),
 ('sales_manager','招商经理',3,3,1,1,'system',NOW(),1,0),
 ('finance','财务人员',1,4,1,1,'system',NOW(),1,0),
 ('pm_dispatch','物业调度',2,5,1,1,'system',NOW(),1,0);

INSERT INTO sys_user (username,nickname,dept_id,post_id,phone,email,gender,user_type,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('admin','超级管理员',1,1,'13800000001','admin@zhyq.com',1,1,1,1,'system',NOW(),1,0),
 ('zhaoshang','招商小李',2,2,'13800000002','sales@zhyq.com',2,1,1,1,'system',NOW(),1,0),
 ('caiwu','财务小王',3,3,'13800000003','fin@zhyq.com',2,1,1,1,'system',NOW(),1,0),
 ('wuye','物业小赵',4,4,'13800000004','pm@zhyq.com',1,1,1,1,'system',NOW(),1,0);

INSERT INTO sys_dict_type (dict_type,dict_name,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('room_status','房源状态',1,1,'system',NOW(),1,0),
 ('contract_status','合同状态',1,1,'system',NOW(),1,0),
 ('bill_status','账单状态',1,1,'system',NOW(),1,0);
INSERT INTO sys_dict_data (dict_type,label,value,color,sort,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('room_status','可租','1','success',1,1,1,'system',NOW(),1,0),
 ('room_status','在租','5','primary',2,1,1,'system',NOW(),1,0),
 ('room_status','维修','7','danger',3,1,1,'system',NOW(),1,0),
 ('bill_status','待收付','3','warning',1,1,1,'system',NOW(),1,0),
 ('bill_status','已结清','5','success',2,1,1,'system',NOW(),1,0),
 ('bill_status','逾期','6','danger',3,1,1,'system',NOW(),1,0);

-- 项目/楼宇/楼层
INSERT INTO biz_project (id,code,name,type,province,city,district,address,manage_area,build_area,status,manager,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,'PRJ001','未来科技产业园','产业园','广东省','深圳市','南山区','科技园路1号',85000.00,120000.00,1,'张总',1,'system',NOW(),1,0),
 (2,'PRJ002','滨江国际写字楼','写字楼','广东省','深圳市','福田区','滨江大道88号',42000.00,60000.00,1,'张总',1,'system',NOW(),1,0);

INSERT INTO biz_building (id,project_id,code,name,floor_count,build_area,usage_type,status,sort,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,1,'B-A','A栋研发楼',12,40000.00,'办公',1,1,1,'system',NOW(),1,0),
 (2,1,'B-B','B栋孵化楼',10,30000.00,'办公',1,2,1,'system',NOW(),1,0),
 (3,2,'B-T','主塔楼',28,60000.00,'办公',1,1,1,'system',NOW(),1,0);

INSERT INTO biz_floor (id,building_id,project_id,name,floor_no,build_area,sort,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,1,1,'3F',3,3200.00,3,1,'system',NOW(),1,0),
 (2,1,1,'5F',5,3200.00,5,1,'system',NOW(),1,0),
 (3,2,1,'2F',2,3000.00,2,1,'system',NOW(),1,0),
 (4,3,2,'10F',10,2100.00,10,1,'system',NOW(),1,0);

-- 房间(不同状态,给大屏饼图数据)
INSERT INTO biz_room (floor_id,building_id,project_id,code,room_no,status,build_area,rent_area,use_area,orientation,decoration,usage_type,base_price,property_fee,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,1,1,'RM-A0301','A-301',5,320.00,300.00,260.00,'南','精装','办公',120.00,25.00,1,'system',NOW(),1,0),
 (1,1,1,'RM-A0302','A-302',5,280.00,260.00,230.00,'南','精装','办公',120.00,25.00,1,'system',NOW(),1,0),
 (1,1,1,'RM-A0303','A-303',1,300.00,280.00,240.00,'北','简装','办公',110.00,25.00,1,'system',NOW(),1,0),
 (2,1,1,'RM-A0501','A-501',5,350.00,320.00,280.00,'南','精装','办公',130.00,28.00,1,'system',NOW(),1,0),
 (2,1,1,'RM-A0502','A-502',3,340.00,310.00,270.00,'南','精装','办公',130.00,28.00,1,'system',NOW(),1,0),
 (3,2,1,'RM-B0201','B-201',1,500.00,460.00,400.00,'东','毛坯','办公',95.00,20.00,1,'system',NOW(),1,0),
 (3,2,1,'RM-B0202','B-202',7,480.00,440.00,380.00,'西','简装','办公',95.00,20.00,1,'system',NOW(),1,0),
 (4,3,2,'RM-T1001','T-1001',5,210.00,195.00,170.00,'南','精装','办公',160.00,32.00,1,'system',NOW(),1,0),
 (4,3,2,'RM-T1002','T-1002',4,220.00,200.00,175.00,'南','精装','办公',160.00,32.00,1,'system',NOW(),1,0),
 (4,3,2,'RM-T1003','T-1003',1,200.00,185.00,160.00,'北','简装','办公',150.00,32.00,1,'system',NOW(),1,0);

-- 租客
INSERT INTO biz_tenant (id,code,name,tenant_type,invite_code,contact,phone,project_id,industry,tags,credit_code,legal_person,reg_address,establish_date,status,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,'T001','云智能科技有限公司',1,'INV001','刘经理','13900000001',1,'人工智能','高新企业,重点客户','91440300MA5XXXXX01','刘明','深圳市南山区',DATE '2019-03-15',1,1,'system',NOW(),1,0),
 (2,'T002','绿源环保股份公司',1,'INV002','陈总','13900000002',1,'环保科技','上市公司','91440300MA5XXXXX02','陈刚',' 深圳市南山区',DATE '2016-07-20',1,1,'system',NOW(),1,0),
 (3,'T003','速达物流有限公司',1,'INV003','孙经理','13900000003',2,'物流','','91440300MA5XXXXX03','孙丽','深圳市福田区',DATE '2020-11-01',1,1,'system',NOW(),1,0),
 (4,'T004','个人租客-周先生',2,'INV004','周先生','13900000004',2,'个体','',NULL,NULL,NULL,NULL,1,1,'system',NOW(),1,0);

-- 租客员工
INSERT INTO biz_tenant_staff (tenant_ref_id,name,dept,post,phone,plate_no,valid_end,status,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,'刘明','行政部','总经理','13900000001','粤B12345',DATE '2027-12-31',1,1,'system',NOW(),1,0),
 (1,'王芳','研发部','工程师','13900000011','粤B12346',DATE '2027-12-31',1,1,'system',NOW(),1,0),
 (2,'陈刚','管理层','董事长','13900000002','粤B22345',DATE '2027-12-31',1,1,'system',NOW(),1,0);

-- 招商线索(不同状态给漏斗)
INSERT INTO crm_lead (contact,phone,company,status,source,intent_project,demand_area,owner_id,next_follow,remark,tenant_id,create_by,create_time,version,deleted) VALUES
 ('黄经理','13700000001','星辰生物科技','3','网页',1,'300-500㎡',2,DATE_ADD(NOW(),INTERVAL 2 DAY),'意向较强',1,'system',NOW(),1,0),
 ('吴总','13700000002','海纳数据','4','渠道',1,'500-800㎡',2,DATE_ADD(NOW(),INTERVAL 1 DAY),'已看房',1,'system',NOW(),1,0),
 ('郑先生','13700000003','微光文创','3','小程序',2,'100-200㎡',2,DATE_ADD(NOW(),INTERVAL 3 DAY),'',1,'system',NOW(),1,0),
 ('冯女士','13700000004','恒信咨询','5','活动',1,'200-300㎡',2,NULL,'已签约转租客',1,'system',NOW(),1,0),
 ('褚经理','13700000005','未知来源','6','电话',NULL,'',2,NULL,'空号无效',1,'system',NOW(),1,0),
 ('卫总','13700000006','公海线索A','7','网页',2,'',NULL,NULL,'待认领',1,'system',NOW(),1,0);

INSERT INTO crm_channel (name,contact,phone,commission_rate,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('世联行招商代理','代理甲','13600000001',2.50,1,1,'system',NOW(),1,0),
 ('本地商会渠道','代理乙','13600000002',1.80,1,1,'system',NOW(),1,0);

-- 合同(执行中,并手工配套账单)
INSERT INTO biz_contract (id,code,tenant_ref_id,project_id,contract_type,status,start_date,end_date,sign_date,rent_price,property_price,rent_area,deposit,charge_mode,pay_cycle,free_months,increase_rate,source,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,'HT2026001',1,1,1,5,DATE '2026-01-01',DATE '2027-12-31',DATE '2025-12-20',120.00,25.00,300.00,108000.00,1,3,1,5.00,'招商',1,'system',NOW(),1,0),
 (2,'HT2026002',2,1,1,5,DATE '2026-03-01',DATE '2028-02-29',DATE '2026-02-15',130.00,28.00,320.00,124800.00,1,3,0,5.00,'招商',1,'system',NOW(),1,0),
 (3,'HT2026003',3,2,1,2,DATE '2026-08-01',DATE '2027-07-31',NULL,160.00,32.00,195.00,93600.00,1,6,0,3.00,'渠道',1,'system',NOW(),1,0);

INSERT INTO biz_contract_room (contract_id,room_id,rent_area,rent_price,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,1,300.00,120.00,1,'system',NOW(),1,0),
 (2,4,320.00,130.00,1,'system',NOW(),1,0);

-- 账单(含已结清/待收/逾期,给财务和大屏趋势)
INSERT INTO fin_bill (code,contract_id,tenant_ref_id,project_id,building_id,room_id,direction,fee_type,source,status,amount,paid_amount,late_fee,tax_rate,period_start,period_end,due_date,overdue_days,tenant_id,create_by,create_time,version,deleted) VALUES
 ('ZD2026010001',1,1,1,1,1,1,'租金','合同计划',5,108000.00,108000.00,0.00,6.00,DATE '2026-01-01',DATE '2026-03-31',DATE '2026-01-01',0,1,'system',DATE_SUB(NOW(),INTERVAL 150 DAY),1,0),
 ('ZD2026040002',1,1,1,1,1,1,'租金','合同计划',5,108000.00,108000.00,0.00,6.00,DATE '2026-04-01',DATE '2026-06-30',DATE '2026-04-01',0,1,'system',DATE_SUB(NOW(),INTERVAL 90 DAY),1,0),
 ('ZD2026070003',1,1,1,1,1,1,'租金','合同计划',6,108000.00,0.00,540.00,6.00,DATE '2026-07-01',DATE '2026-09-30',DATE_SUB(CURDATE(),INTERVAL 10 DAY),10,1,'system',DATE_SUB(NOW(),INTERVAL 10 DAY),1,0),
 ('ZD2026030004',2,2,1,1,4,1,'租金','合同计划',5,124800.00,124800.00,0.00,6.00,DATE '2026-03-01',DATE '2026-05-31',DATE '2026-03-01',0,1,'system',DATE_SUB(NOW(),INTERVAL 120 DAY),1,0),
 ('ZD2026060005',2,2,1,1,4,1,'租金','合同计划',3,124800.00,0.00,0.00,6.00,DATE '2026-06-01',DATE '2026-08-31',DATE_ADD(CURDATE(),INTERVAL 5 DAY),0,1,'system',DATE_SUB(NOW(),INTERVAL 20 DAY),1,0),
 ('ZD2026010006',1,1,1,1,1,1,'保证金','合同计划',5,108000.00,108000.00,0.00,0.00,DATE '2026-01-01',DATE '2026-01-01',DATE '2026-01-01',0,1,'system',DATE_SUB(NOW(),INTERVAL 150 DAY),1,0);

-- 物业工单(不同状态)
INSERT INTO pm_work_order (code,order_type,title,project_id,building_id,room_id,location,category,urgency,status,source,contact,contact_phone,assignee,arrive_time,finish_time,score,tenant_id,create_by,create_time,version,deleted) VALUES
 ('GD2026001','报修','A-301空调不制冷',1,1,1,'A栋301室','空调',3,5,'租客端','刘明','13900000001','物业小赵',DATE_SUB(NOW(),INTERVAL 3 DAY),DATE_SUB(NOW(),INTERVAL 2 DAY),5,1,'system',DATE_SUB(NOW(),INTERVAL 3 DAY),1,0),
 ('GD2026002','报修','公共区照明故障',1,1,NULL,'A栋3层走廊','电气',2,3,'巡检','赵主管','13800000004','物业小赵',DATE_SUB(NOW(),INTERVAL 1 DAY),NULL,NULL,1,'system',DATE_SUB(NOW(),INTERVAL 1 DAY),1,0),
 ('GD2026003','报修','B栋卫生间漏水',1,2,NULL,'B栋2层卫生间','给排水',3,1,'租客端','孙丽','13900000003',NULL,NULL,NULL,NULL,1,'system',NOW(),1,0),
 ('GD2026004','巡检','消防通道巡检异常',2,3,NULL,'主塔楼10层','消防',2,2,'巡检','钱工','13800000005','工程小钱',NULL,NULL,NULL,1,'system',NOW(),1,0);

-- 会议室
INSERT INTO pm_meeting_room (name,project_id,capacity,equipment,open_time,fee_rule,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('A栋301会议室',1,20,'投影/白板/视频会议','09:00-21:00','50元/小时',1,1,'system',NOW(),1,0),
 ('B栋大会议室',1,50,'LED屏/音响/同声传译','08:00-22:00','120元/小时',1,1,'system',NOW(),1,0);

-- 能耗表计 + 读数
INSERT INTO eng_meter (id,code,name,energy_type,project_id,building_id,room_id,ratio,last_reading,status,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,'DB-A301','A301电表','电',1,1,1,1.00,12500.00,1,1,'system',NOW(),1,0),
 (2,'SB-A301','A301水表','水',1,1,1,1.00,850.00,1,1,'system',NOW(),1,0),
 (3,'DB-A501','A501电表','电',1,1,4,1.00,9800.00,1,1,'system',NOW(),1,0);
INSERT INTO eng_reading (meter_id,prev_reading,curr_reading,usage_amount,read_source,read_time,fee,tenant_id,create_by,create_time,version,deleted) VALUES
 (1,12000.00,12500.00,500.00,'自动采集',NOW(),650.00,1,'system',NOW(),1,0),
 (2,800.00,850.00,50.00,'人工',NOW(),225.00,1,'system',NOW(),1,0);

-- 物联设备(不同类别和状态,给大屏)
INSERT INTO iot_device (code,name,category,vendor,project_id,building_id,location,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('CAM001','A栋大堂摄像头','摄像头','海康',1,1,'A栋大堂',1,1,'system',NOW(),1,0),
 ('CAM002','B栋入口摄像头','摄像头','海康',1,2,'B栋入口',1,1,'system',NOW(),1,0),
 ('ACC001','A栋门禁01','门禁','海康',1,1,'A栋一层',1,1,'system',NOW(),1,0),
 ('ACC002','B栋门禁01','门禁','大华',1,2,'B栋一层',2,1,'system',NOW(),1,0),
 ('LOCK001','A301智能门锁','门锁','云丁',1,1,'A栋301',1,1,'system',NOW(),1,0),
 ('PARK001','地库道闸','停车','捷顺',1,1,'负一层车库',1,1,'system',NOW(),1,0),
 ('SENSOR001','A栋空气质量','传感器','其它',1,1,'A栋3层',3,1,'system',NOW(),1,0);

INSERT INTO iot_alarm (device_id,alarm_type,level,status,location,content,alarm_time,tenant_id,create_by,create_time,version,deleted) VALUES
 (4,'设备离线',2,1,'B栋一层','门禁ACC002离线超过30分钟',DATE_SUB(NOW(),INTERVAL 2 HOUR),1,'system',NOW(),1,0),
 (7,'空气质量超标',3,2,'A栋3层','PM2.5超过阈值',DATE_SUB(NOW(),INTERVAL 5 HOUR),1,'system',NOW(),1,0);

-- 办公任务 + 公告
INSERT INTO oa_task (title,owner,priority,due_date,status,source,content,tenant_id,create_by,create_time,version,deleted) VALUES
 ('跟进星辰生物看房','招商小李',3,DATE_ADD(NOW(),INTERVAL 2 DAY),1,'招商','安排下周实地看房',1,'system',NOW(),1,0),
 ('7月账单催缴','财务小王',3,DATE_ADD(NOW(),INTERVAL 1 DAY),2,'财务','HT2026001逾期账单催缴',1,'system',NOW(),1,0),
 ('空调维修验收','物业小赵',2,NOW(),3,'物业','A301空调已修复',1,'system',NOW(),1,0);
INSERT INTO oa_notice (title,content,publish_time,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('园区7月物业费缴纳通知','请各企业于7月20日前完成缴纳',NOW(),1,1,'system',NOW(),1,0),
 ('夏季用电安全提示','高温期间请注意安全用电',DATE_SUB(NOW(),INTERVAL 5 DAY),1,1,'system',NOW(),1,0);

-- 访客 + 商城
INSERT INTO srv_visitor (visitor_name,phone,host,tenant_ref_id,visit_time,reason,plate_no,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('访客张先生','13500000001','刘明',1,DATE_ADD(NOW(),INTERVAL 1 DAY),'商务洽谈','粤B99999',1,1,'system',NOW(),1,0),
 ('访客李女士','13500000002','陈刚',2,NOW(),'设备维护','',2,1,'system',NOW(),1,0);
INSERT INTO srv_product (name,product_type,price,stock,sales,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('工位租赁月卡','服务',1500.00,100,28,1,1,'system',NOW(),1,0),
 ('会议室钟点券','虚拟',50.00,500,120,1,1,'system',NOW(),1,0),
 ('园区咖啡券','实物',30.00,300,86,1,1,'system',NOW(),1,0);

-- 统一待办
INSERT INTO sys_todo (title,biz_type,biz_id,owner,due_date,status,tenant_id,create_by,create_time,version,deleted) VALUES
 ('合同HT2026003待审核','contract',3,'admin',DATE_ADD(NOW(),INTERVAL 2 DAY),1,1,'system',NOW(),1,0),
 ('账单ZD2026070003逾期催缴','bill',3,'财务小王',DATE_ADD(NOW(),INTERVAL 1 DAY),1,1,'system',NOW(),1,0),
 ('工单GD2026003待派单','workorder',3,'物业小赵',NOW(),1,1,'system',NOW(),1,0),
 ('线索星辰生物待跟进','lead',1,'招商小李',DATE_ADD(NOW(),INTERVAL 2 DAY),1,1,'system',NOW(),1,0);

-- 审批实例
INSERT INTO biz_approval (biz_type,biz_id,title,status,apply_by,tenant_id,create_by,create_time,version,deleted) VALUES
 ('contract',3,'合同HT2026003审批','2','招商小李',1,'system',NOW(),1,0);
