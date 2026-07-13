# zhyq 代码模式规范(子任务必须严格遵循)

后端根目录: `/Users/a408/Documents/dipark/zhyq/backend`
前端根目录: `/Users/a408/Documents/dipark/zhyq/frontend`
Java 包根: `com.zhyq.park`  | 后端接口前缀: `/api`(context-path 已配)

## 后端分层(每张表一套)
包结构:`com.zhyq.park.<模块>.{entity,mapper,controller}` — service 层省略,controller 直接用 mapper(本项目务实做法)。

### Entity
```java
package com.zhyq.park.<模块>.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data; import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
@TableName("表名")
public class Xxx extends BaseEntity {
    // 只写业务字段,不要写 id/tenantId/createBy/createTime/updateBy/updateTime/version/deleted
    // 这些都在 BaseEntity 里,MyMetaObjectHandler 自动填充
}
```
DECIMAL→`BigDecimal`, DATE→`LocalDate`, DATETIME→`LocalDateTime`, TINYINT/INT→`Integer`, BIGINT→`Long`。

### Mapper
```java
package com.zhyq.park.<模块>.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.<模块>.entity.Xxx;
public interface XxxMapper extends BaseMapper<Xxx> {}
```

### Controller(参照 system.SysUserController)
- `@RestController @RequestMapping("/<模块>/xxx")`,`@RequiredArgsConstructor` 注入 mapper。
- 统一返回 `com.zhyq.park.common.result.Result<T>`,分页返回 `Result<PageResult<T>>`。
- 分页用 `mapper.selectPage(new Page<>(pageNo,pageSize), lambdaQueryWrapper)`,取 `PageResult.of(p.getTotal(), p.getRecords())`。
- 标准端点:`GET /page`(分页+条件查询)、`GET /{id}`、`POST`(新增)、`PUT`(改)、`DELETE /{id}`、按需 `GET /list`。
- 条件用 `LambdaQueryWrapper`,字符串条件加 `StringUtils.hasText(x)` 守卫。
- `@Tag`/`@Operation` 加中文 swagger 注解。

## 前端(参照 views/system/User.vue 和 api/system.js)
- API 文件:`src/api/<模块>.js`,导出 `xxxApi = { page, add, update, remove, ... }`,用 `@/utils/request`。
- 页面:`src/views/<模块>/Xxx.vue`,`<script setup>` + Element Plus。
- 列表页结构:`.search-bar`(查询表单 el-form inline) + `.table-card`(toolbar 新增按钮 + el-table border stripe + el-pagination) + el-dialog 表单。
- 状态字段用 `el-tag` 着色(见附录B状态)。金额/面积展示带单位。
- 组件、图标已全局自动导入(unplugin),无需手动 import Element 组件。
- 路由已在 `src/router/index.js` 注册好,菜单在 `src/layout/menu.js`;若新增页面路径不在其中需补上。

## 状态色(规格书 §4.2)
待处理=橙warning / 执行中=蓝primary / 成功=绿success / 终止异常=红danger / 归档=灰info

## 重要
- 不改 `common/`、`ZhyqParkApplication`、`application.yml`、`db/migration/*`(表已建好)。
- 每个子任务完成后自己 `mvn -q compile` 验证后端可编译(在 backend 目录)。
- 前端不要跑 build,只写文件,保证语法正确即可。
