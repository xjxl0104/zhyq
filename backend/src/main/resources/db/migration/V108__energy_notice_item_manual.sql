-- V108 通知单明细:手工新增收费项 / 手工改过的备注
-- 通知单调整时可以直接新增收费行(manual=1),也可以改任意一行的备注(remark_edited=1);
-- 重新生成通知单时这两类手工内容按户找回。纯新增两列,不动已有数据。
ALTER TABLE eng_fee_notice_item
    ADD COLUMN manual        TINYINT NOT NULL DEFAULT 0 COMMENT '1=手工新增的收费行' AFTER remark,
    ADD COLUMN remark_edited TINYINT NOT NULL DEFAULT 0 COMMENT '1=备注是手工改过的,重新生成时保留' AFTER manual;
