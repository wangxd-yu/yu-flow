# 系统深度集成

介绍如果把 `flow-core` 作为一个普通的 Spring Boot Starter 引入现有的单体/微服务应用。

业务 API **默认零改造**：引入 Starter 后，未纳管路由仍由宿主处理。若需对宿主原生接口做日志/计量/限流，或用动态引擎替换同名 path，见 [宿主 API 托管](./host-api-governance.md)。
