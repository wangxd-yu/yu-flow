# 宿主 API 托管（替换 / 包裹）

Yu Flow 嵌入宿主后，可在 **接口管理（Controller）** 中纳管宿主原生 HTTP 接口，与动态 API 共用发布、日志、计量与入站防护能力。

## 核心原则

| 原则 | 说明 |
|------|------|
| 零改造 | 宿主业务 Controller / 调用方 **无需** 为托管加参数或改 URL |
| 默认宿主鉴权 | `yu.flow.ingress.enabled=false`（默认）时，**包裹（WRAP）** 不强制管理端 JWT |
| 显式纳管 | 未登记发布的宿主路由仍透明放行，行为与接入前一致 |
| 按接口模式 | 每条资产选择 **替换 REPLACE** 或 **包裹 WRAP** |

> 勿与入站 `authMode=HOST` 混淆：那是「要求调用方已过宿主登录」，不是「本接口由宿主实现」。

## 两种同名拦截

| 模式 | `interceptMode` | `serviceType` | 行为 |
|------|-----------------|---------------|------|
| **替换** | `REPLACE` | FLOW / DB / JSON / STRING | 网关短路执行 Yu Flow 引擎，**宿主同 path 不再到达** |
| **包裹** | `WRAP` | `HOST` | 可选防护后转发宿主 Controller，记录计量/日志，**响应默认透传** |

同一已发布 `method + path` 全局唯一，两模式互斥。

## 鉴权分工

```text
REPLACE + ingress 关 → 现网策略：要求管理端 JWT（防止动态 API 匿名裸奔）
WRAP    + ingress 关 → 信任宿主 Security（可选 open.require-host-auth Probe）
任意模式 + ingress 开 → 按接口 security_config / 全局默认合并
开放入口 /flow-api/open/** → 始终走开放平台鉴权（与业务同名 path 独立）
  └─ 授权 WRAP 资产后：鉴权通过 → 改写为业务 path → 受控转发宿主（响应透传）
  └─ WRAP 不支持开放 /export 导出
```

## 运营建议

- WRAP 默认关闭执行日志；高流量接口慎开，避免日志膨胀。
- WRAP 勿对大文件/流式接口开启 body 落库。
- WRAP **不套用**「返回包装 / 查询缓存」（透传宿主响应）；统一响应壳或缓存请用 REPLACE。
- REPLACE 发布时若检测到宿主同 method+path，UI 会强警告确认；无宿主冲突则不弹危险确认。
- 将宿主接口「升级」为 REPLACE 前务必确认调用方已切换到新实现。

## 日志策略（`hostBinding.logMode`）

| 模式 | 行为（需同时打开接口「执行日志」） |
|------|-----------------------------------|
| `ALL` | 全量记（不含 body） |
| `ERROR_ONLY` | 仅失败（导入草稿默认） |
| `SAMPLE` | 失败必记；成功按 `logSamplePermille`（千分比）采样 |

## 路由探活

- 开启 `probeEnabled` 后，后台每约 60s 检查宿主 MVC 是否仍注册该 path（**无 HTTP 回环**）。
- 列表健康列会叠加探活失败信号；表单可「立即探测」。

## 从宿主导入

接口列表 → **从宿主导入**：扫描 `RequestMappingHandlerMapping`（排除 `/flow-api`、`/flow-ui`），多选生成 **未发布** WRAP 草稿。

编辑 WRAP 资产时，顶栏路径为**宿主路由下拉选择**（方法随选项带入），不再手输业务路径。

新建/编辑页工具菜单支持 **从 cURL 导入**（纯前端）：填入 Method / Path 与请求契约，不改服务实现，也不影响 WRAP 网关主路径。

## 相关文档

- [入站防护与宿主网关分工](./ingress-security.md)
- [系统深度集成](./embed-integration.md)
- [动态 API 手册](../manual/dynamic-api.md)
