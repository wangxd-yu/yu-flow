# 系统深度集成

将 `flow-api` 以 Spring Boot Starter / JAR 嵌入宿主应用时的鉴权与路径约定。

业务 API **默认零改造**：未纳管路由仍由宿主处理。宿主原生接口的日志 / 计量 / REPLACE·WRAP 见 [宿主 API 托管](./host-api-governance.md)。

## 1. 双层鉴权（推荐生产嵌入形态）

| 层 | 职责 | 典型手段 |
|----|------|----------|
| **宿主** | 能否进入 Flow 管控区域 | Spring Security：`/flow-ui/**`、`/flow-api/**` 要求已登录（可再限角色） |
| **Flow** | 进入后能干哪些管理操作 | 管理端 JWT + RBAC 权限码 |

**必须分别登录宿主与 Flow**：宿主登录 ≠ Flow 管理会话。  
**不做**宿主登录后静默换发 / 同步 Flow JWT；管理员在 Flow 登录页（或等价入口）单独登录。

### 1.1 宿主 Security 示例

使用 `authenticated()`（保留 Session / SecurityContext），**不要**对这两段路径使用 `web.ignoring()`（否则 SPI / Probe 读不到宿主用户）。

```java
http.authorizeHttpRequests(auth -> auth
    // 开放入口：AppKey + HMAC（无宿主浏览器会话）
    .requestMatchers("/flow-api/open/**").permitAll()
    // 短期签名下载链
    .requestMatchers("/flow-api/download/excel/**").permitAll()
    // OSS 原生接口：网关放行，细控在场景访问规则 / @RequirePerm（不要 web.ignoring，以免丢掉身份头）
    .requestMatchers("/flow-api/oss/**").permitAll()
    // 管控面：必须先登录宿主，再由 Flow 校验 JWT / RBAC
    .requestMatchers("/flow-api/**", "/flow-ui/**").authenticated()
    // …宿主自有规则
);
```

若配置了 `server.servlet.context-path`（如 `/flow`），matcher 需带上 context-path，或按宿主框架约定写相对应用路径。

### 1.2 Flow 侧第二关（进程内）

配置项：`yu.flow.security.management-require-host-auth`（**默认 `false`**，仅需加强防护时开启）。

```yaml
yu:
  flow:
    security:
      management-require-host-auth: true   # 可选加强
```

开启后：

- 对 `/flow-api/**`（login / captcha / open / 签名下载 / **OSS 原生接口**除外）：先校验管理端 JWT，再调用 `HostAuthenticationProbe`。
- **嵌入宿主**：请覆盖 `HostAuthenticationProbe`，改为读宿主 Session / `SecurityContext`，这样即便宿主误配 `permitAll`，仅有 Flow JWT、无宿主登录的请求仍会被拒绝。
- 未开启时：管理端只走 Flow JWT + RBAC；宿主侧仍可用 Security `authenticated()` 做外层限制（与本开关独立）。

```java
@Component
public class HostSessionAuthenticationProbe implements HostAuthenticationProbe {
    @Override
    public boolean isAuthenticated(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}
```

### 1.3 例外路径（不要套「必须宿主登录」）

| 路径 | 原因 |
|------|------|
| `/flow-api/open/**` | 第三方 AppKey |
| `/flow-api/download/excel/**` | 链上 HMAC + TTL |
| `/flow-api/oss/**` | 网关不强制 Flow 管理 JWT。上传/下载按 OSS 场景访问规则（`caller_policy`）+ `FlowHostPrincipalProvider`；连接/场景等管理 CRUD 由 `@RequirePerm` 校验 Flow JWT |
| 已发布业务真实 path / WRAP | 走 ingress / 宿主原有鉴权，**不是**管控面前缀 |

`/flow-api/login`、`/flow-api/login/captcha`、`/flow-api/login/public-key`：Flow 网关不强制 JWT；宿主侧仍建议要求「已登录宿主」后再允许打开 Flow 登录页并提交登录。登录口令经 SM2（`passwordCipher`）传输，不再接受明文 `password`。

## 2. 身份与数据范围 SPI（OSS / 行级权限）

与「能否调用管理 API」不同：上传人、隐私下载可见范围由下列 SPI 提供（宿主实现一次即可）：

- `FlowHostPrincipalProvider` — 当前用户（`userType` / 角色 / 权限 / 部门，供调用方策略匹配）
- `FlowHostDataScopeProvider` — 数据范围（本人 / 部门列表 / …）
- `FlowHostIdentityCatalogProvider`（可选）— 管理端调用方策略的下拉清单。有 SPI 时优先；否则可用「平台设置 → 宿主机配置 → 宿主身份目录」启用 5 条系统保留接口。都未对接时独立运行有前端示例，仍可手输码；不影响运行时匹配

OSS 台账 `flow_oss_object.uploaded_by` 存各用户体系内主键，`uploaded_by_user_type` 存 `FlowHostPrincipal.userType`（`ADMIN` / `END_USER` / `OPEN_APP`，宿主可扩展）。「仅本人」与用户配额按这两列一起匹配；`userId` 不要再拼类型前缀。

OSS 上传场景可另挂宿主调用方策略（`flow_oss_upload_profile.caller_policy`，结构与已发布 API 的 `callerPolicy` 相同，分 `upload` / `download`）。默认 `enabled=false`，不改变历史行为。失败码 `403 OSS_CALLER_DENIED`。开放平台 AppKey 上传（`userType=OPEN_APP`）不跑该匹配。配置入口：管理端 → OSS 上传场景 → 上传与访问权限。详见管理台「核心用户体系与数据隔离」§3.1。

未覆盖时使用内置 JWT 解析。详见管理台「鉴权与安全集成」文档与 [入站防护](./ingress-security.md)。

## 3. 宿主放行前缀（仅两类）

宿主 Security 只需识别：

- `/flow-ui/**` — 管理台 SPA
- `/flow-api/**` — Flow 自有 API（子路径鉴权策略由 Flow 网关区分）

不必再为 OSS 等拆新前缀；OSS 管理与运行接口均在 `/flow-api/oss/**` 下。

## 4. 相关文档

- [入站防护与宿主网关分工](./ingress-security.md)
- [宿主 API 托管（替换 / 包裹）](./host-api-governance.md)
- [第三方开放平台接入](./open-platform-integration.md)
- [生产加固清单](./production-hardening.md)
