param(
  [string]$OutputPath = "backups/mysql/bizsage-$(Get-Date -Format yyyyMMdd-HHmmss).sql",
  [string]$ContainerName = "bizsage-mysql",
  [string]$Database = "bizsage",
  [string]$User = "bizsage",
  [string]$Password = "bizsage"
)

$ErrorActionPreference = "Stop"
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) {
  New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

docker exec $ContainerName mysqldump "-u$User" "-p$Password" $Database | Set-Content -Encoding UTF8 -Path $OutputPath
Write-Host "MySQL backup written to $OutputPath"
