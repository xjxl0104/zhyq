-- 全平台应用日报按门户成员归人:后台账号对应的门户成员 id(yc_portal_member.id)。
-- 只加可空列,不改已有数据;没填的账号由日报那边按姓名对上门户成员。
ALTER TABLE sys_user ADD COLUMN portal_uid VARCHAR(32) DEFAULT NULL COMMENT '门户成员 id(应用日报归人)' AFTER nickname;
