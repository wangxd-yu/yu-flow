# 架构安全与权限配置

Yu Flow 在追求极致“配置化”的同时，也内置了企业级的高并发安全防护与精细化的页面路由控制体系。

## 前端 SPA 缓存管控拦截器

在单页应用 (SPA) 加载时，浏览器会瞬间发起几十个对静态资源（JS/CSS/图片）以及认证状态的并发请求。
如果安全拦截器每次都查询后端 Redis 进行鉴权，将造成灾难性的 I/O 风暴。

我们在 `FlowUiInterceptor` 内部采用了极客级别的 **5 秒本地短缓存 (Local Cache TTL)**：
- 同一波页面加载，只有第一次请求触发 Redis 网络验证，随后 5 秒内的所有并发校验直接在内存（纳秒级别）中放通。
- 采用 `volatile` 保证多线程可见性。完美兼顾了配置即时生效和系统的巅峰性能表现。

## UmiJS 路由黑白名单隔离

基于 Browser History 模式获取的真实页面路径 (`requestURI`)，Yu Flow 的控制拦截器实现了以下差异化策略：

1. **绝对放行的独立发布页（白名单）**：
   - 预览地址：`/flow-ui/page-manage/preview/**`
   - Amis 设计工作台：`/flow-ui/page-manage/designer/**`
   - _此类页面设计为“面向终端侧或设计人员”，其具体的鉴权应由页面自身的应用层业务凭证接管，底座网关予以直接透传。_

2. **核心微内核管理后台（强控区）**：
   - 如数据源 (`/flow/dataSource`)、控制器管理 (`/flow/controller`)、模型字典 (`/data-model/**`) 及 `/flow-ui/home` 首屏。
   - 所有引擎配置相关操作，必须开启了 UI 开关配置且鉴权通过才能触达。

## Redis 动态服务启停控制

在生产环境，为了避免研发擅自篡改配置，我们支持“UI 一键拔除”。
您可以直接在宿主机的 Redis 控制台输入：

```bash
# 关闭后端所有的可视化界面的入口
SET flow:uiEnable false

# 随时热开启
SET flow:uiEnable true
```
最迟不超过 5 秒，拦截器即会在分布式集群所有节点上同步生效并阻断对管理页面的 403 越权访问。无缓存值时，平台自动平滑降级至 `application.yml` 的 `yu.flow.enabled` / `enable-ui` 静态配置。

## 加密通道 `Security Group`
在安全部署中，确保 `application.yml` 保留着 `yu.flow.security.aes-secret-key` 并注入您的 `YU_FLOW_AES_SECRET`，由它来加解密各种敏感入参。
