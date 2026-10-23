-- 用户指令(2026-10-23)：后台账号只按手机号关联 P4 伙伴，给“丁超”的后台账号补上手机号 18824642574。
-- 严格保护，任何一条不满足都不做更改（不会让迁移失败）：
--   1) 后台用户里昵称为“丁超”或“丁超(…)/丁超（…）”的未删除账号恰好只有一个；
--   2) 该账号当前手机号为空或不同；
--   3) 没有别的后台用户已经使用这个手机号（避免两个账号都绑定到同一位 P4）。
UPDATE sys_user SET phone = '18824642574'
WHERE deleted = 0
  AND id = (SELECT id FROM (
        SELECT id FROM sys_user
        WHERE deleted = 0 AND (nickname = '丁超' OR nickname LIKE '丁超(%' OR nickname LIKE '丁超（%')
        ORDER BY id LIMIT 1) t1)
  AND (SELECT COUNT(*) FROM (
        SELECT id FROM sys_user
        WHERE deleted = 0 AND (nickname = '丁超' OR nickname LIKE '丁超(%' OR nickname LIKE '丁超（%')) t2) = 1
  AND (phone IS NULL OR phone = '' OR phone <> '18824642574')
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_user WHERE deleted = 0 AND phone = '18824642574') t3);

-- 伙伴“丁超”（取 id 最小的一条，防重名）若还没有手机号，补成同一个号码，使后台账号能关联到他；
-- 已有手机号的不改；该号码已被别的伙伴占用时不改（crm_promoter.phone 唯一）。
UPDATE crm_promoter SET phone = '18824642574'
WHERE id = (SELECT id FROM (SELECT id FROM crm_promoter WHERE name = '丁超' AND deleted = 0 ORDER BY id LIMIT 1) t1)
  AND (phone IS NULL OR phone = '' OR phone LIKE 'p:%')
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM crm_promoter WHERE phone = '18824642574') t3);
