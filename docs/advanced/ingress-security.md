# 入站防护与宿主网关分工

Yu Flow 在网关层提供**可选**的入站防护（鉴权 / 防重放 / 限流 / IP 白名单），用于宿主尚未建设完整 API 网关时的兜底。默认关闭，避免与宿主网关「双鉴权」。

## 职责边界

| 场景 | 建议 |
|------|------|
| 宿主已有统一鉴权、限流、WAF | 保持 `yu.flow.ingress.enabled=false`（默认） |
| 宿主仅放行动态 API，缺少防重放/限流 | 打开 `ingress.enabled`，用全局默认 + 按接口覆盖 |
| 第三方调用 | 始终使用 `/flow-api/open/**` 开放入口（HMAC + nonce + 平台授权），**不受**接口 `authMode=NONE` 影响 |
| 管理端 `/flow-api/*` | 仍走管理 JWT，不在本能力范围内 |

防刷（验证码 / WAF）、熔断等能力一期不做，仅预留扩展位。

## 全局配置优先级

```text
系统配置 flow_sys_config（启用）  >  application.yml / 环境变量  >  代码默认
```

- 管理端「系统配置」分组 **入站防护 (INGRESS)** / **开放平台 (OPEN)** 可热更新（Redis 广播刷新）。
- **库中无键或停用**时自动回退 yml，保证初始化阶段无系统配置也能启动。
- `yu.flow.open.entry-prefix` **仅**走 yml（路由契约，不进系统配置）。

### yml 兜底示例

```yaml
yu:
  flow:
    ingress:
      # 总开关：false = 信任宿主（与历史行为一致）
      enabled: ${YU_FLOW_INGRESS_ENABLED:false}
      # 默认鉴权：NONE | HOST | OPEN
      default-auth-mode: ${YU_FLOW_INGRESS_DEFAULT_AUTH_MODE:NONE}
      # 默认防重放（仅 OPEN 鉴权生效；复用开放平台 nonce/skew）
      default-anti-replay: ${YU_FLOW_INGRESS_DEFAULT_ANTI_REPLAY:true}
      default-rate-limit-enabled: ${YU_FLOW_INGRESS_DEFAULT_RATE_LIMIT_ENABLED:false}
      default-rate-limit-qps: ${YU_FLOW_INGRESS_DEFAULT_RATE_LIMIT_QPS:100}
      # 空 = 不限制；支持单 IP / IPv4 CIDR，逗号分隔
      default-ip-allowlist: ${YU_FLOW_INGRESS_DEFAULT_IP_ALLOWLIST:}
      # 限流 Redis 失败策略（与开放平台一致，建议 fail-open）
      rate-limit-fail-open: ${YU_FLOW_INGRESS_RATE_LIMIT_FAIL_OPEN:true}
```

与旧配置的关系：

- `yu.flow.open.require-host-auth` / `OPEN_REQUIRE_HOST_AUTH`：仅在 **ingress 关闭** 时生效。
- 打开 ingress 后，以 `EffectiveSecurity.authMode` 为准（`HOST` / `NONE` / `OPEN`）。

## 按接口覆盖（`security_config`）

接口「基本信息 → 入站防护」写入 `flow_api_info.security_config`，并打进 `publishedSnapshot`。**改完需发布才影响线上。**

```json
{
  "authMode": "INHERIT",
  "antiReplay": null,
  "rateLimitEnabled": null,
  "rateLimitQps": null,
  "ipAllowlist": null
}
```

| 字段 | 取值 | 含义 |
|------|------|------|
| `authMode` | `INHERIT` / `NONE` / `HOST` / `OPEN` | `INHERIT` → 全局 `default-auth-mode` |
| `antiReplay` | `null` / `true` / `false` | `null` → 全局；仅 `OPEN` 时生效 |
| `rateLimitEnabled` | `null` / `true` / `false` | `null` → 全局 |
| `rateLimitQps` | `null` / number | 启用限流时的秒级 QPS |
| `ipAllowlist` | `null` / `""` / `"ip,cidr"` | `null` → 全局；`""` → 明确不限制 |

单字段优先级：**接口显式值 > 全局默认**。`ingress.enabled=false` 时强制等效 `NONE` + 无限流 + 无 IP 限制（开放入口除外）。

## 请求处理顺序（真实 path）

```text
匹配已发布 API
  →（可选）allow-direct-path + AppKey → 开放鉴权链路
  → ingress.enabled?
       否 → 可选 require-host-auth → 契约校验 → 执行
       是 → IP 白名单 → authMode(NONE|HOST|OPEN) → 接口限流 → 契约校验 → 执行
```

错误响应与开放平台一致：HTTP 状态码 + JSON `errorCode` / `msg`（前缀 `INGRESS_*`，如 `INGRESS_RATE_LIMITED`、`INGRESS_HOST_AUTH_REQUIRED`）。

## 典型组合

1. **全局 HOST + 某接口 NONE**：该接口可匿名（宿主需自行放行该 path）。
2. **全局 NONE + 某接口 OPEN**：仅该接口要求 AppKey/HMAC；防重放随 `antiReplay`。
3. **接口 `rateLimitQps=1`**：连续请求可出现 `429 INGRESS_RATE_LIMITED`。
4. **改 securityConfig 未发布**：线上仍用旧快照。

## 与开放平台的分工

| 入口 | 鉴权与限流 |
|------|------------|
| `/flow-api/open/{真实path}` | 始终 `OpenAuthService`（平台授权、平台级 QPS、nonce） |
| 真实发布 path + ingress OPEN | 复用开放凭证验签 + 接口授权；另可叠加接口级 ingress 限流 |
| 真实发布 path + ingress HOST | `HostAuthenticationProbe`（宿主实现登录探测） |

宿主侧实现 `HostAuthenticationProbe` Bean 即可对接自身 Session / JWT；未实现时默认宽松（视为已登录），生产务必自行实现。

与 **宿主 API 托管（REPLACE / WRAP）** 的关系：`authMode=HOST` 只表示「调用方需已过宿主登录」，不是「接口由宿主实现」。WRAP 在 `ingress.enabled=false` 时信任宿主鉴权、不强制管理端 JWT。详见 [宿主 API 托管](./host-api-governance.md)。
