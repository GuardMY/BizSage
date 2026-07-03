$ErrorActionPreference = "Stop"

docker compose -f "$PSScriptRoot\..\docker-compose.yml" ps

try {
  Invoke-WebRequest -UseBasicParsing http://localhost:6333/ | Out-Null
  Write-Host "Qdrant OK"
} catch {
  Write-Host "Qdrant health check failed"
  exit 1
}
