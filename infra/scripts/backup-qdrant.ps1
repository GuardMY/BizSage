param(
  [string]$OutputPath = "backups/qdrant/qdrant-storage-$(Get-Date -Format yyyyMMdd-HHmmss).tar",
  [string]$ContainerName = "bizsage-qdrant"
)

$ErrorActionPreference = "Stop"
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) {
  New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

docker run --rm --volumes-from $ContainerName -v "${PWD}:/backup" alpine `
  sh -c "tar cf /backup/$OutputPath /qdrant/storage"
Write-Host "Qdrant backup written to $OutputPath"
