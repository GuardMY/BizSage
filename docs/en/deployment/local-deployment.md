# Local Deployment

## Start Infrastructure

```powershell
infra\scripts\start-local.ps1
```

This starts:

- MySQL on `localhost:3306`
- Redis on `localhost:6379`
- Qdrant on `http://localhost:6333`

## Health Check

```powershell
infra\scripts\health-check.ps1
```

## Start Application Services

Run each service in a separate PowerShell terminal after the infrastructure is
started.

### API Service

```powershell
cd services\api
mvn spring-boot:run
```

The API listens on `http://localhost:8080` by default. Override it with the
`API_PORT` environment variable when needed.

### AI Worker

```powershell
cd services\ai-worker
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8100
```

The AI worker listens on `http://localhost:8100`.

### Collector

```powershell
cd services\collector
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8200
```

The collector listens on `http://localhost:8200`.

### Web App

```powershell
cd apps\web
npm install
npm run dev
```

The web app listens on `http://localhost:3000` and should call only the API
service directly.

## Default Accounts

The V1 baseline migration creates three development accounts. All use the same
development password hash intended for local testing only:

- `admin`
- `operator`
- `user`

Application-level authentication defines the actual accepted local password in
the API module.
