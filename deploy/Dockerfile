# Yu Flow 一体化镜像：前端构建 → 后端打包（内嵌前端）→ JRE 运行
# 构建上下文是仓库根目录（白名单见根目录 .dockerignore）：
#   docker build -f deploy/Dockerfile -t yu-flow .
# 版本号：docker build --build-arg REVISION=2.0.0 ...（pom 的 ${revision}）

FROM node:22-slim AS ui
WORKDIR /src/flow-ui
ENV CI=true
# postinstall 会执行 max setup，需要完整源码，所以先拷源码再装依赖；下载走 pnpm store 缓存
COPY flow-ui/ ./
RUN npm install -g "$(node -p "require('./package.json').packageManager")" \
    && pnpm config set store-dir /pnpm-store
RUN --mount=type=cache,id=yu-flow-pnpm,target=/pnpm-store \
    pnpm install --frozen-lockfile \
    && pnpm build

FROM maven:3.9.15-eclipse-temurin-25 AS api
WORKDIR /src/flow-api
ARG REVISION=2.0-SNAPSHOT
COPY flow-api/pom.xml ./
COPY flow-api/src ./src
COPY --from=ui /src/flow-ui/dist ./src/main/resources/META-INF/resources/flow-ui
RUN --mount=type=cache,id=yu-flow-m2,target=/root/.m2 \
    mvn -B -q package -DskipTests -Djacoco.skip=true -Drevision="${REVISION}" \
    && cp target/*-exec.jar /app.jar

# 只导出可执行包，供发布流水线取出 jar。本阶段不能放在最后，否则默认镜像会变成空壳
FROM scratch AS artifact
COPY --from=api /app.jar /yu-flow-api-exec.jar

FROM eclipse-temurin:25-jre
# curl 供 HEALTHCHECK 使用。GraalJS 首次运行会往家目录解压内部资源，运行用户需要可写的家目录
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 flow \
    && useradd --system --uid 10001 --gid flow --create-home --home-dir /home/flow --shell /usr/sbin/nologin flow
WORKDIR /app
COPY --from=api /app.jar /app/app.jar
# 可执行包故意不打进 application.yml（给嵌入宿主让路）。独立运行时放到工作目录的 config/，
# 文件里只有环境变量占位，没有口令。需要覆盖时挂载同名文件即可。
COPY --from=api /src/flow-api/src/main/resources/application.yml /app/config/application.yml

# JAVA_OPTS 整体替换默认堆参数。JAVA_TOOL_OPTIONS 由 JVM 自行读取，可在不改入口的情况下追加参数
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
USER flow
EXPOSE 11281

# 端口与上下文路径随 SERVER_PORT / SERVER_SERVLET_CONTEXT_PATH 覆盖而变化
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD curl -fsS -o /dev/null "http://127.0.0.1:${SERVER_PORT:-11281}${SERVER_SERVLET_CONTEXT_PATH:-/flow}/actuator/health" || exit 1

# 容器参数原样透传给应用（如 --server.port=8080）。
# 外置配置可挂载到 /app/config/application.yml（Spring Boot 默认读取工作目录下的 config/）
ENTRYPOINT ["sh", "-c", "exec java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow $JAVA_OPTS -jar /app/app.jar \"$@\"", "yu-flow"]
