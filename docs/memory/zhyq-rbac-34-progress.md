---
name: zhyq-rbac-34-progress
description: "zhyq ver4.0 3.4 RBAC 实施进度 + @PreAuthorize 测试模式 + V30 迁移三件套"
metadata:
  node_type: memory
  type: project
---

**ver4.0 最后一节 3.4 RBAC 实施中**(worktree `rbac-3.4`,基于 prod-hardening)。范围:**先卡敏感模块 = contract(1)+finance(9)+system(8)= 18 controller**。见 [[zhyq-ver4-prod-hardening]]。

## 关键事实
- RBAC 骨架 3.3 已铺好:数据模型 `sys_user_role→sys_role→sys_role_menu→sys_menu.perm`;登录加载角色(ROLE_前缀)+perm 写入 JWT;JwtAuthFilter 填充 authorities;`@EnableMethodSecurity` 已开。
- **缺口①**:85 controller 零 `@PreAuthorize`,任何登录用户能访问一切。
- **缺口②(致命)**:全库无 `sys_user_role`、`sys_role_menu` 种子,且 V11 建菜单没填 `sys_menu.perm`。→ 所有人(含 admin)JWT authorities 为空。**一挂注解,admin 自己也 403。**

## V30 迁移必须三件套(缺一即锁死 admin)
1. 回填 18 模块菜单的 `sys_menu.perm`(命名 `模块:资源:操作`,如 `finance:payment:pay`)。
2. `sys_role_menu`:把这些 perm 关联给 admin 角色(role.code='admin')。
3. `sys_user_role`:把 admin 用户关联到 admin 角色。
   全部用幂等写法(INSERT ... SELECT ... WHERE NOT EXISTS 或 ON DUPLICATE)。

## @PreAuthorize 单测模式(离线可跑,别用 @WebMvcTest)
- `@WebMvcTest` 会触发 @MapperScan 加载全部 MyBatis mapper → 需 SqlSessionFactory → 离线失败。**放弃**。
- 用纯 AOP 单测:`@ExtendWith(SpringExtension)` + `@ContextConfiguration(内部 @Configuration @EnableMethodSecurity + mock 依赖 + new Controller)` + `@WithMockUser(authorities=...)`;无权限断言 `AccessDeniedException`,有权限 `assertDoesNotThrow`。样例见 `PaymentControllerSecurityTest.java`(4 绿)。
- 编译测试:见 [[zhyq-build-env]] 的 JAVA_HOME + libexec mvn 路径,`-o test -Dtest=Xxx`。

## 命名规范
`模块:资源:操作` —— query/add/edit/delete/pay 等。查询类统一 `:query`。

## 进度
- [x] PaymentController 挂注解 + TDD 4 绿(模式验证)
- [x] 其余 17 个敏感 controller 挂注解(3 子代理并行 + 主脑补漏 SysDict/MsgCenter/SysPost.list)
- [x] V30 迁移三件套(perm 回填 + admin 角色授权 + admin 用户关联,全幂等)
- [x] GlobalExceptionHandler 补 AccessDeniedException → 403(否则方法级拒绝落兜底变 500)
- [x] 鉴权单测 Payment/SysUser 各 4;全测 12 绿;离线编译 BUILD SUCCESS
- [x] 已提交 e71e889(worktree 分支 rbac-3.4,基于 prod-hardening)
- [ ] 推分支 + 开 PR;合回 prod-hardening 后即达成 ver4.0

## 完成即 ver4.0
3.4 是 ver4.0 最后一节。合并后按 [[zhyq-ver4-prod-hardening]] 提交/打 ver4.0 版本节点(注意仓库用**分支**标版本,非 tag;且已存在的 ver3.3 分支内容对不上,勿混淆)。
