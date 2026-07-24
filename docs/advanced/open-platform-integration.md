# 第三方开放平台接入

宿主系统可通过 Yu Flow **开放入口**把已发布 API 安全暴露给外部伙伴，使用 AppKey + HMAC 签名鉴权，并按平台粒度做接口授权、IP 白名单与限流。

> 非开放入口的已发布 API 入站兜底（HOST/OPEN/限流/IP）见 [入站防护与宿主网关分工](./ingress-security.md)。

## 1. 调用入口

- 管理端：`/flow-api/open-platforms`（平台 / 凭证 / 授权 / 文档导出）
- **推荐**开放入口：`/flow-api/open/{真实发布 path}`
- 示例：接口发布 path 为 `/demo/hello` → 外部调用 `/flow-api/open/demo/hello`
- **可选**直连真实 path：开启 `yu.flow.open.allow-direct-path=true` 后，第三方可对已发布 URL 带 AppKey 头直打（宿主需放行带 `X-Yu-App-Key` 的请求）

前缀可通过 `yu.flow.open.entry-prefix` 调整（**仅 yml**，不进系统配置）。

运行时开关（`OPEN_ENABLED`、`OPEN_ALLOW_PLAIN_SECRET`、`OPEN_SKEW_SECONDS` 等）优先读管理端「系统配置 → 开放平台」；库中无键或停用时回退 `yu.flow.open.*` / 环境变量。

## 2. 鉴权头

| Header | 说明 |
|---|---|
| `X-Yu-App-Key` | 平台 AppKey |
| `X-Yu-Timestamp` | Unix 秒级时间戳 |
| `X-Yu-Nonce` | 随机串，时钟窗口内不可重复 |
| `X-Yu-Signature` | HMAC-SHA256 十六进制签名 |
| `X-Yu-App-Secret` | 明文 Secret（仅无 Signature 时；**生产默认关闭**） |

生产务必保持 `yu.flow.open.allow-plain-secret=false`（可用 `YU_FLOW_OPEN_ALLOW_PLAIN_SECRET` 覆盖）。

## 3. 签名串

```
METHOD\nrealPath\ntimestamp\nonce\nbodySha256OrEmpty
```

- `realPath`：去掉开放前缀后的路径（与发布 path 一致，建议以 `/` 开头）
- `bodySha256OrEmpty`：当 `yu.flow.open.include-body-hash=true` 时为 body 的 SHA-256 hex；无 body 时为空串
- 时钟偏差默认 ±300 秒（`yu.flow.open.skew-seconds`）
- Nonce 依赖 Redis；`yu.flow.open.nonce-fail-closed=true` 时 Redis 不可用则拒绝请求

## 4. 授权与方法限制

- 平台须勾选「已发布」接口后才能通过开放入口访问
- `allow_methods` 为空：跟随接口发布 method
- `allow_methods` 显式配置（如 `GET,POST`）：请求方法必须命中，否则返回 `403 OPEN_AUTH_METHOD_DENIED`

## 5. 常见错误码

| HTTP | code | 含义 |
|---|---|---|
| 401 | OPEN_AUTH_MISSING | 缺少凭证头 |
| 401 | OPEN_AUTH_INVALID | 签名 / 密钥错误 |
| 401 | OPEN_AUTH_EXPIRED | 时间窗 / 凭证 / nonce 过期 |
| 403 | OPEN_AUTH_DENIED | 平台停用或未授权接口 |
| 403 | OPEN_AUTH_IP_DENIED | IP 不在白名单 |
| 403 | OPEN_AUTH_METHOD_DENIED | 授权未开放该 HTTP 方法 |
| 401 | OPEN_HOST_AUTH_REQUIRED | 要求宿主登录（`require-host-auth`） |
| 429 | OPEN_RATE_LIMITED | 平台 QPS 限流 |

失败响应含 `errorCode`（机器可读）与 `msg`。

## 5.1 宿主登录探测（可选）

当已发布 API **未带** AppKey、且 `yu.flow.open.require-host-auth=true` 时，网关调用 SPI：

```java
public interface HostAuthenticationProbe {
    boolean isAuthenticated(HttpServletRequest request);
}
```

默认 Bean 恒返回 `true`（宽松）。宿主可提供自己的 Bean（例如读 SecurityContext），防止业务 URL 被误配成 `permitAll` 后匿名访问。

## 6. 文档与联调

管理端平台详情「文档导出」可：

- 预览 / 下载对接说明（不含 Secret）
- 导出 OpenAPI JSON / YAML、Postman Collection、Markdown（含 curl）
- 复制完整 Base URL

宿主文档站亦可将本页作为对外集成说明入口。

## 7. 关键配置摘要

```yaml
yu:
  flow:
    open:
      enabled: true
      entry-prefix: /flow-api/open
      skew-seconds: 300
      allow-plain-secret: false
      rotate-grace-hours: 24
      call-log-enabled: true
      include-body-hash: true
      nonce-fail-closed: true
      allow-direct-path: false
      require-host-auth: false
```

签名辅助类：`org.yu.flow.module.open.auth.OpenAuthSignatures`（`buildPayload` / `sign`）。

入站摘要日志写入 `flow_log_open_call`（与出站 `flow_log_third` 不同）；平台可单独关闭。
