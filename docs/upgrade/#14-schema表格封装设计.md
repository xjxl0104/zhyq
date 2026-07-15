# #14 schema 驱动 useCrudPage 表格封装设计 · 待实施（本轮自主）

> 版本 v0.1（2026-07-15）· 批次③ P0-3 · 依赖 #4 token（已做）
> 纯前端。灰度迁移不强推 77 页。

## 1. 目标与非目标

**目标**：抽一个 `useCrudPage` composable，消除每个 list 页重复的 ~60-80 行样板（loading/list/total/query 状态 + load/reset + 分页绑定 + add/edit dialog 的 visible/title/emptyForm/submit add-vs-edit 分支）。一份 api 对象驱动，页面只留 columns 模板 + 表单字段 + 自定义动作。

**非目标**：不重写全部 77 页（灰度，先迁 1-2 页做样板+验证）；不做可视化 schema 配置；不引第三方表格库（Element Plus 2.7.6 够用）。列模板/表单字段仍由页面写（那是真正的业务差异），只抽流程样板。

## 2. 现状锚点（探查确认）

- 唯一 composable：`src/composables/useChart.js`（batch③ 建）。useCrudPage 放同目录。
- api 模块 `src/api/*.js` 形状：每模块导出 `{page, get, add, update, remove}` 类方法（axios 封装在 request，返回已 unwrap 的 Result.data）。
- 代表页 `ContractList.vue`(357行) 重复样板：`loading/list/total/query` + `load()/reset()` + 分页 + add/edit dialog `visible/title` + `emptyForm()`/`submitForm()`(add vs edit 分支)。自定义动作(submit/approve/terminate)页面自留。
- 无表格抽象库，无命名冲突（CrudTable/useCrudPage 均可用）。

## 3. 设计：useCrudPage composable

`src/composables/useCrudPage.js`：
```
useCrudPage(api, options) → {
  loading, list, total, query,
  pagination: { page, size },
  load(), reset(), handleSizeChange(), handleCurrentChange(),
  dialogVisible, dialogTitle, form, isEdit,
  openAdd(), openEdit(row), submit(), remove(row),
}
```
- `api`：`{page, get, add, update, remove}`（缺某方法则对应能力禁用，不报错）。
- `options`：`{ defaultQuery, emptyForm, beforeSubmit, pageField }`——defaultQuery 初始查询条件；emptyForm 返回新增空表单；beforeSubmit 提交前钩子（校验/加工）；pageField 兼容分页响应字段名（records/list/total）。
- `submit()`：isEdit ? api.update : api.add，成功后关 dialog + reload + ElMessage。
- `remove(row)`：ElMessageBox 确认 → api.remove(id) → reload。
- 所有异步包 try/finally 管 loading，错误交全局 axios 拦截器（不吞）。

## 4. 迁移样板（本轮迁 1-2 页验证）

迁 `TenantInfo`（或 building/Room）这类标准 CRUD 页做样板：用 useCrudPage 替换样板，列模板/表单保留，`npm run build` 通过 + 页面功能不变。**不动**有复杂自定义动作的页（合同审批等）——本轮不强迁，只证明 useCrudPage 能与页面自有逻辑共存。

## 5. 零触碰 & 决策

- 零触碰：新增 composable 是纯加法；迁移的页行为保持不变（同样的查询/增改删）。未迁的页一律不动。
- D1 迁移范围：本轮迁 1-2 标准页做样板，其余留后续灰度。
- D2 分页字段兼容：pageField option 兼容后端不同分页响应形状。
- D3 列/表单：仍页面写（YAGNI，不做 schema 化列定义——那会过度抽象）。

## 6. 工作量 & 验证

useCrudPage.js + 迁 1-2 页 + `npm run build` 通过 +（若全栈起）localhost 点验迁移页增删改查正常。纯前端，无迁移无后端。约 sonnet 半批。
