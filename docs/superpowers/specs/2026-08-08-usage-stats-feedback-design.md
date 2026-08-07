# 平台使用度统计与建议提交系统 — 设计文档 v1.0

> 对应功能点拆解 v1.0
> 本文档定义：数据模型、指标口径、评分公式、状态机、技术方案

---

## 1. 系统定位与约束

- 辅助推广工具，不是监控系统
- 只采集 API 访问日志，不做前端埋点、不监测鼠标键盘
- 日志保留 6 个月，到期自动清理
- 统计结果不作为绩效考核依据（BI 页面固定声明）
- 对业务代码零侵入：日志采集在 Filter 层，聚合走离线任务

---

## 2. 技术方案概览

### 2.1 日志采集

| 方案 | 说明 |
|------|------|
| 实现方式 | `OncePerRequestFilter`，注册在 SecurityConfig 中 `JwtAuthFilter` 之后 |
| 写入方式 | 异步写入（内存队列 + 批量 flush），队列满或写入失败时静默丢弃并计数 |
| 路由模板化 | 从 Spring `RequestMappingHandlerMapping` 获取 pattern（如 `/order/{id}/submit`） |
| 排除清单 | 数据库配置表 `access_log_exclude`，启动时加载 + 定时刷新（5 分钟） |

### 2.2 聚合任务

| 方案 | 说明 |
|------|------|
| 调度方式 | `@Scheduled` cron，与现有 `SlaEscalationJob` 模式一致 |
| 日粒度 | 每日 02:00 执行，从 access_log 聚合写入 user_daily_metrics |
| 周/月粒度 | 每周一 03:00 / 每月 1 日 03:30，从 user_daily_metrics 聚合 |
| 评分计算 | 跟在周/月聚合之后执行 |
| 幂等 | REPLACE INTO / ON DUPLICATE KEY UPDATE，按 (user_id, stat_date) 去重 |
| 补跑 | 提供手动触发接口，传入日期范围 |

### 2.3 BI 视图

| 方案 | 说明 |
|------|------|
| 技术选型 | 自研前端页面（ECharts + 现有 Dashboard 模式），不引入第三方 BI 工具 |
| 查询层 | 新建 `BiController`，使用 JdbcTemplate 做只读聚合查询 |
| 权限 | 管理层视图：`bi:admin:view`；产品团队视图：`bi:product:view` |

### 2.4 图片存储

| 方案 | 说明 |
|------|------|
| 存储 | 复用现有 `sys_file` 体系（本地磁盘 + 认证下载） |
| biz_type | `feedback_image` |
| 访问控制 | 通过现有文件下载接口，需登录态 |
| 限制 | 单张 ≤5MB，单条 ≤5 张，校验文件头魔数 |

---

## 3. 功能一：使用度统计

### 3.1 access_log 表结构

```sql
-- 不继承 BaseEntity，独立的追加日志表
CREATE TABLE access_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '操作用户ID',
    dept_id     BIGINT       NOT NULL COMMENT '用户所属部门ID',
    route       VARCHAR(255) NOT NULL COMMENT '模板化路由 /order/{id}/submit',
    method      VARCHAR(10)  NOT NULL COMMENT 'GET/POST/PUT/DELETE',
    module      VARCHAR(64)  DEFAULT NULL COMMENT '所属模块（来自路由映射表）',
    is_core     TINYINT(1)   DEFAULT 0 COMMENT '是否核心操作',
    status_code INT          NOT NULL COMMENT 'HTTP 状态码',
    duration_ms INT          NOT NULL COMMENT '请求耗时(ms)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, created_at),
    INDEX idx_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API 访问日志（保留6个月）';
```

不分区理由：预估日活 50~200 人 × 人均 100~300 请求 = 1~6 万条/天，6 个月约 500~1000 万条，单表 + 索引可承受。若后续量级上升再加按月分区。

### 3.2 排除清单配置表

