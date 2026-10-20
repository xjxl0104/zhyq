-- 用户指令(2026-10-20):把丁超设为 P4 且不设上级(邀请链路顶点)。
-- 幂等:已是 P4/已无上级时各语句为空操作;若系统里没有名为"丁超"的伙伴,本迁移不做任何更改。
-- 动他的上级会影响其子树 path,与 MktPromoterService.changeParent 同口径:整棵子树前缀替换。

-- 1) 岗位 → P4(取 id 最小的一条"丁超",防重名误伤)
UPDATE crm_promoter SET position_code = 'P4', position_since = NOW()
WHERE id = (SELECT id FROM (SELECT id FROM crm_promoter WHERE name = '丁超' AND deleted = 0 ORDER BY id LIMIT 1) t);

-- 2) 本人及其子树 path 改为以他为根:旧前缀 → /他的id/
--    已是根时为新旧前缀相同,等价空操作
UPDATE crm_promoter child
JOIN (SELECT id, path FROM crm_promoter WHERE name = '丁超' AND deleted = 0 ORDER BY id LIMIT 1) top
  ON child.deleted = 0 AND child.path LIKE CONCAT(top.path, '%')
SET child.path = CONCAT('/', top.id, '/', SUBSTRING(child.path, LENGTH(top.path) + 1));

-- 3) 上级置空(链路顶点,不设上级)
UPDATE crm_promoter SET parent_id = NULL
WHERE id = (SELECT id FROM (SELECT id FROM crm_promoter WHERE name = '丁超' AND deleted = 0 ORDER BY id LIMIT 1) t)
  AND parent_id IS NOT NULL;
