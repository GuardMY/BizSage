# BizSage

BizSage 是一个行业智能与经营诊断 Agent 平台。

V1 是内部验证 MVP，用于验证从数据采集、数据治理、知识存储、RAG 检索、诊断 Agent 输出到 Web 运营/用户体验的核心链路。

## V1 模块

- `apps/web` - Next.js Web 应用，提供诊断对话和简易运营后台。
- `apps/android` - Android 占位目录。Android App 延后到 V1 之后。
- `services/api` - Spring Boot API，负责认证、RBAC、会话、情报、知识元数据和网关式 API 契约。
- `services/ai-worker` - Python FastAPI worker，负责 RAG 和诊断生成。
- `services/collector` - Python collector，负责 V1 数据源采集。
- `infra` - 本地 Docker Compose、部署脚本和数据库迁移。
- `docs` - 里程碑、API、数据库、部署和验收文档。

## 快速开始

1. 复制 `.env.example` 为 `.env`。
2. 启动基础设施：

   ```powershell
   docker compose -f infra/docker-compose.yml up -d
   ```

3. 启动 API：

   ```powershell
   cd services/api
   mvn spring-boot:run
   ```

4. 启动 AI worker：

   ```powershell
   cd services/ai-worker
   python -m venv .venv
   .\.venv\Scripts\pip install -r requirements.txt
   .\.venv\Scripts\uvicorn app.main:app --reload --port 8100
   ```

5. 启动 Web 应用：

   ```powershell
   cd apps/web
   npm install
   npm run dev
   ```

## V1 默认约束

- V1 不实现 Android。
- 第三方 API 采集使用 mock/可插拔 provider。
- LLM 调用使用 OpenAI-compatible API 形态；未配置密钥时使用 mock fallback。
- 高可用、动态信源权重、快照、PDF 报告和付费会员属于 V2/V3 范围。
