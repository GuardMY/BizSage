$ErrorActionPreference = "Stop"

docker compose --project-name bizsage -f "$PSScriptRoot\..\docker-compose.yml" ps

try {
  Invoke-WebRequest -UseBasicParsing http://localhost:16333/ | Out-Null
  Write-Host "Qdrant OK"
} catch {
  Write-Host "Qdrant health check failed"
  exit 1
}
