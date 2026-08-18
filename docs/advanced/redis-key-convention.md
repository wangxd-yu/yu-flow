# Redis Key 命名规范与系统清单

> **日期**: 2026-08-18  
> **范围**: `flow-api` 系统自管的 Redis Key / Pub/Sub 频道  
> **对齐**: 表名前缀混合规则（`flow_{域}_{用途}`）在 Redis 侧的对应写法

本文只盘点**系统写入**的 key。编排画布「Redis 节点」的 key 由 DSL 调用方自定，**不强制** `flow:` 前缀。

权限字符串（如 `flow:api:view`、`flow:oss:admin`）走 RBAC，不是 Redis key。

---

## 1. 规范

```
flow:{域}:{用途}[:细分…][:id…]
```

| 规则 | 说明 |
|------|------|
| 产品前缀 | 一律 `flow:`，禁止裸业务名、禁止 `yu-flow:` |
| 分隔符 | 单冒号 `:`。禁止 `::` |
| 大小写 | 段名全小写。禁止 camelCase（如 `uiEnable`） |
| 域 | 与业务/横切面一致：`api` / `task` / `mq` / `oss` / `log` / `metrics` / `sys` / `open` / `alert` / `login` / `directory` / `asset` / `ingress` / `ui` |
| 用途 | `lock` / `cleanup` / `dedup` / `captcha` / `nonce` / `resp` / `map` / `refresh` / `rl`（限流窗口，见下） |
| Pub/Sub | `flow:{域}:{资源}:refresh:topic` |
| 锁 | `flow:{域}:{用途}:lock` 或 `flow:{域}:lock:{id}` |
| 高基数字段 | 时间桶、hash、窗口秒等放在 **最后一段** |
| 缩写 | 允许稳定缩写：`rl` = rate-limit 窗口，`m` = minute 指标桶。新增 key 优先写全称 |

**不要**对系统前缀做 `KEYS flow:*` 后批量 `DEL`：会误删锁、限流窗口、验证码和指标桶。

---

## 2. 系统 Key 清单

### 2.1 Pub/Sub 频道（无 TTL，频道名不是 KV）

| Key | 类型 | 代码 | 说明 |
|-----|------|------|------|
| `flow:api:cache:refresh:topic` | channel | `FlowApiCacheManager` | 已发布 API 内存缓存刷新 |
| `flow:sys:config:refresh:topic` | channel | `SysConfigCacheManager` | 系统配置刷新 |
| `flow:sys:macro:refresh:topic` | channel | `SysMacroCacheManager` | 宏刷新 |
| `flow:directory:cache:refresh:topic` | channel | `FlowDirectoryServiceImpl` | 目录树刷新 |
| `flow:open:credential:refresh:topic` | channel | `OpenPlatformCache` | 开放平台凭证刷新 |
| `flow:asset:ref:refresh:topic` | channel | `FlowReferenceIndex` | 资产引用索引刷新 |

### 2.2 分布式锁 / 定时任务互斥

| Key | 类型 | TTL | 代码 |
|-----|------|-----|------|
| `flow:task:lock:{taskId}` | STRING | 任务超时分钟 | `FlowTaskScheduler` |
| `flow:log:cleanup:lock` | STRING | 30 min | `LogCleanupTask` |
| `flow:oss:cleanup:lock` | STRING | 30 min | `OssObjectCleanupJob` |
| `flow:metrics:flush:lock` | STRING | 刷盘窗口 | `MetricsFlushJob` |
| `flow:metrics:cleanup:lock` | STRING | 10 min | `MetricsCleanupJob` |
| `flow:alert:lock:rule:{ruleId}` | STRING | ≥30s | `AlertDispatchServiceImpl` |
| `flow:alert:lock:sysconfig` | STRING | ≥30s | 同上 |

### 2.3 缓存 / 幂等 / 限流 / 开关

