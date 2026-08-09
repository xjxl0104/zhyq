-- ver6.2:门禁通行记录、访客登记 新增审批人字段
ALTER TABLE acc_access_record ADD COLUMN approver VARCHAR(64) NULL COMMENT '审批人' AFTER access_time;
ALTER TABLE acc_visitor ADD COLUMN approver VARCHAR(64) NULL COMMENT '审批人' AFTER status;
