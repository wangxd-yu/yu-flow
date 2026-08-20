#!/bin/bash
set -e

# 无论成功失败，执行完毕后暂停等待，方便查看输出
trap 'read -p "按任意键退出..."' EXIT

# ==========================================
# yu-flow 一键打包脚本 (Linux / Mac / Git Bash)
# 功能：前端 Umi 编译 -> 复制产物到后端 -> mvn install 本机
#       -> 询问是否 deploy 到 Nexus 私服（默认不推）
# 非交互强制推送：DEPLOY=1 ./build.sh
# 非交互跳过推送：SKIP_DEPLOY=1 ./build.sh（或不设 DEPLOY）
# ==========================================

# 切换到脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="${SCRIPT_DIR}/flow-ui"
BACKEND_DIR="${SCRIPT_DIR}/flow-api"
RESOURCE_UI_DIR="${BACKEND_DIR}/src/main/resources/META-INF/resources/flow-ui"
# build.sh 在 yu-flow/yu-flow/，deploy.sh 在仓库根 脚本/
DEPLOY_SCRIPT="$(cd "${SCRIPT_DIR}/.." && pwd)/脚本/deploy.sh"

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

echo -e "\n[3/4] ================== 安装到本机 Maven 仓库 =================="
cd "${BACKEND_DIR}"

echo "执行: mvn clean install -DskipTests -Djacoco.skip=true"
mvn clean install -DskipTests -Djacoco.skip=true
echo "本机 install 成功！坐标: org.yu:yu-flow-api-java17-springboot3:1.0-SNAPSHOT"

echo -e "\n[4/4] ================== 推送到 Nexus 私服（可选） =================="
SHOULD_DEPLOY=0
if [ "${SKIP_DEPLOY}" = "1" ]; then
  echo "已设置 SKIP_DEPLOY=1，跳过私服 deploy。"
elif [ "${DEPLOY}" = "1" ]; then
  SHOULD_DEPLOY=1
  echo "已设置 DEPLOY=1，将推送到私服。"
elif [ -t 0 ]; then
  echo ""
  read -r -p "是否推送到 Nexus 私服？[y/N] " DEPLOY_ANSWER
  case "${DEPLOY_ANSWER}" in
    y|Y|yes|YES) SHOULD_DEPLOY=1 ;;
    *) echo "未选择推送，仅完成本机 install。" ;;
  esac
else
  echo "非交互环境且未设 DEPLOY=1，默认不推私服。"
fi

if [ "${SHOULD_DEPLOY}" = "1" ]; then
  if [ ! -f "${DEPLOY_SCRIPT}" ]; then
    echo "[ERROR] 未找到 deploy 脚本: ${DEPLOY_SCRIPT}"
    exit 1
  fi
  echo "执行: ${DEPLOY_SCRIPT}"
  # 避免 deploy.sh 末尾二次 read 卡住
  NONINTERACTIVE=1 bash "${DEPLOY_SCRIPT}"
  echo "私服 deploy 成功！snapshots: http://192.168.102.20:28080/repository/maven-snapshots/"
fi

echo ""
echo "========================================"
echo "一键构建完成"
echo "  - 本机 JAR: ${BACKEND_DIR}/target/"
echo "  - 本机 m2:  org.yu:yu-flow-api-java17-springboot3:1.0-SNAPSHOT"
if [ "${SHOULD_DEPLOY}" = "1" ]; then
  echo "  - 私服:    已推送 maven-snapshots"
else
  echo "  - 私服:    未推送"
fi
echo "用法提示:"
echo "  - 默认只装本机 m2；脚本结束前问是否推私服，直接回车=不推"
echo "  - 非交互要推私服：DEPLOY=1 ./build.sh"
echo "  - cloud-lowcode 若仍拿旧 SNAPSHOT：mvn -U 或清本地该坐标后再编"
echo "========================================"
echo ""
