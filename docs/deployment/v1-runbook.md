# BizSage V1 Runbook

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

Docker CLI was not available in the current implementation environment, so
compose startup must be validated on a machine with Docker installed.

## Start API

```powershell
cd services\api
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
