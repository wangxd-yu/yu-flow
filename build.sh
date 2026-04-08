#!/bin/bash
set -e

# ==========================================
# yu-flow 一键打包脚本 (Linux / Mac / Git Bash)
# 功能：前端 Umi 编译 -> 复制产物到后端 -> 后端 Maven 打包 JAR
# ==========================================

# 切换到脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="${SCRIPT_DIR}/flow-ui"
BACKEND_DIR="${SCRIPT_DIR}/flow-api"
RESOURCE_UI_DIR="${BACKEND_DIR}/src/main/resources/META-INF/resources/flow-ui"

echo -e "\n[1/4] ================== 开始构建前端 =================="
cd "${FRONTEND_DIR}"

echo "执行: pnpm install"
pnpm install

echo "执行: pnpm run build"
pnpm run build
echo "前端构建成功！"

echo -e "\n[2/4] ================== 同步前端产物到后端资源目录 =================="
echo "清理旧的前端资源: ${RESOURCE_UI_DIR}"
rm -rf "${RESOURCE_UI_DIR}"
mkdir -p "${RESOURCE_UI_DIR}"

echo "复制新的前端资源 (dist -> flow-ui)"
cp -R dist/* "${RESOURCE_UI_DIR}/"
echo "资源同步完成！"

echo -e "\n[3/4] ================== 开始构建后端 =================="
cd "${BACKEND_DIR}"

echo "执行: mvn clean package -DskipTests"
mvn clean package -DskipTests
echo "后端构建成功！"

echo -e "\n[4/4] ================== 打包完成 =================="
echo "一键打包成功！最终的 JAR 文件位于:"
echo "${BACKEND_DIR}/target/ 目录下"
echo ""
