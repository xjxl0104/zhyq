# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

园区运营与财务同事。2026-10-14 用户明确要求：财务需要独立的大可视化面板，让新同事一眼看懂；首屏采用“业务全景＋待办”，理解流程并知道下一步做什么。

## Product Purpose

DIPARK 智慧系统管理园区空间、租赁、合同、费用收缴及运营业务。财务总览连接既有业务页面，展示应收来源、收缴进度与待处理事项。

## Capabilities and Constraints

仓库实际能力：应收登记计费、账单、收款、零元核销、红冲、收据、发票登记与报表。账期累计收缴与按到账日期统计的现金净额需要清晰区分。云仓服务费、云仓结算及佣金有独立台账。

本次新增只读总览，沿用财务报表权限和现有页面，不改变收款、计费或合同状态规则。前端既有 Vue 3、Element Plus、Pinia、Vue Router 与统一样式。

## Evidence on Hand

实际流程与接口见 `src/views/finance/`、`src/api/finance.js`、后端 `finance/` 和 `receivable/`。正式面板只使用接口数据；开发预览示例必须明确标识。

## Product Principles

- 先解释业务，再引导操作。
- 金额必须带单位、时间范围和统计口径。
- 错误或尚未加载不能显示成零收入或零欠款。
- 操作入口遵循现有权限。
