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
