# Local Deployment

## Start The Full Local Stack

```powershell
infra\scripts\start-all.ps1
```

This one-command launcher starts Docker infrastructure and the API, AI worker,
collector, and Web app as background processes. Logs and PID files are written
under `logs/local/`.

Useful options:

- `-SkipInfrastructure`: do not start Docker; use when MySQL, Redis, and Qdrant are already running.
- `-SkipDependencyInstall`: fail fast if Python virtual environments or `node_modules` are missing.
- `-OpenBrowser`: open `http://localhost:3000` after startup is requested.
## Start Infrastructure

```powershell
infra\scripts\start-local.ps1
```

This starts:

- MySQL on `localhost:13306`
- Redis on `localhost:16379`
- Qdrant on `http://localhost:16333`

## Health Check

```powershell
infra\scripts\health-check.ps1
```

## Start Application Services

Run each service in a separate PowerShell terminal after the infrastructure is
started.

### API Service

Initialize the MySQL schema before the first API start:

```powershell
mysql -h 127.0.0.1 -P 13306 -u bizsage -pbizsage bizsage < infra/mysql/init/001_v1_baseline.sql
```

After the baseline is present, Flyway validates and applies later incremental
migrations automatically when the API boots.

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

The V1 baseline migration creates four development accounts. All use the same
development password hash intended for local testing only:

- `admin`
- `operator`
- `user`
- `seed_paid`

Application-level authentication defines the actual accepted local password in
the API module.
