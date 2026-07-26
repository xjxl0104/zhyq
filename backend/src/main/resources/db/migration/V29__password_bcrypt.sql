-- =====================================================================
-- V29 密码算法升级:SHA-256 → BCrypt
-- 背景:V16 用 SHA2(明文+盐) 存储密码,登录逻辑已改为 Spring Security
--       BCryptPasswordEncoder.matches() 校验,两者不兼容 → 全部账号无法登录。
-- 处理:将仍为非 BCrypt 格式的演示账号密码重置为 BCrypt('zhyq@2026')。
--       BCrypt 串以 $2 开头;WHERE 过滤保证幂等且不覆盖已正确的哈希。
-- 明文口令仍为 zhyq@2026(演示级);正式上线请逐用户改密。
-- =====================================================================
UPDATE sys_user
SET password = '$2a$10$zVmKfp48A/ClhP1LSnYEteKCHRDVAlXBwis9FEhGEPWVaR2qahPgi'
WHERE password IS NULL OR password NOT LIKE '$2%';
