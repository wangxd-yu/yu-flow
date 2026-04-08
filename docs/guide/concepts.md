# 核心概念

本文档介绍在 Yu Flow 环境下的关键系统层抽象：

1. **引擎底座 Context**：应用启动时的配置加载
2. **连接池 Datasource**：与远端 JDBC 数据库的通讯链路
3. **数据模型 TableModel**：抽象自物理表的元数据节点
4. **API Controller Proxy**：基于表达式或 SQL 构建的动态接口
5. **Amis Schema**：通过可视化构建的 JSON 视图描述树
