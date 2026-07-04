# ── Build stage ──────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /build

# API base URL 在构建时编译进客户端 JS
ARG NEXT_PUBLIC_API_BASE_URL=/api
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}

# npm 国内镜像加速
RUN npm config set registry https://registry.npmmirror.com

# 分层缓存依赖（BuildKit cache mount 跨构建复用 npm cache）
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY . .
# 确保 public 目录存在（Next.js 中为可选目录）
RUN mkdir -p public
RUN --mount=type=cache,target=/root/.npm \
    npm run build

# ── Runtime stage ────────────────────────────────────────
FROM node:20-alpine

# curl 用于健康检查
RUN apk add --no-cache curl

# 安全：非 root 运行
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# 只复制 standalone 产物，镜像体积缩小 60-80%
COPY --from=build --chown=app:app /build/.next/standalone ./
COPY --from=build --chown=app:app /build/.next/static ./.next/static
COPY --from=build --chown=app:app /build/public ./public

USER app

# Next.js standalone 会读取 HOSTNAME 决定绑定地址，必须显式设为 0.0.0.0
ENV HOSTNAME=0.0.0.0

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -sf http://localhost:3000/ || exit 1

CMD ["node", "server.js"]
