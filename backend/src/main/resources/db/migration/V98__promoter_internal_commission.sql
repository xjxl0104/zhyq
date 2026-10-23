-- 内部人员(手机号命中后台用户)与普通伙伴一样计佣:清除已有的内部人员标记
UPDATE crm_promoter SET is_internal = 0 WHERE is_internal = 1;
