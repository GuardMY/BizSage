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

## Default Accounts

The V1 baseline migration creates three development accounts. All use the same
development password hash intended for local testing only:

- `admin`
- `operator`
- `user`

Application-level authentication defines the actual accepted local password in
the API module.
