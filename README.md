<div align="center">

# 🚀 yu-flow

**轻量、高性能的动态 API 业务编排引擎**

*拖拽节点 → 编排逻辑 → 一键发布 API，告别重复的 CRUD 和 BFF 接口开发*

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://www.oracle.com/java/)
[![React](https://img.shields.io/badge/React-18-61DAFB.svg)](https://reactjs.org/)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/user/yu-flow/pulls)

[快速开始](#-快速开始) · [核心特性](#-核心特性) · [使用场景](#-使用场景) · [技术架构](#-技术架构) · [参与贡献](#-参与贡献) · [商业支持](#-商业支持)

</div>

---

<!-- 
  🎬 TODO: 录制一段 30 秒的操作 GIF，替换下面的占位图
  推荐工具：ScreenToGif (Windows) / Kap (Mac) 
  录制内容：新建 API → 拖入节点 → 连线 → 配置 → 发布 → 调用成功
-->

<div align="center">

> 📹 **演示动图即将上线** — 在本地运行后，你将看到如下体验：
>
> **新建 API** → **拖入数据库/HTTP/脚本节点** → **可视化连线** → **配置表达式** → **一键发布** → **即时调用**

</div>

---

## 🤔 它解决什么问题？

| 场景 | 没有 yu-flow | 有了 yu-flow |
|------|:----------:|:----------:|
| 前端要一个聚合接口 | 找后端排期 → 开发 BFF → 联调 → 发版 (2-5天) | 前端自己拖拽编排 → 发布 → 调用 (**30分钟**) |
| 查两个库的数据拼到一起 | 写 Service → 写 DAO → 手动拼接 JSON → 测试 | 拖两个 Database 节点 → 用脚本合并 → 搞定 |
| 对接第三方 API 做数据转换 | 写 HTTP Client → 解析响应 → 错误处理 → 发版 | HttpRequest 节点 + Evaluate 脚本节点 → 搞定 |
| 需要条件判断/循环处理 | 写 if/else → for 循环 → 各种边界处理 | If/Switch/For 节点直接拖拽编排 |

**一句话总结**：把重复的接口开发工作，从"写代码"变成"画流程图"。

---

## ✨ 核心特性

### 🎨 可视化流程编排
基于 AntV X6 画布引擎，拖拽式构建 API 业务逻辑，所见即所得。

### 📦 22 种开箱即用节点

```
流程控制    ➤  Start · End · If · Switch · For · Collect · Parallel · Return
数据处理    ➤  Evaluate · Template · SetVar · Record · SystemVar · SystemMethod
外部交互    ➤  HttpRequest · Database · ServiceCall · ApiServiceCall
请求响应    ➤  Request · Response
```

### 🔥 三引擎脚本系统

| 引擎 | 语法示例 | 适用场景 |
|------|---------|---------|
| **AviatorScript** (默认) | `price * 0.8` | 简单表达式、条件判断 |
| **JavaScript** (GraalJS) | `items.filter(x => x.price > 100)` | 复杂数据变换、ES6+ |
| **SpEL** | `#Math.max(#a, #b)` | Spring 生态集成 |

所有脚本引擎均配备**安全沙箱**：禁用文件 IO、进程创建、反射调用、类加载。

### 🗄️ 动态多数据源
运行时添加/切换数据源 (MySQL · PostgreSQL · 瀚高)，Database 节点一键执行 SQL。

### 🔀 并发 Scatter-Gather
For 节点自动将数组拆分为并发分支，Collect 节点屏障汇聚结果，原生支持高性能批处理。

### 🔒 三层安全沙箱

```
Layer 1  Aviator Feature 白名单 — 禁用 NewInstance / StaticMethods / Module
Layer 2  GraalJS IO 全禁用     — allowIO(false) / allowCreateProcess(false)  
Layer 3  SpEL 类型拦截         — SimpleEvaluationContext + T() 语法拦截
```

### 📐 可嵌入式架构
单个 JAR 包即可嵌入任何 Spring Boot 应用，让你的产品瞬间拥有 API 编排能力。

---

## 🚀 快速开始

### 环境要求

| 组件 | 版本 |
|------|------|
| Java | 8+ |
| Node.js | 18+ |
| MySQL | 5.7+ / 8.0+ |
| Redis | 6.0+ |
| pnpm | 10+ |

### 本地启动

```bash
# 1. 克隆仓库
git clone https://github.com/user/yu-flow.git
cd yu-flow

# 2. 启动后端
cd flow-api
# 配置 application.yml 中的 MySQL 和 Redis 连接信息
mvn spring-boot:run

# 3. 启动前端 (新开终端)
cd flow-ui
pnpm install
pnpm dev
```

打开浏览器访问 `http://localhost:8000`，开始编排你的第一个 API！

### 一键打包部署

```bash
# 前后端一体打包 (前端产物内嵌到 JAR 中)
bash build.sh

# 启动
java -jar flow-api/target/yu-flow-api-1.0-SNAPSHOT-exec.jar
```

---

## 🏗️ 技术架构

```
┌──────────────────────────────────────────────────────┐
│                    flow-ui 前端                       │
│   React 18 · Ant Design Pro · AntV X6 · Amis · CR   │
└───────────────────────┬──────────────────────────────┘
                        │ REST API
┌───────────────────────▼──────────────────────────────┐
│                    flow-api 后端                      │
│              Spring Boot 2.7 · JPA · Redis           │
│  ┌────────────────────────────────────────────────┐  │
│  │              FlowEngine 核心引擎               │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐       │  │
│  │  │ Aviator  │ │ GraalJS  │ │   SpEL   │       │  │
│  │  │ 编译缓存 │ │ AST缓存  │ │ 安全模式 │       │  │
│  │  └──────────┘ └──────────┘ └──────────┘       │  │
│  │  22种节点 · Scatter-Gather · 系统宏 · 参数校验 │  │
│  └────────────────────────────────────────────────┘  │
│  动态数据源(Druid) · JWT认证 · 目录管理 · 数据模型   │
└──────────────────────────────────────────────────────┘
```

---

## 💡 使用场景

### 场景 1：BFF 接口聚合
> 前端需要一个接口同时返回用户信息 + 订单列表 + 推荐商品

**传统方式**：后端写 3 个 Service 调用 → 手动聚合 → 发版  
**yu-flow**：拖入 3 个 HttpRequest 节点 → Evaluate 节点合并数据 → 发布

### 场景 2：数据报表 API
> 从两个不同数据库查数据，做汇总计算后返回

**传统方式**：写多数据源配置 → 写 SQL → 写 Service → 写 Controller  
**yu-flow**：配置两个数据源 → 拖入两个 Database 节点 → 脚本汇总 → 发布

### 场景 3：Webhook 处理
> 接收第三方回调 → 解析数据 → 条件判断 → 调用内部服务

**传统方式**：写 Controller → 写解析逻辑 → 写条件分支 → 写服务调用  
**yu-flow**：Request 节点接收 → If 节点判断 → ServiceCall 节点调用 → 完成

---

## 🗺️ 路线图

- [x] 核心编排引擎 (22 种节点)
- [x] 三语言脚本引擎 (Aviator / GraalJS / SpEL)
- [x] 安全沙箱 (三层防护)
- [x] 动态多数据源管理
- [x] 可视化画布编辑器
- [x] API 发布与管理
- [x] 系统宏 (变量宏 / 函数宏)
- [ ] 在线 Playground Demo
- [ ] 流程调试器 (断点 / 单步执行)
- [ ] API 版本管理
- [ ] 监控仪表盘
- [ ] Docker Compose 一键部署
- [ ] 完整文档站

---

## 🤝 参与贡献

我们欢迎任何形式的贡献！

- 🐛 **发现 Bug？** → [提交 Issue](https://github.com/user/yu-flow/issues)
- 💡 **有新想法？** → [发起 Discussion](https://github.com/user/yu-flow/discussions)
- 🔧 **想提交代码？** → Fork → 开发 → 提交 PR

## 💬 联系我们

- **微信交流群**：添加微信 `yu-flow-bot`，备注 "yu-flow" 拉群
- **邮箱**：657716219@qq.com

---

## 💼 商业支持

**yu-flow Pro** 商业版提供企业级特性：

- 🏢 多租户权限管理
- 🛡️ API 网关 (限流 / 熔断 / 鉴权)
- 📊 监控仪表盘 (调用量 / 延迟 / 错误率)
- 🔄 版本管理与灰度发布
- 🔌 企业集成连接器 (钉钉 / 企微 / 飞书)
- 📞 商业技术支持

联系我们获取试用：657716219@qq.com

---

## 📄 开源协议

[Apache License 2.0](LICENSE) — 可商用、可修改、可分发。

---

<div align="center">

**如果这个项目对你有帮助，请给一个 ⭐ Star 支持一下！**

</div>
