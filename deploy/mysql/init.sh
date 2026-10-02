#!/bin/bash
# MySQL 官方镜像首次初始化数据卷时执行：建表 + 系统种子。
# 跳过种子里的本机 JDBC 数据源行，应用启动后会按当前数据源自动补 [DEFAULT]。
set -euo pipefail

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
client=(mysql --protocol=socket -uroot --default-character-set=utf8mb4 "${MYSQL_DATABASE}")

"${client[@]}" < /yu-flow-sql/00_all_flow_tables.sql

awk '
  /^-- ===== flow_db_connection/ { skip = 1 }
  skip && /^-- ===== / && $0 !~ /^-- ===== flow_db_connection/ { skip = 0 }
  !skip { print }
' /yu-flow-sql/00_system_init.sql | "${client[@]}"

unset MYSQL_PWD
