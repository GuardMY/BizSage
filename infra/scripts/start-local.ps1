$ErrorActionPreference = "Stop"

docker compose --project-name bizsage -f "$PSScriptRoot\..\docker-compose.yml" up -d mysql redis qdrant

Write-Host "BizSage infrastructure is starting."
Write-Host "MySQL:  localhost:13306"
Write-Host "Redis:  localhost:16379"
Write-Host "Qdrant: http://localhost:16333"
