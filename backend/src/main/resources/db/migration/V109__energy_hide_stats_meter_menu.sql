-- V109 下线「能耗统计」「智能表计」两个菜单入口
-- 能耗页只保留 电费 / 水费 / 垃圾处理费 / 缴费汇总。只把菜单隐藏停用,不删行、不动授权关系;
-- 需要恢复时把这两行的 visible、status 改回 1 即可。
UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) IN ('energy/stats', 'energy/meter');
