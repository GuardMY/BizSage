param(
  [Parameter(Mandatory = $true)]
  [string]$InputPath,
  [string]$ContainerName = "bizsage-mysql",
  [string]$Database = "bizsage",
  [string]$User = "bizsage",
  [string]$Password = "bizsage"
)

$ErrorActionPreference = "Stop"
$resolved = Resolve-Path -LiteralPath $InputPath
Get-Content -Raw -LiteralPath $resolved | docker exec -i $ContainerName mysql "-u$User" "-p$Password" $Database
Write-Host "MySQL restore completed from $resolved"
