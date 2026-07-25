# 生产环境加固清单

Yu Flow 以「可嵌入、可演示」为出发点，部分能力在本地开发档位下较为宽松。生产（尤其公网可达）部署前，请逐项核对本清单。

> 原则：**默认即安全（fail-closed）**。所有放松项都必须显式配置，且写入运维记录。

## 一、必须项（不满足禁止上线）

| 检查项 | 配置 / 环境变量 | 生产要求 |
|--------|-----------------|----------|
| JWT 密钥 | `YU_FLOW_JWT_SECRET` | 必须设置，≥32 字符高熵随机串；缺失时启动即失败（fail-closed） |
| AES 密钥 | `YU_FLOW_AES_SECRET` | 必须设置，恰好 16/24/32 字节；缺失、为空或长度不合法时启动即失败 |
| 管理员口令 | `YU_FLOW_ADMIN_PASSWORD` | 必须通过环境变量注入强口令；仍为默认值 `123456` 时启动即失败 |
| 不安全默认拦截 | `YU_FLOW_FAIL_ON_INSECURE_DEFAULTS` | 保持 `true`（默认）；`false` 仅限本地 |
| yml 管理员回退 | `YU_FLOW_ALLOW_YML_ADMIN_FALLBACK` | 保持 `false`（默认） |
| 数据库口令 | `SPRING_DATASOURCE_PASSWORD` | 环境变量注入，禁止弱口令 |

## 二、默认已收紧（确认未被放开）

| 检查项 | 配置 | 默认 | 说明 |
|--------|------|------|------|
| 宿主演示接口 | `yu.flow.host-demo.enabled` | `false` | `/yu-demo/**` 仅用于本地验收 WRAP，生产不得开启 |
| 匿名可调接口 | `yu.flow.security.allow-ingress-auth-none` | `false` | 禁止接口 `authMode=NONE`；应急放开后须尽快关闭 |
| 演示模式 | `yu.flow.demo-mode` | `false` | 仅公开演示环境开启；语义见[演示模式](../manual/demo-mode.md) |
| 出站私网阻断 | `yu.flow.security.block-private-outbound` | `true` | HttpRequest 节点禁止访问私网/元数据地址（防 SSRF） |
| 入站防护 | `yu.flow.ingress.enabled` + `default-auth-mode` | `true` + `HOST` | 已发布 API 默认要求管理端登录；关闭即信任宿主网关，需宿主自证鉴权 |
| 开放平台明文密钥 | `yu.flow.open.allow-plain-secret` | `false` | 保持关闭 |
| 开放平台 nonce | `yu.flow.open.nonce-fail-closed` | `true` | Redis 异常时拒绝请求，防重放降级 |

## 三、按需收紧（结合部署形态评估）

| 检查项 | 配置 | 建议 |
|--------|------|------|
| 开放平台总开关 | `yu.flow.open.enabled`（默认 `true`） | **不对第三方开放则设为 `false`**，最小化暴露面 |
| 入站限流 | `ingress.default-rate-limit-enabled`（默认 `false`） | 公网直连场景建议开启并压测 QPS 阈值 |
| 限流降级策略 | `ingress.rate-limit-fail-open`（默认 `true`） | 高危环境改 `false`（Redis 故障时拒绝），并保障 Redis 高可用 |
| 脚本引擎语言面 | `yu.flow.security.script-allowed-languages`（默认 `aviator,spel,javascript`） | `groovy` / `python` 逃逸面较大，默认禁用；确需开放请显式加入白名单并限制编辑权限 |
| HttpRequest `ignoreSsl` | `yu.flow.security.allow-ignore-ssl`（默认 `true`） | 生产设 `YU_FLOW_ALLOW_IGNORE_SSL=false` 一刀切禁用跳过证书校验；确需自签名请配置受信 CA |
| 宿主登录探测 | `HostAuthenticationProbe` Bean | `ingress` 关闭 + WRAP 场景下**必须**由宿主实现；未实现时视为已登录（宽松） |

## 四、运维与审计

- **Excel 签名下载**：签名链允许绕过管理端 JWT（设计如此）。token 采用 HMAC-SHA256 签名（JWT 密钥为弱默认值时拒绝签发），TTL 强制收敛在 30～3600 秒；**在有效期内可重复下载**（兼容浏览器/下载器多次请求），请保持短 TTL、勿将签名 URL 落入日志或对外分享渠道。
- **系统配置热更新**：`flow_sys_config`（INGRESS/OPEN 分组）优先于 yml；上线后请在管理端「系统配置」复核实际生效值，而非仅看 yml。
- **登录审计与调用日志**：保持开启，接入宿主统一日志采集；开放平台调用日志默认开启。
- **安全基线复扫**：每季度对照 `report/`、`sast/` 存量报告复查未关闭项。

## 五、上线前快速自检

```bash
# 1. 演示/调试面已关闭（应返回 404）
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/yu-demo/host-ping

# 2. 已发布 API 匿名访问被拒（未登录应 401/403，而非 200）
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/<已发布业务path>

# 3. 开放入口未授权访问被拒
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/flow-api/open/<path>
```

## 相关文档

- [入站防护与宿主网关分工](./ingress-security.md)
- [宿主 API 托管（替换 / 包裹）](./host-api-governance.md)
- [第三方开放平台接入](./open-platform-integration.md)
- [权限与安全](./security.md)
- [演示模式安全管控](../manual/demo-mode.md)
