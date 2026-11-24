-- The park accepts applications from the 25th through the 30th, inclusive.
UPDATE biz_setting
SET svalue = CASE skey WHEN 'withdraw_window_start_day' THEN '25' ELSE '30' END,
    update_by = 'system', update_time = NOW(), version = version + 1
WHERE module = 'marketing' AND deleted = 0
  AND skey IN ('withdraw_window_start_day','withdraw_window_end_day');

INSERT INTO biz_setting (module,skey,svalue,remark,tenant_id,create_by,create_time,version,deleted)
SELECT 'marketing', d.skey, d.svalue, d.remark, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'withdraw_window_start_day' skey,'25' svalue,'提现申请开放起始日（含）' remark
    UNION ALL SELECT 'withdraw_window_end_day','30','提现申请开放截止日（含），短月截至月末'
) d
WHERE NOT EXISTS (SELECT 1 FROM biz_setting s WHERE s.module='marketing' AND s.skey=d.skey AND s.deleted=0);

-- Align previously enabled automatic submissions with their effective day; retain disabled schedules.
UPDATE biz_setting SET svalue='25',update_by='system',update_time=NOW(),version=version+1
WHERE module='marketing' AND skey='auto_withdraw_day' AND deleted=0
  AND CAST(svalue AS SIGNED) BETWEEN 1 AND 24;
