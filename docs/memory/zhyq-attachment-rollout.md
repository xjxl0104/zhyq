---
name: zhyq-attachment-rollout
description: "zhyq 各业务新增页接入统一附件上传 — 方案B先传后回填 + 核心模块范围"
metadata:
  node_type: memory
  type: project
---

**需求**:凡是核心业务的「新增/编辑」都支持附件上传。用户 2026-07-27 提出。

## 底座现状(已就绪,无需重建)
- 后端 `sys_file` 通用附件表:`biz_type + biz_id` 关联任意业务(V28 迁移)。`biz_id` 可空 = 先传后关联(原设计意图)。
- FileController `/file`:upload / upload-batch / list(bizType+bizId) / delete。
- 前端 `frontend/src/components/FileUpload.vue`(el-upload 封装,props: modelValue/bizType/bizId/accept,20MB 限制,带 token header)+ `src/api/file.js`。

## 已定方案(user 确认)
- **范围**:先接**核心业务模块**(约 10-15 页):合同/工单/巡检/投诉/发票/收据/租户资料/设备档案/会议室/资产/OA 审批。纯配置页(字典/部门/岗位/菜单)**不接**。
- **关联方式**:方案 B「先传后回填」。附件先传(bizId=null)拿 fileId 列表 → 业务保存拿到新 ID → 前端调**批量关联接口**回填 bizId。

## 待办
- [ ] 后端补 `POST /file/attach`(bizType, bizId, fileIds[])批量回填 bizId —— 方案B地基,TDD。注意幂等 + 仅回填属于该 bizType 的空 bizId 记录,防越权改他人附件。
- [ ] FileUpload.vue 支持「先传后回填」用法(暴露已传 fileId 列表给父组件)。
- [ ] 逐个核心页接入(FileUpload 放进新增/编辑弹窗;保存成功后调 attach)。
- [ ] 每模块 bizType 命名约定:work_order/contract/patrol/complaint/invoice/receipt/tenant/asset/... 与 V28 注释对齐。

## 分支
不混进 rbac-3.4(PR #1)。基于 prod-hardening 起新 worktree/分支。见 [[zhyq-ver4-prod-hardening]] [[zhyq-rbac-34-progress]]。

## ver4.0 汇总(user 2026-07-27 要求:做完推 GitHub 命名 ver4.0,自行验收)
ver4.0 = 三部分汇总,仓库用**分支**标版本:
- prod-hardening:3.2 配置外置 + 3.3 认证
- rbac-3.4:3.4 RBAC(PR #1)
- feat/attachments:附件功能(当前)
收尾:附件做完 → merge rbac-3.4 进来 → 建 ver4.0 分支推 origin。

## 样板已验证(commit f9e7ee1)
合同 ContractList.vue 端到端打通。**接入模式(每页重复)**:
1. `import { fileApi } from '@/api/file'` + `import FileUpload from '@/components/FileUpload.vue'`
2. 弹窗表单末尾加:`<FileUpload v-model="attachFiles" biz-type="XXX" :biz-id="form.id" />`
3. `const attachFiles = ref([])`
4. openDialog:`attachFiles.value=[]`;编辑时 `attachFiles.value = await fileApi.list('XXX', row.id)`
5. submit:add 返回新 id(request 拦截器已拆包 → add() 直接返回 Result.data=Long);
   `const pendingIds=(attachFiles.value||[]).filter(f=>f&&f.id&&!f.bizId).map(f=>f.id);`
   `if(id&&pendingIds.length) await fileApi.attach('XXX', id, pendingIds)`  // try/catch 不阻断
6. 后端 add 必须返回新 id(Result<Long>);若返回 void 需先补,否则新增无法 attach。

## 目标页面 + bizType(核心业务)
- property/WorkOrder.vue → work_order
- property/Patrol.vue → patrol
- finance/Invoice.vue → invoice
- finance/Receipt.vue → receipt
- tenant/TenantList.vue → tenant
- am/Asset.vue → asset
- oa/Approval.vue → oa_approval
- oa/Document.vue → oa_document
(纯配置/字典/部门/岗位不接)

## 接入结果(逐页)
- contract/ContractList ✓(样板,add 返 id)
- finance/Invoice ✓(add 返 Result<Long>)
- finance/Receipt ✗ 跳过:该页无新增/编辑弹窗,仅查询+只读表格+打印。收据应是收款时自动生成,非手工新增。后端 add 其实返 id,但页面无入口。如需手工建收据要先做增改弹窗(超出附件范围)。
- oa/Document ✓(add 返 Result<Long>)
- oa/Approval ✗ 跳过:审批中心列表,记录由合同/退款/调账等流程生成,页面仅审批意见框无新增弹窗。

