# ── Build stage ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# 国内镜像加速
COPY settings.xml /root/.m2/settings.xml

# 分层缓存依赖（BuildKit cache mount 跨构建复用 ~/.m2）
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:resolve -B -Dmaven.artifact.threads=10

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -DskipTests -B -Dmaven.artifact.threads=10

# ── Runtime stage ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# curl 用于健康检查
RUN apk add --no-cache curl

# 安全：非 root 运行
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build --chown=app:app /build/target/*.jar app.jar
USER app

EXPOSE 8080

# 健康检查：只确认端口可达即可（Spring Security 返回 401 也算存活）
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/ | grep -qE '200|301|401|403|404' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
