param(
  [Parameter(Mandatory = $true)]
  [string]$InputPath,
  [string]$ContainerName = "bizsage-qdrant"
)

$ErrorActionPreference = "Stop"
$resolved = Resolve-Path -LiteralPath $InputPath
docker run --rm --volumes-from $ContainerName -v "${PWD}:/backup" alpine `
  sh -c "tar xf /backup/$InputPath -C /"
Write-Host "Qdrant restore completed from $resolved"
