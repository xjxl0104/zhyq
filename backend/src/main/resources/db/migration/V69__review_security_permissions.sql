-- Review security fixes: preserve explicit legacy page grants while making APIs enforceable.
-- No new role is granted access merely because it can log in.
INSERT INTO sys_menu (parent_id,name,type,path,sort,visible,status,tenant_id,create_by,create_time,version,deleted)
SELECT COALESCE((SELECT MIN(id) FROM sys_menu p WHERE p.name='招商' AND p.type=1 AND p.deleted=0),0),
       '意向客户',2,'/crm/customer',3,1,1,1,'system',NOW(),1,0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.path='/crm/customer' AND m.deleted=0);

CREATE TEMPORARY TABLE review_security_permissions (
    route VARCHAR(128) NOT NULL, perm VARCHAR(128) NOT NULL, label VARCHAR(128) NOT NULL
);
INSERT INTO review_security_permissions(route,perm,label) VALUES
('/crm/customer', 'crm:customer:query', '意向客户-查询'),
('/crm/customer', 'crm:customer:add', '意向客户-新增'),
('/crm/customer', 'crm:customer:edit', '意向客户-编辑'),
('/crm/customer', 'crm:customer:delete', '意向客户-删除'),
('/tenant/list', 'tenant:query', '租客附件-查询'),
('/tenant/list', 'tenant:edit', '租客附件-编辑'),
('/iot/device', 'iot:device:query', '硬件附件-查询'),
('/iot/device', 'iot:device:edit', '硬件附件-编辑'),
('/iot/vendor', 'iot:vendor:query', '厂商附件-查询'),
('/iot/vendor', 'iot:vendor:edit', '厂商附件-编辑'),
('/service/decoration', 'service:decoration:query', '装修附件-查询'),
('/service/decoration', 'service:decoration:edit', '装修附件-编辑'),
('/service/pass', 'service:pass:query', '物品放行附件-查询'),
('/service/pass', 'service:pass:edit', '物品放行附件-编辑'),
('/service/declare', 'service:declare:query', '申报附件-查询'),
('/service/declare', 'service:declare:edit', '申报附件-编辑'),
('/service/policy', 'service:policy:query', '政策附件-查询'),
('/service/policy', 'service:policy:edit', '政策附件-编辑'),
('/property/asset', 'property:asset:query', '资产附件-查询'),
('/property/asset', 'property:asset:edit', '资产附件-编辑'),
('/property/inspection', 'property:inspection:query', '巡检附件-查询'),
('/property/inspection', 'property:inspection:edit', '巡检附件-编辑'),
('/property/feedback', 'property:feedback:query', '意见反馈附件-查询'),
('/property/feedback', 'property:feedback:edit', '意见反馈附件-编辑'),
('/property/complaint', 'property:feedback:query', '投诉附件-查询'),
('/property/complaint', 'property:feedback:edit', '投诉附件-编辑'),
('/property/meeting', 'property:meeting:query', '会议室附件-查询'),
('/property/meeting', 'property:meeting:edit', '会议室附件-编辑'),
('/property/workorder', 'property:workorder:query', '工单附件-查询'),
('/property/workorder', 'property:workorder:edit', '工单附件-编辑'),
('/property/patrol', 'property:patrol:query', '巡更附件-查询'),
('/property/patrol', 'property:patrol:edit', '巡更附件-编辑'),
('/property/activity', 'property:activity:query', '活动附件-查询'),
('/property/activity', 'property:activity:edit', '活动附件-编辑'),
('/property/check-clean', 'property:check:query', '保洁附件-查询'),
('/property/check-clean', 'property:check:edit', '保洁附件-编辑'),
('/property/check-green', 'property:check:query', '绿化附件-查询'),
('/property/check-green', 'property:check:edit', '绿化附件-编辑'),
('/property/check-quality', 'property:check:query', '品质附件-查询'),
('/property/check-quality', 'property:check:edit', '品质附件-编辑'),
('/oa/notice', 'oa:notice:query', '公告附件-查询'),
('/oa/notice', 'oa:notice:edit', '公告附件-编辑'),
('/oa/article', 'oa:article:query', '文章附件-查询'),
('/oa/article', 'oa:article:edit', '文章附件-编辑'),
('/oa/recruit', 'oa:recruit:query', '招聘附件-查询'),
('/oa/recruit', 'oa:recruit:edit', '招聘附件-编辑'),
('/oa/task', 'oa:task:query', '任务附件-查询'),
('/oa/task', 'oa:task:edit', '任务附件-编辑'),
('/oa/document', 'oa:document:query', '公文附件-查询'),
('/oa/document', 'oa:document:edit', '公文附件-编辑'),
('/energy/meter', 'energy:reading:query', '抄表附件-查询'),
('/energy/meter', 'energy:reading:edit', '抄表附件-编辑');

INSERT INTO sys_menu (parent_id,name,type,perm,sort,visible,status,tenant_id,create_by,create_time,version,deleted)
SELECT COALESCE(MIN(page.id),0), MIN(p.label),3,p.perm,0,0,1,1,'system',NOW(),1,0
FROM review_security_permissions p
LEFT JOIN sys_menu page ON page.path=p.route AND page.deleted=0 AND page.type=2
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm=p.perm AND m.deleted=0)
GROUP BY p.perm;

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.deleted=0
JOIN review_security_permissions p ON p.perm=m.perm
WHERE r.deleted=0 AND (r.code='admin' OR EXISTS (
    SELECT 1 FROM sys_role_menu old_grant JOIN sys_menu page ON page.id=old_grant.menu_id
    WHERE old_grant.role_id=r.id AND page.path=p.route AND page.deleted=0 AND page.status=1
)) AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=r.id AND existing.menu_id=m.id);

INSERT INTO sys_user_menu(user_id,menu_id)
SELECT DISTINCT old_grant.user_id,m.id FROM sys_user_menu old_grant
JOIN sys_menu page ON page.id=old_grant.menu_id AND page.deleted=0 AND page.status=1
JOIN review_security_permissions p ON p.route=page.path
JOIN sys_menu m ON m.perm=p.perm AND m.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_menu existing WHERE existing.user_id=old_grant.user_id AND existing.menu_id=m.id);

-- Lead conversion already requires lead edit; preserve that authorized workflow after adding customer controls.
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT old_grant.role_id,m.id FROM sys_role_menu old_grant
JOIN sys_menu lead_menu ON lead_menu.id=old_grant.menu_id AND lead_menu.perm='crm:lead:edit' AND lead_menu.deleted=0
JOIN sys_menu m ON m.perm IN ('crm:customer:add','crm:customer:query') AND m.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=old_grant.role_id AND existing.menu_id=m.id);
INSERT INTO sys_user_menu(user_id,menu_id)
SELECT DISTINCT old_grant.user_id,m.id FROM sys_user_menu old_grant
JOIN sys_menu lead_menu ON lead_menu.id=old_grant.menu_id AND lead_menu.perm='crm:lead:edit' AND lead_menu.deleted=0
JOIN sys_menu m ON m.perm IN ('crm:customer:add','crm:customer:query') AND m.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_menu existing WHERE existing.user_id=old_grant.user_id AND existing.menu_id=m.id);
DROP TEMPORARY TABLE review_security_permissions;
