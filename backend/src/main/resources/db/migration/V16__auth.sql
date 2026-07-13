-- =====================================================================
-- V16 最简登录:sys_user 加密码列 + 演示账号密码
-- 密码为 SHA-256(明文+盐) 简化方案,盐固定 'zhyq' —— 演示级,正式上线换 BCrypt
-- admin / zhyq@2026  的 SHA-256("zhyq@2026zhyq")
-- =====================================================================
ALTER TABLE sys_user ADD COLUMN password VARCHAR(128) COMMENT '密码哈希' AFTER username;

-- 全部演示用户统一密码 zhyq@2026
UPDATE sys_user SET password = SHA2(CONCAT('zhyq@2026','zhyq'), 256);
