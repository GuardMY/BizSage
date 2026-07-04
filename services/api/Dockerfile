# ── Build stage ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
# cache dependencies before copying source
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn package -DskipTests -q

# ── Runtime stage ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

ENV BIZSAGE_PROFILE=dev
ENV DB_URL=jdbc:mysql://mysql:3306/bizsage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
ENV DB_USERNAME=bizsage
ENV DB_PASSWORD=bizsage
ENV REDIS_HOST=redis
ENV REDIS_PORT=6379
ENV JWT_SECRET=change-me-v1-dev-secret-change-me
ENV DATA_ENCRYPTION_KEY=0123456789abcdef0123456789abcdef
ENV AI_WORKER_URL=http://ai-worker:8100

ENTRYPOINT ["java", "-jar", "app.jar"]
