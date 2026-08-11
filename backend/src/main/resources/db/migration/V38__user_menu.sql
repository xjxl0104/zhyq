-- =====================================================================
-- V38 用户直接菜单分配表
-- 支持绕过角色，直接给用户分配菜单权限（粒度到二级菜单）
-- =====================================================================
CREATE TABLE sys_user_menu (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id   BIGINT NOT NULL,
    menu_id   BIGINT NOT NULL,
    UNIQUE KEY uk_user_menu (user_id, menu_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户直接菜单关联';
