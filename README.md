# BizSage

BizSage is an industry intelligence and business diagnosis Agent platform.

V1 is an internal-validation MVP. It proves the core path from data ingestion to
governance, knowledge storage, RAG retrieval, diagnosis Agent output, and a Web
operator/user experience.

## V1 Modules

- `apps/web` - Next.js Web app for diagnosis chat and simple operations console.
- `apps/android` - Android placeholder. The Android app is deferred beyond V1.
- `services/api` - Spring Boot API for auth, RBAC, conversations, intelligence,
  knowledge metadata, and gateway-style API contracts.
- `services/ai-worker` - Python FastAPI worker for RAG and diagnosis generation.
- `services/collector` - Python collector for V1 data sources.
- `infra` - Local Docker Compose, deployment scripts, and database migrations.
- `docs` - Milestones, API, database, deployment, and acceptance documentation.

## Quick Start

1. Copy `.env.example` to `.env`.
2. Start infrastructure:

   ```powershell
   docker compose -f infra/docker-compose.yml up -d
   ```

3. Run the API:

   ```powershell
   cd services/api
   mvn spring-boot:run
   ```

4. Run the AI worker:

   ```powershell
   cd services/ai-worker
   python -m venv .venv
   .\.venv\Scripts\pip install -r requirements.txt
   .\.venv\Scripts\uvicorn app.main:app --reload --port 8100
   ```

5. Run the Web app:

   ```powershell
   cd apps/web
   npm install
   npm run dev
   ```

## V1 Defaults

- Android is not implemented in V1.
- Third-party API collection uses a mock/pluggable provider.
- LLM calls use an OpenAI-compatible API shape with mock fallback when no key is
  configured.
- High availability, dynamic source weighting, snapshots, PDF reports, and paid
  membership are V2/V3 work.
