# ver4.0 · 3.4 RBAC 敏感模块权限标识清单(单一真相源)

> 命名:`模块:资源:操作`。查询统一 `:query`。@PreAuthorize("hasAuthority('X')")。
> 此表同时驱动:controller 注解 + V30 迁移(sys_menu.perm 回填)。改这里 = 改两处。

## contract — 合同(ContractController `/contract`)
| 方法 | perm |
|---|---|
| page, get/{id} | contract:query |
| add | contract:add |
| update | contract:edit |
| delete | contract:delete |
| submit | contract:submit |
| approve | contract:approve |
| terminate | contract:terminate |
| archive | contract:archive |

## finance — 财务
### BillController `/finance/bill`
| page, stats, overdue, get/{id} | finance:bill:query |
| calcLateFee | finance:bill:calcLateFee |
| add | finance:bill:add |
| update | finance:bill:edit |
| delete | finance:bill:delete |

### PaymentController `/finance/payment` (已完成)
| pay | finance:payment:pay |
| list | finance:payment:query |

### InvoiceController `/finance/invoice`
| page, get/{id} | finance:invoice:query |
| add | finance:invoice:add |
| update | finance:invoice:edit |
| delete | finance:invoice:delete |

### ReceiptController `/finance/receipt`
| page, get/{id}, logs | finance:receipt:query |
| add | finance:receipt:add |
| update | finance:receipt:edit |
| delete | finance:receipt:delete |
| print | finance:receipt:print |

### PayNoticeController `/finance/notice`
| page | finance:notice:query |
| delete | finance:notice:delete |
| generate | finance:notice:generate |
| send | finance:notice:send |

### FlowController `/finance/flow`
| page | finance:flow:query |

### SettingController `/finance/setting`
| list | finance:setting:query |
| batch | finance:setting:edit |
| add | finance:setting:add |
| delete | finance:setting:delete |

### ReportController `/finance/report`
| summary | finance:report:query |

### CheckoutReportController `/finance/checkout-report`
| page, stats | finance:report:query |

## system — 系统管理
### SysUserController `/system/user`
| page, get/{id}, list | system:user:query |
| add | system:user:add |
| update | system:user:edit |
| delete | system:user:delete |

### SysRoleController `/system/role`
| page, list | system:role:query |
| add | system:role:add |
| update | system:role:edit |
| delete | system:role:delete |

### SysMenuController `/system/menu`
| list, get/{id} | system:menu:query |
| add | system:menu:add |
| update | system:menu:edit |
| delete | system:menu:delete |

### SysResourceController `/system/resource`
| page, get/{id} | system:resource:query |
| add | system:resource:add |
| update, toggle | system:resource:edit |
| delete | system:resource:delete |

### SysDeptController `/system/dept`
| list | system:dept:query |
| add | system:dept:add |
| update | system:dept:edit |
| delete | system:dept:delete |

### SysPostController `/system/post`
| page | system:post:query |
| add | system:post:add |
| update | system:post:edit |
| delete | system:post:delete |

### SysDictController `/system/dict`
| typePage, dataByType | system:dict:query |
| addType, addData | system:dict:add |
| updateType, updateData | system:dict:edit |
| delType, delData | system:dict:delete |

### MsgCenterController `/system/message`
| templatePage, recordPage | system:message:query |
| templateAdd | system:message:add |
| templateUpdate | system:message:edit |
| templateDelete, recordDelete | system:message:delete |
| send | system:message:send |

## pur — 采购管理(ver6.6 补,随 PR #4 并入)
> 迁移:V41__pur_workflow_perms_seed.sql(非 V30)。

### PurPlanController `/pur/plan`
| 方法 | perm |
|---|---|
| page, list, get/{id} | pur:plan:query |
| add | pur:plan:add |
| update | pur:plan:edit |
| remove | pur:plan:delete |

### PurRequestController `/pur/request`
| 方法 | perm |
|---|---|
| page, get/{id} | pur:request:query |
| add | pur:request:add |
| update | pur:request:edit |
| remove | pur:request:delete |
| submit | pur:request:submit |
| complete | pur:request:complete |
| cancel | pur:request:cancel |

## workflow — 审批链(ver6.6 补)
> 流程定义/节点是合同、采购等多 bizType 共用的「谁能审批」配置,
> 能改即可把审批人指向自己实现自审,故按管理级单独设点。
> 运行时接口(start / task approve / reject / my / instance)本次未收口,
> 属存量问题:收口需先定审批人角色口径。

### WorkflowController `/workflow`
| 方法 | perm |
|---|---|
| definitionPage, nodes | workflow:definition:manage |
| addDefinition, updateDefinition, removeDefinition, saveNodes | workflow:definition:manage |
| start, approve, reject, myTasks, myPendingTasks, instancePage, instanceTasks | (未收口,见上) |

> myPendingTasks(`/workflow/task/my-pending`)是预算管理板块加的,属同一组运行时接口:
> 它只是 myTasks 的展示增强(补上所属实例的 bizType/bizId),不新增可读数据 ——
> 同一登录用户本就能从 instancePage 拿到这些字段。运行时接口整体收口时应一并处理。

## budget — 预算管理(随预算管理板块并入)
> 迁移:V42__budget_management.sql。ver6.6 已占用 V41,故排 V42。
> 口径对齐 pur:提交申请 / 归档 / 取消各自独立设点,不被 edit 覆盖 ——
> 能改草稿不等于能推进审批状态。

### BudgetController `/budget`
| 方法 | perm |
|---|---|
| page, get/{id} | budget:query |
| add | budget:add |
| update | budget:edit |
| remove | budget:delete |
| submit | budget:submit |
| archive | budget:archive |
| cancel | budget:cancel |
