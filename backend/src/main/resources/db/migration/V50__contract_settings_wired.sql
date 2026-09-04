-- V50: 合同设置补齐真正会被代码读的配置项(2026-09-04 负责人:「合同设置现在没什么作用」)
--
-- 现状:biz_setting 整张表是死的 —— 只有设置页在增删改,没有任何业务代码读它,
--   每个值都在别处又硬编码了一份,而且对不上:
--     contract.expire_remind_days = 90,但 DashboardController 写死 INTERVAL 30 DAY
--     finance.late_fee_rate = 0.0005,但 LateFeeService 写死同名常量
--   改配置不生效,设置页自然就是个摆设。
--
-- 本次做两件事:①把已有的 code_prefix / expire_remind_days 接到代码上(见 Java 侧)
--   ②补三个同样接了线的合同配置项,而不是再加没人读的旋钮。
--
-- 幂等:已存在同 module+skey 则跳过,可重复执行。纯新增配置行,不改存量数据。

INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'contract', 'deposit_months', '2', '保证金月数:新增合同时按「月租金 × 月数」自动算保证金', 1, 'system', NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM biz_setting s WHERE s.module = 'contract' AND s.skey = 'deposit_months' AND s.deleted = 0
);

INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'contract', 'default_term_years', '6', '默认租期(年):新增合同时按起租日自动推算到期日', 1, 'system', NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM biz_setting s WHERE s.module = 'contract' AND s.skey = 'default_term_years' AND s.deleted = 0
);

INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'contract', 'auto_expire_enabled', '1', '到期自动置为已到期:关掉后每日任务不再把执行中合同翻成已到期', 1, 'system', NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM biz_setting s WHERE s.module = 'contract' AND s.skey = 'auto_expire_enabled' AND s.deleted = 0
);

-- 把两条已有配置的说明写清楚「作用于哪里」,设置页照着显示,改之前能知道会影响什么
UPDATE biz_setting SET remark = '合同编号前缀:新增合同时按「前缀+年份+序号」自动生成编号'
WHERE module = 'contract' AND skey = 'code_prefix' AND deleted = 0;

UPDATE biz_setting SET remark = '到期提醒提前天数:工作台「合同即将到期」与合同列表的到期标记都按它算'
WHERE module = 'contract' AND skey = 'expire_remind_days' AND deleted = 0;
