# BizSage V1/V2 Runbook

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+
- Python 3.12+
- Docker Desktop or compatible Docker CLI

## Start Infrastructure

```powershell
infra\scripts\start-local.ps1
```

Docker CLI was not available in the current implementation environment, so compose startup must be validated on a machine with Docker installed.

## Start API

```powershell
cd services\api
mvn spring-boot:run
```

For V2 gray-release profile:

```powershell
cd services\api
$env:BIZSAGE_PROFILE="prod-gray"
mvn spring-boot:run
```

## Start Collector

```powershell
cd services\collector
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8200
```

## Start AI Worker

```powershell
cd services\ai-worker
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8100
```

## Start Web

```powershell
cd apps\web
npm install
npm run dev
```

Open `http://localhost:3000`.

## V2 Backup And Restore Drill

Run on a Docker-capable staging machine:

```powershell
infra\scripts\backup-mysql.ps1
infra\scripts\restore-mysql.ps1 -InputPath <backup.sql>
infra\scripts\backup-qdrant.ps1
infra\scripts\restore-qdrant.ps1 -InputPath <backup.tar>
```

Record RTO, RPO, operator, timestamp, and any failure in `docs/en/milestones/v2-verification-results.md`.
