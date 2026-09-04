-- V49: 收款撤销(红冲留痕) + 零元账单核销(2026-09-04 负责人报的两个现场问题)
--
-- 问题一「免租期账单收不了款」:免租期/抵扣期的账单应收就是 0.00,
--   PaymentService.收款 有「收款金额必须大于0」硬校验,前端输入框 min 又是 0.01,
--   于是这类账单既收不了 0 也收不了 0.01(0.01 会撞超额校验),永远卡在「待收付」。
--   本次不给它开「0 元收款」的口子(0 元支付单/0 元流水/0 元收据全是噪音,还会污染报表),
--   改为独立的「零元核销」通道:只把账单置为已结清并留痕,不产生任何资金记录。
--
-- 问题二「收银台点错了收款没法退」:收款是一笔事务(支付单+账单实收+收支流水+收据),
--   原来只有进没有出。本次按会计红冲口径做:原单标记已撤销、生成一张负额红冲支付单、
--   写一笔负额收支流水、作废对应收据、账单实收回退并重算状态。全程留痕可审计,
--   不做物理删除 —— 删掉就再也查不到「谁在什么时候撤了哪一笔」。
--
-- void_status 用 TINYINT 不用 VARCHAR:枚举存字符串一旦长度算错会被 MySQL 静默截断。
-- 纯加列 + 加权限,不改任何存量数据,无破坏性;存量行由 DEFAULT 0 落到「正常」态。

ALTER TABLE fin_payment
    ADD COLUMN void_status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0=正常 1=已撤销的原单 2=红冲单' AFTER pay_method,
    ADD COLUMN void_reason         VARCHAR(255) NULL               COMMENT '撤销原因' AFTER void_status,
    ADD COLUMN void_time           DATETIME     NULL               COMMENT '撤销时间' AFTER void_reason,
    ADD COLUMN void_by             VARCHAR(32)  NULL               COMMENT '撤销操作人' AFTER void_time,
    ADD COLUMN original_payment_id BIGINT       NULL               COMMENT '红冲单指向的原支付单 id' AFTER void_by;

CREATE INDEX idx_payment_original ON fin_payment (original_payment_id);

ALTER TABLE fin_receipt
    ADD COLUMN void_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=正常 1=已作废(对应收款被撤销)' AFTER payee;

-- ---------- 权限点(幂等:perm 已存在则跳过,沿用 V48 三件套写法) ----------
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, '收款-撤销(红冲)', 3, 'finance:payment:void', 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = 'finance:payment:void' AND m.deleted = 0
);

INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, '账单-零元核销', 3, 'finance:bill:writeOff', 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = 'finance:bill:writeOff' AND m.deleted = 0
);

-- ---------- 授予 admin 角色(幂等) ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perm IN ('finance:payment:void', 'finance:bill:writeOff') AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