| Key | 类型 | 说明 |
|-----|------|------|
| `flow:api:map` | HASH | 已发布 API。field = `{METHOD}-/{path}`。写入：`FlowRedisServeUtil` |
| `flow:api:map:temp` | HASH | 刷新暂存，rename 到 `flow:api:map` |
| `flow:api:resp:{apiId}:{sha256}` | STRING | 查询响应缓存 |
| `flow:api:resp:index:{apiId}` | SET | 该 API 全部 resp key 索引 |
| `flow:mq:dedup:{taskId}:{messageId}` | STRING | 消费去重 |
| `flow:login:captcha:{id}` | STRING | 登录图形验证码 |
| `flow:open:nonce:{appKey}:{nonce}` | STRING | 开放平台防重放 |
| `flow:open:rl:{platformId}:{windowSec}` | STRING | 开放平台 QPS 窗口（TTL ≈ 2s） |
| `flow:ingress:rl:{apiId}:{dim}:{windowSec}` | STRING | 入站限流；`dim` 为 appKey 或 `anon` |
| `flow:alert:dedup:{ruleId}:{fingerprint}` | STRING | 告警去重 |
| `flow:alert:throttle:rule:{ruleId}` | STRING | 规则节流 |
| `flow:alert:throttle:sysconfig` | STRING | SysConfig 兜底通道节流 |
| `flow:ui:enable` | STRING | 管理 UI 开关。`true`/`1` 开，其它关。未设置则走 yml |
| `flow:uiEnable` | STRING | **旧键**，仅当 `flow:ui:enable` 不存在时回读。请改 SET 新键 |

### 2.4 运行指标

| Key | 类型 | 说明 |
|-----|------|------|
| `flow:metrics:active` | SET | 当前活跃桶 |
| `flow:metrics:meta:dirty` | SET | 待落库 meta |
| `flow:metrics:m:{type}:{assetId}:{trigger}:{yyyyMMddHHmm}` | HASH | 分钟桶。`m` = minute（历史缩写，解析逻辑绑死，勿改） |
| `flow:metrics:meta:{type}:{assetId}` | HASH | 资产级 meta |

---

## 3. 不合规项与能否直接改

| 原 Key | 问题 | 结论 |
|--------|------|------|
| `flow::api::map` | 双冒号；且 **从未被写入**（写入是 `flow:api:map`）。`FlowRequestUtil.hasTag` 一直读空 | **已改**为读 `flow:api:map`，HASH field 与写入侧统一为 `{METHOD}-/{path}` |
| `flow:uiEnable` | camelCase，运维文档会手敲 | **已改**主键为 `flow:ui:enable`，**双读旧键**。生产若已 `SET flow:uiEnable`，升级后仍生效；新操作请用新键。要彻底关掉 UI 需 `DEL` 两个键 |
| `flow:metrics:m:…` | 缩写 `m` | **不要直接改**。高基数在线 key + `MetricsKeys.parseBucketKey`。改名需双写/刷盘窗口 |
| `flow:*:rl:…` | 缩写 `rl` | **可不改**。窗口 TTL 约 2s，改名几乎无残留，收益低 |
| `FlowRedisServeUtil.UI_ENABLED` | 死常量，从未读写 | **已删除** |

### 不能当系统 key 改的

- **Redis 步骤节点**：`RedisStepExecutor` 使用编排里填写的任意 key。
- **RBAC 权限码**：`flow:api:view` 等，与 Redis 无关。

---

## 4. 运维备忘

```bash
# 关闭管理 UI（推荐）
SET flow:ui:enable false

# 开启
SET flow:ui:enable true

# 恢复 yml 默认（新旧键都删）
DEL flow:ui:enable
DEL flow:uiEnable
```

变更后拦截器最多 5 秒生效（本地短缓存）。

排查已发布 API 路由缓存：

```bash
HLEN flow:api:map
HGET flow:api:map "GET-/your/path"
```

---

## 5. 新增 Key 检查清单

1. 是否以 `flow:` 开头、单冒号、小写段名？
2. 第二段是否是稳定域名（与表/模块对齐）？
3. 锁是否带 `:lock`，频道是否以 `:refresh:topic` 结尾？
4. 高基数字段是否在最后，并设了 TTL？
5. 常量是否集中在写入类（避免再出现读写两套 key）？
