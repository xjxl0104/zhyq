# Mobile responsive implementation plan

> Execute with superpowers:subagent-driven-development; keep changes in the current feature checkout and review the integrated result.

**Goal:** Make the existing management application usable on phones while preserving desktop and all business actions.
**Architecture:** Shared viewport state and mobile navigation, shared responsive CSS, mobile cards for common lists, targeted layouts for complex surfaces. Existing APIs and handlers remain authoritative.
**Tech Stack:** Vue 3, Element Plus, Vite, Vitest, SCSS.
**Spec:** docs/mobile-adaptation-audit-2026-09-17.md (approved by user: directly complete the work).

## Global constraints
- Phone breakpoint: `(max-width: 767px), (max-width: 1023px) and (max-height: 500px)`; test 360/390/430, landscape 844x390, tablet 768, desktop 1440.
- Preserve business logic, status conditions, permissions, data fields, desktop functionality and current visual identity.
- No backend, auth, deployment, API contract or dependency changes.
- Shared files belong to root. Other agents only edit assigned files. Do not commit while another worker is editing.
- Phone tables may scroll locally with visible guidance; page contents must remain reachable without clipped controls.
- Verify reactive interactions with behavioral tests; verify CSS with browser layout checks rather than source-string tests.

### Task 1: shared responsive shell (root)
Files: composables/useResponsive.js, layout/Layout.vue, views/Login.vue, styles/responsive.scss, main.js, index.html, related unit tests.
Interface: `useResponsive()` returns `{ isMobile }`, a readonly Vue ref using matchMedia with lifecycle cleanup.
- [x] Test mobile menu opening/closing, route changes, desktop collapse preference, breakpoint cleanup and listener lifecycle.
- [x] Implement mobile header with title, menu and project context, Element Plus modal drawer with the existing menu; keep desktop sidebar.
- [x] Add screen-height/safe-area, single-column forms, modal bounds, filter/toolbars/pagination, touch controls, table-scroll hints, responsive generic grid rules.
- [x] Test and inspect phone and desktop.

### Task 2: mobile business lists (delegated)
Files owned: new components/MobileRecordList.vue and its tests; views/property/WorkOrder.vue, views/oa/Approval.vue, views/finance/Bill.vue, views/contract/ContractList.vue, views/tenant/TenantList.vue, views/crm/Lead.vue; focused tests as needed.
Consumes: `import { useResponsive } from '@/composables/useResponsive'`; `const { isMobile } = useResponsive()`.
Produces: accessible card views sharing existing data/query/actions, with desktop tables under `v-else`. New component may own card layout only.
- [x] Read actual page templates and handlers; preserve every status-gated action, selection/batch action, loading state, pagination and detail navigation.
- [x] Add reusable list component exposing default/title/actions slots with `{ row, index }`, loading/empty state, and stable row keys. Write a behavioral rendering/interaction test first.
- [x] Render compact mobile records with headline, status, key data and all existing actions. Remaining details must remain accessible (expand details or existing detail view). Do not truncate the only copy of a core identifier without access to full value.
- [x] Keep existing table templates and handlers. CSS changes scoped to owned files; no global styles or shell edits.
- [x] Run component and relevant page tests; report changed files, validations, risks. No commit or subagents.

### Task 3: complex layouts (root after base; delegation if helpful)
Files: building/RoomControl.vue; components/RecordDetail.vue; dashboard/Index.vue; data/{DataCenter,SectionView}.vue; finance/Cashier.vue; oa/Schedule.vue; other exceptional fixed layouts found in static inventory; suggestion/FeedbackFab.vue.
- [x] Replace fixed room tree with collapsible mobile selection. Preserve selection/filter behavior.
- [x] Reflow desktop grids/stats/detail steps; adapt calendars, cashier and import sequences without removing actions.
- [x] Ensure charts resize and mobile ornamental backgrounds stop rendering; use safe-area for feedback control.
- [x] Apply local scrolling only to inherently wide tables/calendars and show a hint.

### Task 4: integrated verification and review
- [x] Run complete Vitest suite and production build; distinguish existing failures.
- [x] Browser-check all target widths with the local fixture server, then confirm the production build at phone and desktop sizes; no production writes. Check representative list, dialog, drawer, filters, menu/route/project switching and desktop baseline.
- [x] Run one batched visual inspection, fix concrete issues in one batch, then confirm. Check broad route layout metrics with deterministic fixtures.
- [x] Independent whole-diff review. Fix substantive findings and rerun impacted checks.
- [x] Document results and limitations, leave a reviewable feature branch and mark goal complete only after required work finishes.

Completion evidence: docs/mobile-adaptation-validation-2026-09-17.md. Implementation and both independent reviews passed; no deployment or remote merge performed.
