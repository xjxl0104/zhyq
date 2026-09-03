-- V48: 滞纳金人工调整(2026-09-03 负责人需求:现场有时不收滞纳金,管理员要能手动改为 0)
--
-- 关键约束:滞纳金原本由 LateFeeService 每日全量重算(自愈任务 + 手动端点),
--   人工改完如果不打标记,下一轮重算就会把它覆盖回去。故加 late_fee_manual 标记:
--   置 1 后重算整条跳过(查询与条件更新两处都带守卫),置回 0 恢复自动计算。
--
-- late_fee_remark 记调整原因(如「与租户协商减免」),留痕给后续对账/审计看;
-- 谁改的、什么时候改的由 update_by/update_time(MyMetaObjectHandler 自动填充)
-- 与操作日志(@OperationLog)记录,此处不重复存。
--
-- 新增权限点 finance:bill:lateFee:adjust,并授予 admin 角色(沿用 V30 三件套的幂等写法)。
-- 纯加列 + 加权限,不改任何存量数据,无破坏性。

ALTER TABLE fin_bill
    ADD COLUMN late_fee_manual TINYINT      NOT NULL DEFAULT 0 COMMENT '滞纳金是否人工调整:1=锁定不参与自动重算' AFTER late_fee,
    ADD COLUMN late_fee_remark VARCHAR(255) NULL               COMMENT '滞纳金人工调整原因' AFTER late_fee_manual;

-- ---------- 权限点(幂等:perm 已存在则跳过) ----------
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, '账单-调整滞纳金', 3, 'finance:bill:lateFee:adjust', 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = 'finance:bill:lateFee:adjust' AND m.deleted = 0
);

-- ---------- 授予 admin 角色(幂等) ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perm = 'finance:bill:lateFee:adjust' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