```sql
CREATE TABLE access_log_exclude (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    pattern VARCHAR(255) NOT NULL COMMENT '排除路由模式，支持 Ant 风格通配：/actuator/**, /auth/login',
    reason  VARCHAR(100) DEFAULT NULL COMMENT '排除原因',
    enabled TINYINT(1)   DEFAULT 1,
    UNIQUE KEY uk_pattern (pattern)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

默认排除清单：
- `/auth/**` — 登录登出
- `/actuator/**` — 健康检查
- `/doc/**`, `/swagger-resources/**` — API 文档
- `/file/download/**` — 文件下载（非业务操作）
- 轮询类接口（由产品标注后补充）

轮询处理策略：标记为排除，不计入 active_minutes。

### 3.3 指标口径定义

#### 日粒度指标（user_daily_metrics）

| 指标 | 口径 | 说明 |
|------|------|------|
| active_minutes | 将用户当日请求按 created_at 排序，相邻请求间隔 ≤10 分钟算连续活跃，每段活跃时长 = 末次 - 首次（单次请求该段计 0） | 不用于评分，仅作参考 |
| request_count | 当日非排除请求总数 | 辅助指标 |
| module_set | 当日访问的去重模块列表（JSON 数组） | 用于计算覆盖广度 |
| core_action_count | 当日 is_core=1 的请求数 | 用于评分 |
| flow_started | 当日在业务表中发起的流程数（按 P1 清单定义的发起状态） | 来自业务表 |
| flow_completed | 当日在业务表中到达终态的流程数 | 来自业务表 |
| data_created | 当日在核心业务表新建的记录数（合同、账单、工单等） | 数据贡献原始值 |
| feedback_submitted | 当日提交的建议/Bug 数 | 来自 feedback 表 |
| feedback_adopted | 当日被标记为"已采纳"的建议数 | 来自 feedback 表 |

#### 周/月粒度指标（user_period_metrics）

| 指标 | 口径 |
|------|------|
| active_days | 周期内 request_count > 0 的天数 |
| coverage_rate | 周期内 module_set 并集大小 / 全部业务模块数 |
| flow_close_rate | flow_completed / flow_started（flow_started=0 时为 NULL） |
| total_core_actions | 周期内 core_action_count 之和 |
| total_data_created | 周期内 data_created 之和 |
| total_feedback | 周期内 feedback_submitted 之和 |
| total_adopted | 周期内 feedback_adopted 之和 |

### 3.4 聚合表结构

```sql
CREATE TABLE user_daily_metrics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    dept_id             BIGINT      NOT NULL,
    stat_date           DATE        NOT NULL COMMENT '统计日期',
    active_minutes      INT         DEFAULT 0,
    request_count       INT         DEFAULT 0,
    module_set          JSON        DEFAULT NULL COMMENT '["contract","finance",...]',
    core_action_count   INT         DEFAULT 0,
    flow_started        INT         DEFAULT 0,
    flow_completed      INT         DEFAULT 0,
    data_created        INT         DEFAULT 0,
    feedback_submitted  INT         DEFAULT 0,
    feedback_adopted    INT         DEFAULT 0,
    created_at          DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, stat_date),
    INDEX idx_dept_date (dept_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_period_metrics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    dept_id             BIGINT      NOT NULL,
    period_type         TINYINT     NOT NULL COMMENT '1=周 2=月',
    period_start        DATE        NOT NULL COMMENT '周期起始日',
    active_days         INT         DEFAULT 0,
    coverage_rate       DECIMAL(5,4) DEFAULT NULL COMMENT '0.0000~1.0000',
    flow_close_rate     DECIMAL(5,4) DEFAULT NULL COMMENT 'NULL 表示无流程发起',
    total_core_actions  INT         DEFAULT 0,
    total_data_created  INT         DEFAULT 0,
    total_feedback      INT         DEFAULT 0,
    total_adopted       INT         DEFAULT 0,
    created_at          DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_period (user_id, period_type, period_start),
    INDEX idx_dept_period (dept_id, period_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.5 雷达五维评分

评分周期：月度。每个维度归一化为 0~100 分。

| 维度 | 计算公式 | 说明 |
|------|---------|------|
| **覆盖广度** | `coverage_rate × 100` | 用户访问模块数 / 总模块数 |
| **流程闭环** | `flow_close_rate × 100`（NULL 时不计分，该维度权重分摊到其他维度） | 发起的流程完成比例 |
| **使用频率** | `min(active_days / 工作日数 × 100, 100)` | 当月活跃天数占工作日的比例 |
| **数据贡献** | 同部门 `total_data_created` 分位数 × 100（P50 得 50 分） | 相对于同部门同事的数据产出 |
| **反馈贡献** | `(total_feedback + total_adopted × 2) / 部门人均值 × 50`，上限 100 | 采纳加权 ×3（1 + 额外 2） |

补充规则：
- active_minutes **不**出现在评分中（仅作辅助参考）
- 部门评分 = 成员评分均值（排除当月从未登录的用户）
- 空部门或单人部门正常计算，不做特殊处理
- B6 未上线期间：反馈贡献维度从 `feedback` 表 status=4（已采纳）直接统计

```sql
CREATE TABLE user_score (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    dept_id         BIGINT      NOT NULL,
    period_type     TINYINT     NOT NULL COMMENT '1=周 2=月',
    period_start    DATE        NOT NULL,
    dim_coverage    TINYINT UNSIGNED DEFAULT 0 COMMENT '覆盖广度 0~100',
    dim_flow        TINYINT UNSIGNED DEFAULT NULL COMMENT '流程闭环 0~100，NULL=无流程',
    dim_frequency   TINYINT UNSIGNED DEFAULT 0 COMMENT '使用频率 0~100',
    dim_data        TINYINT UNSIGNED DEFAULT 0 COMMENT '数据贡献 0~100',
    dim_feedback    TINYINT UNSIGNED DEFAULT 0 COMMENT '反馈贡献 0~100',
    total_score     TINYINT UNSIGNED DEFAULT 0 COMMENT '综合得分（有效维度均值）',
    created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_period (user_id, period_type, period_start),
    INDEX idx_dept_period (dept_id, period_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.6 路由-模块映射表

```sql
CREATE TABLE route_module_mapping (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    route       VARCHAR(255) NOT NULL COMMENT '路由模式 /contract/**',
    module      VARCHAR(64)  NOT NULL COMMENT '模块标识 contract',
    module_name VARCHAR(64)  NOT NULL COMMENT '模块中文名 合同管理',
    is_core     TINYINT(1)   DEFAULT 0 COMMENT '是否核心操作（写入类）',
    enabled     TINYINT(1)   DEFAULT 1,
    UNIQUE KEY uk_route (route)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 4. 功能二：建议与 Bug 提交

### 4.1 表结构

```sql
CREATE TABLE suggestion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(50)  NOT NULL COMMENT '标题（≤50字）',
    content         TEXT         DEFAULT NULL COMMENT '详细描述',
    type            TINYINT      NOT NULL COMMENT '1=Bug 2=建议 3=其他',
    module          VARCHAR(64)  DEFAULT NULL COMMENT '关联模块',
    source_url      VARCHAR(500) DEFAULT NULL COMMENT '提交时所在页面URL',
    user_agent      VARCHAR(255) DEFAULT NULL,
    user_id         BIGINT       NOT NULL,
    dept_id         BIGINT       NOT NULL,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：见4.2状态机',
    priority        TINYINT      DEFAULT 0 COMMENT '0=未定 1=低 2=中 3=高',
    assignee_id     BIGINT       DEFAULT NULL COMMENT '指派处理人ID',
    close_reason    VARCHAR(255) DEFAULT NULL COMMENT '关闭原因（关闭时必填）',
    resolved_at     DATETIME     DEFAULT NULL,
    tenant_id       BIGINT       DEFAULT NULL,
    create_by       VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)  DEFAULT NULL,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT          DEFAULT 0,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_dept (dept_id),
    INDEX idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='建议与Bug提交';

CREATE TABLE suggestion_image (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    suggestion_id   BIGINT       NOT NULL,
    file_id         BIGINT       NOT NULL COMMENT '关联 sys_file.id',
    sort_order      TINYINT      DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_suggestion (suggestion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE suggestion_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    suggestion_id   BIGINT       NOT NULL,
    action          VARCHAR(32)  NOT NULL COMMENT 'created/assigned/status_changed/commented',
    from_status     TINYINT      DEFAULT NULL,
    to_status       TINYINT      DEFAULT NULL,
    operator_id     BIGINT       NOT NULL,
    operator_name   VARCHAR(64)  NOT NULL,
    remark          VARCHAR(500) DEFAULT NULL COMMENT '操作备注/关闭原因',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_suggestion (suggestion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 4.2 状态机

```
                  ┌─────────────────────────────────────┐
                  │                                     ▼
[1 待处理] ──► [2 已确认] ──► [3 处理中] ──► [4 已解决] ──► [6 已关闭]
    │               │              │              │
    │               │              │              └──► [5 已采纳]（仅建议类型）
    │               │              │
    └───────────────┴──────────────┴──────────────────► [6 已关闭]
```

| 状态值 | 名称 | 说明 |
|--------|------|------|
| 1 | 待处理 | 初始状态，用户提交后 |
| 2 | 已确认 | 产品确认有效 |
| 3 | 处理中 | 已指派并在修复/规划中 |
| 4 | 已解决 | Bug 已修复 / 建议已实现 |
| 5 | 已采纳 | 建议被纳入计划（仅 type=2 建议类型可到达） |
| 6 | 已关闭 | 终态：无效/重复/不修复 |

合法流转矩阵：

| 当前\目标 | 1 | 2 | 3 | 4 | 5 | 6 |
|-----------|---|---|---|---|---|---|
| 1 待处理  | - | ✓ | - | - | - | ✓ |
| 2 已确认  | - | - | ✓ | - | - | ✓ |
| 3 处理中  | - | - | - | ✓ | ✓ | ✓ |
| 4 已解决  | - | - | - | - | - | ✓ |
| 5 已采纳  | - | - | - | - | - | ✓ |
| 6 已关闭  | - | - | - | - | - | - |

约束：
- 流转到「已关闭」必填 close_reason
- 流转到「已采纳」仅 type=2（建议）允许
- 指派处理人可在状态 1/2/3 时操作
- 已关闭为终态，不可回退

### 4.3 通知机制

状态变更后通知提交人，复用现有 `NotificationService`：

| 事件 | 通知渠道 | 模板内容 |
|------|---------|---------|
| 状态变更（非创建） | 站内信（sys_msg_record） | 「您提交的 [{title}] 状态已更新为 [{status_name}]」 |
| 被指派 | 站内信 | 「[{operator}] 将 [{title}] 指派给您处理」 |

通知失败不阻塞状态流转，失败记录写入日志。

---

## 5. API 设计

### 5.1 统计相关

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /bi/admin/north-star | 北极星指标 + 环比 | bi:admin:view |
| GET | /bi/admin/dept-radar | 部门雷达评分列表 | bi:admin:view |
| GET | /bi/admin/dept-radar/{deptId} | 部门下钻到个人 | bi:admin:view + 数据权限 |
| GET | /bi/admin/trend | 趋势图数据（周/月） | bi:admin:view |
| GET | /bi/product/module-usage | 模块使用率 | bi:product:view |
| GET | /bi/product/flow-analysis | 流程卡点分析 | bi:product:view |
| GET | /bi/product/api-top | API 调用 Top 榜 | bi:product:view |
| GET | /bi/product/feedback-board | 反馈看板 | bi:product:view |
| POST | /bi/admin/backfill | 手动补跑指定日期 | bi:admin:manage |

### 5.2 建议提交相关

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /suggestion | 提交建议/Bug | 登录即可 |
| GET | /suggestion/mine | 我的提交列表 | 登录即可 |
| GET | /suggestion/mine/{id} | 我的提交详情 | 本人 |
| GET | /suggestion/manage | 管理端列表（筛选/分页） | suggestion:manage |
| GET | /suggestion/manage/{id} | 管理端详情 | suggestion:manage |
| PUT | /suggestion/manage/{id}/status | 状态流转 | suggestion:manage |
| PUT | /suggestion/manage/{id}/assign | 指派处理人 | suggestion:manage |

---

## 6. 权限设计

新增权限节点（挂在系统管理菜单下）：

| 权限标识 | 说明 | 默认角色 |
|---------|------|---------|
| bi:admin:view | 管理层 BI 视图 | 管理员、部门负责人 |
| bi:admin:manage | 补跑等管理操作 | 管理员 |
| bi:product:view | 产品团队 BI 视图 | 产品角色 |
| suggestion:manage | 建议管理端 | 产品角色、管理员 |

普通用户只有提交和查看自己反馈的权限，无需额外配置。

部门管理者在管理层视图中只能看到**本部门**个人明细（通过现有 data_scope 机制控制）。

---

## 7. 非功能要求

| 项目 | 要求 |
|------|------|
| 日志采集性能 | 开启后 P99 延迟增加 <1ms（异步写入，队列容量 10000） |
| 聚合任务耗时 | 单日任务 <30 分钟 |
| 数据保留 | access_log 6 个月自动清理（每日 04:00 定时删除过期数据） |
| 可用性 | 日志写入失败不影响业务请求；通知失败不阻塞状态流转 |
| 隐私 | 不记录请求体/响应体；不记录 IP；仅记录路由模板 |

---

*设计文档 v1.0 · 2026-08-08*
