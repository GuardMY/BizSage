$ErrorActionPreference = "Stop"

docker compose -f "$PSScriptRoot\..\docker-compose.yml" up -d

Write-Host "BizSage infrastructure is starting."
Write-Host "MySQL:  localhost:3306"
Write-Host "Redis:  localhost:6379"
Write-Host "Qdrant: http://localhost:6333"
