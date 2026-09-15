# Warehouse Twin Implementation Plan

**Goal:** Deliver a local interactive warehouse model as the Vue app's homepage.

**Architecture:** A configurable Three.js model, Vue scene adapter and workspace interface. A development-only preview route supplies explicitly labeled sample records; authenticated navigation uses existing business routes.

**Tech Stack:** Vue 3, Vue Router 4, Three.js 0.180, Vite 5, Vitest 2.

**Spec:** `../specs/2026-09-15-warehouse-twin-design.md`

## Tasks

- [x] Implement model configuration and route mapping in `frontend/src/views/twin/twinData.js`; add model and navigation tests covering floor boundaries and module destinations.
- [x] Build photo-informed exterior, floor separation and warehouse interior in `warehouseModel.js`. Keep individual floors named and selectable, share geometries and materials where possible.
- [x] Implement scene lifecycle in `WarehouseScene.vue`: OrbitControls, resize observation, raycast selection, projected accessible point buttons, animated transitions and cleanup. Confirm real WebGL rendering in browser.
- [x] Build `TwinHome.vue`, `TwinIcon.vue` and `twin.css`: sidebar, view switch, floor picker, point detail, business filters, reference gallery and local module views. Add interaction tests covering selection, module navigation and demo labels.
- [x] Register development preview routes and protected homepage; keep original dashboard at `/overview`. Verify production preview exclusion without authentication bypass.
- [x] Run `pnpm test`, `pnpm build`, and browser walkthrough for exterior, exploded view, interior, business points, navigation and responsive layout. Document exact startup URL and remaining data/model limitations.

Local repository is a fresh checkout dedicated to this request. No push or deployment is part of this plan. Continue in this task under the user's instruction to implement and show the local result.
