FROM python:3.12-alpine

# curl 用于健康检查
RUN apk add --no-cache curl

# 安全：非 root 运行
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# 分层缓存依赖（BuildKit cache mount 跨构建复用 pip cache）
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple/ \
    --trusted-host mirrors.aliyun.com \
    -r requirements.txt

COPY app/ app/
RUN find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null; exit 0

# 切换非 root 用户
USER app

EXPOSE 8100

# 健康检查：FastAPI 有 /health 端点
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -sf http://localhost:8100/health || exit 1

# 生产模式不带 --reload（dev 时由 docker-compose override）
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8100"]
