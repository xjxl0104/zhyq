-- V143: 下线办公管理-日程模块(/oa/schedule)
UPDATE sys_menu
SET status = 0
WHERE deleted = 0
  AND (
      TRIM(BOTH '/' FROM path) = 'oa/schedule'
      OR perm LIKE 'oa:schedule:%'
      OR perm = 'oa:schedule'
  );
