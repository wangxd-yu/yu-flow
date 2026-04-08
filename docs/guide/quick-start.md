---
title: 快速开始
outline: deep
---

# 🚀 快速开始

**只需引入一个依赖，让你的 Spring Boot 应用瞬间拥有可视化业务编排能力。** 全程不需要修改一行现有代码，不需要额外搭建任何服务——引入 Starter、执行建表脚本、配置两行 YAML，启动后打开浏览器即可使用。

::: info ⏱️ 预计耗时
从零到跑通第一个动态接口，全程约 **5 分钟**。
:::

---

## 📋 准备工作

请确认你的开发环境满足以下条件：

| 组件 | 版本要求 | 说明 |
| --- | --- | --- |
| **JDK** | 1.8+ 或 17+ | 推荐 17 |
| **Spring Boot** | 2.x 或 3.x | 已验证 2.7.x |
| **MySQL** | 5.7+ / 8.0+ | 引擎元数据存储 |
| **Redis** | 任意版本 | *可选*，用于接口缓存加速 |
| **Maven / Gradle** | 3.6+ / 7.0+ | 构建工具 |

::: tip 💡 零前端环境
你**不需要安装 Node.js**。Yu Flow 的管理界面已经预编译打包在 JAR 包内，随 Spring Boot 一起启动即可访问。
:::

---

## 🛠️ Step 1：引入依赖

在你**现有的** Spring Boot 项目中，添加 `yu-flow-api` 依赖：

::: code-group

```xml [Maven (pom.xml)]
<dependency>
    <groupId>org.yu</groupId>
    <artifactId>yu-flow-api</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

```groovy [Gradle (build.gradle)]
implementation 'org.yu:yu-flow-api:1.0-SNAPSHOT'
```

:::

就是这一步。没有注解要加，没有接口要实现——`FlowAutoConfiguration` 会通过 Spring Boot 的自动装配机制自动激活所有能力。

---

## 🗄️ Step 2：初始化数据库

Yu Flow 需要几张元数据表来存储你创建的数据模型、API 配置、页面配置和系统宏定义。

**请在你的 MySQL 数据库中，按顺序执行以下 SQL 脚本：**

```
📂 yu-flow/flow-api/sql/
├── flow_directory.sql           ← 全局目录树
├── flow_model.sql               ← 数据模型管理
├── flow_page.sql                ← 页面配置管理
├── flow_sys_macro.sql           ← 系统宏定义表
├── flow_sys_macro_init.sql      ← 系统宏初始数据
├── alter_flow_controller.sql    ← API 控制器增强
├── alter_flow_datasource.sql    ← 数据源增强
└── alter_flow_datasource_health.sql ← 数据源健康检查
```

::: warning 执行顺序提示
请**先执行 `flow_directory.sql`**（因为其他表的外键依赖目录表），然后再执行其余脚本。`flow_sys_macro_init.sql` 必须在 `flow_sys_macro.sql` 之后执行。
:::

---

## ⚙️ Step 3：添加配置

在你的 `application.yml`（或 `application.properties`）中添加以下配置：

```yaml
yu:
  flow:
    enabled: true        # 总开关：启用 Yu Flow 引擎
    enable-ui: true      # 启用内置管理界面
    username: admin      # 管理后台登录用户名
    password: flow@699   # 管理后台登录密码
```

::: tip 🔌 随时可插拔
`yu.flow.enabled` 是整个引擎的**总开关**。设为 `false` 即可完全禁用 Yu Flow 的自动装配，不会对你的现有代码产生任何影响——这就是嵌入式设计的核心理念。
:::

::: details 完整配置参考（高级用户）

```yaml
yu:
  flow:
    enabled: true
    enable-ui: true
    username: admin
    password: ${YU_FLOW_ADMIN_PASSWORD:flow@699}
    engine:
      expression-engine: simple   # simple (推荐) 或 spel
      strict-mode: true           # 执行前校验节点配置完整性
      enable-trace: false         # 开启节点执行追踪（调试用）
    security:
      aes-secret-key: ${YU_FLOW_AES_SECRET:your-16byte-key}
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `engine.expression-engine` | `simple` | 表达式引擎：`simple` 安全高效，`spel` 功能更强 |
| `engine.strict-mode` | `true` | 是否在执行前验证所有节点配置 |
| `engine.enable-trace` | `false` | 是否记录每个节点的执行详情 |
| `security.aes-secret-key` | `flow-secure-keys` | 数据源密码 AES 加密密钥（生产环境务必更换） |

:::

---

## ✨ Step 4：启动！

像往常一样启动你的 Spring Boot 应用：

```bash
mvn spring-boot:run
```

然后打开浏览器，访问：

```
http://localhost:8080/flow-ui/index.html
```

> 如果你的项目配置了 `server.servlet.context-path`（例如 `/flow`），请拼接上该路径：`http://localhost:8080/flow/flow-ui/index.html`

使用你在 Step 3 中配置的用户名和密码登录（默认 `admin` / `flow@699`）。

::: tip 🎉 恭喜！
如果你看到了 Yu Flow 的管理后台界面——数据源、数据模型、API 配置、页面管理一应俱全——说明引擎已经成功嵌入到你的项目中了！

**全程零代码改动。你的原有 Controller、Service、Repository 完好无损。**
:::

---

## 🎯 终极体验：创建你的第一个动态接口

打开界面 ≠ 成功。**让我们用 60 秒跑通一个真实的动态 API**，以亲身感受"配置即接口"的魔力。

### 第 1 步：新建 API

在左侧导航栏点击 **「API 配置」**，然后点击右上角的 **「新建」** 按钮。

- **API 路径**：填入 `/hello-flow`
- **请求方法**：选择 `GET`
- **名称**：填入 `我的第一个接口`

### 第 2 步：编排流程

进入 Flow 编排画布后，你会看到一个 **Request 节点** 已经自动创建好了（它是每条流程的入口）。

1. 从左侧组件库拖入一个 **Response** 节点
2. 从 Request 的 `out` 端口 **拉一条线** 到 Response 的 `in` 端口
3. 点击 Response 节点，在右侧属性面板中配置：
   - **Status Code**：`200`
   - **Body**：输入以下内容

```json
{ "message": "Hello Yu Flow!", "timestamp": "2026" }
```

### 第 3 步：保存并发布

点击顶部工具栏的 **「保存」** 按钮。

### 第 4 步：见证奇迹

打开终端，执行：

```bash
curl http://localhost:8080/flow-api/dynamic/hello-flow
```

你将立刻看到响应：

```json
{ "message": "Hello Yu Flow!", "timestamp": "2026" }
```

::: tip 🤯 感受到了吗？
你刚才做的事情：

✅ 创建了一个全新的 REST API<br>
✅ 全程 **没有写一行 Java 代码**<br>
✅ 全程 **没有重启服务器**<br>
✅ 全程 **没有修改任何配置文件**

这就是 Yu Flow 的核心价值——**配置即接口，发布即生效**。
:::

---

## 📚 下一步

恭喜你已经成功跑通了第一个动态接口！接下来，你可以：

| 主题 | 说明 | 链接 |
| --- | --- | --- |
| **核心概念总览** | 理解画布、连线、上下文数据流转机制 | [编排画布与核心概念](/api/) |
| **节点参考手册** | 逐个了解 Request、Database、If 等每种节点的详细用法 | [节点手册](/api/nodes/request) |
| **动态数据源** | 连接你的业务数据库，开始操作真实数据 | [数据源管理](/manual/data-source) |
| **Flow DSL 规范** | 深入理解底层的 JSON 协议格式 | [DSL 协议](/advanced/flow-dsl) |
