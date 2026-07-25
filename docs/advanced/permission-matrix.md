# 权限码矩阵参考

本页给出管理端全量 Controller 与权限码的对照矩阵，供角色配置与安全审计使用。

## 鉴权模型速览

- 管理端接口统一经 `FlowApiGatewayFilter` 校验 JWT（认证），再由 `@RequirePerm` 切面校验权限码（授权）。
- **HTTP 方法分流**：当注解同时声明 `xxx:view` 与 `xxx:write` 时，`GET/HEAD/OPTIONS` 只需 `view`，`POST/PUT/PATCH/DELETE` 必须持有 `write`——只读账号无法通过类级注解越权写。
- 方法级注解优先于类级注解；`ADMIN` 内置角色持有 `*` 通配。
- RBAC 总开关：系统配置 `RBAC_ENABLED`（默认 `true`）。

## 内置角色

| 角色 | 权限范围 |
|------|----------|
| `ADMIN` | `*`（全部） |
| `OPERATOR` | 编排读写 + 观测 + 基础设施写 + 日志清理；无用户/系统配置写 |
| `VIEWER` | 各模块 `*:view` 只读 |

## 权限码 × Controller 矩阵

### 编排与资产（类级 view/write 双码，按 HTTP 方法分流）

| Controller | 路径前缀 | 权限码 |
|------------|----------|--------|
| FlowApiController | `/flow-api/api` | `flow:api:view` / `flow:api:write`（调试执行显式要求 write） |
| HostApiController | `/flow-api/api/host` | `flow:api:view` / `flow:api:write` |
| DebugController | `/flow-api/debug` | `flow:api:view` / `flow:api:write` |
| FlowDirectoryController | `/flow-api/directories` | `flow:api:view` / `flow:api:write` |
| FlowServiceFlowController | `/flow-api/service-flow` | `flow:service:view` / `flow:service:write` |
| FlowTaskController | `/flow-api/task` | `flow:task:view` / `flow:task:write` |
| FlowModelInfoController | `/flow-api/models` | `flow:model:view` / `flow:model:write` |
| PageInfoController / PageDirectoryController | `/flow-api/pages`、`/flow-api/page-directories` | `flow:page:view` / `flow:page:write` |
| DynamicDataSourceController | `/flow-api/dataSource` | `flow:ds:view` / `flow:ds:write` |
| FlowOpenPlatformController | `/flow-api/open-platforms` | `flow:open:view` / `flow:open:write` |
| ResponseTemplateController | `/flow-api/response-templates` | `sys:template:view` / `sys:template:write` |
| SysMacroController | `/flow-api/sys-macros` | `sys:macro:view` / `sys:macro:write` |

### 观测与日志

| Controller | 路径前缀 | 权限码 |
|------------|----------|--------|
| MetricsController | `/flow-api/metrics` | `flow:runtime:view`（`POST /health` 为批量查询，非写操作） |
| FlowExecutionLogController | `/flow-api/log/execution` | `log:view` |
| FlowTaskLogController | `/flow-api/log/task` | `log:view`；`DELETE /clear/{taskId}` 要求 `log:write` |
| FlowServiceLogController | `/flow-api/log/service` | `log:view`；`DELETE /clear/{serviceId}` 要求 `log:write` |
| FlowThirdLogController | `/flow-api/log/third` | `log:view` |
| LoginLogController | `/flow-api/log/login` | `log:view` |
| AuditLogController | `/flow-api/log/audit` | `log:view`（方法级） |
| AlertController | `/flow-api/alerts` | `flow:alert:view` / `flow:alert:edit`（方法级） |

### 系统管理（方法级显式注解）

| Controller | 路径前缀 | 权限码 |
|------------|----------|--------|
| SysConfigController | `/flow-api/sys-configs` | `sys:config:view` / `sys:config:write` |
| SysUserController | `/flow-api/sys-users` | `sys:user:view` / `sys:user:write` |
| SysRoleController | `/flow-api/sys-roles` | `sys:role:view` / `sys:role:write` |
| FlowMailController | `/flow-api/mail` | `POST /test` 要求 `sys:config:write` |
| ReleaseController | `/flow-api/release` | `flow:release:view` / `flow:release:edit`（方法级） |
| OpenApiController | `/flow-api/v3` | `docs:view` |

### 无 `@RequirePerm`（设计如此）

| Controller | 说明 |
|------------|------|
| FlowLoginController | 登录 / 验证码入口，登录前无凭证 |
| AuthController | `me` / `logout` / `change-password`，JWT 认证保护，操作仅作用于当前会话 |
| ExcelSignedDownloadController | 短期签名 token 保护（HMAC + TTL），见[生产加固清单](./production-hardening.md) |
| HostDemoController | 本地演示接口，默认关闭（`yu.flow.host-demo.enabled=false`） |
| FlowUiController | SPA 静态渲染，由 `FlowUiInterceptor` 管控 |

## 审计记录

- 2026-07-25 全量矩阵核查：唯一真实缺口为任务/服务日志 `DELETE /clear/{id}`（类级仅 `log:view`，只读账号可清日志）。已修复：新增 `log:write` 权限码（迁移 `V2026_07_25_02`，授予 OPERATOR），两个 clear 接口补方法级 `@RequirePerm("log:write")`。
- 其余「仅类级双码」的写方法均受 HTTP 方法分流保护，无越权面。

## 相关文档

- [架构安全与权限配置](./security.md)
- [生产环境加固清单](./production-hardening.md)
- [入站防护与宿主网关分工](./ingress-security.md)
