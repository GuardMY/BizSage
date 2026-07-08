<#
.SYNOPSIS
  BizSage V2 full-stack health check.
.DESCRIPTION
  Checks all 7 services: MySQL, Redis, Qdrant, API, AI Worker, Collector, Web.
  Exits with 0 if all pass, 1 if any check fails.
#>

$ErrorActionPreference = "Continue"
$allPassed = $true
$passed = 0
$failed = 0

function Write-Result($service, $ok, $detail = "") {
  if ($ok) {
    Write-Host "  [PASS] $service $detail" -ForegroundColor Green
    $script:passed++
  } else {
    Write-Host "  [FAIL] $service $detail" -ForegroundColor Red
    $script:failed++
    $script:allPassed = $false
  }
}

Write-Host ""
Write-Host "=== BizSage V2 Full-Stack Health Check ===" -ForegroundColor Cyan
Write-Host ""

# ── Infrastructure ──────────────────────────────────────────

# MySQL (port 13306)
try {
  docker exec bizsage-mysql mysqladmin ping -h localhost -u bizsage -pbizsage 2>$null | Out-Null
  Write-Result "MySQL" $true "(container: bizsage-mysql)"
} catch {
  Write-Result "MySQL" $false "($($_.Exception.Message))"
}

# Redis (port 16379)
try {
  $redisResult = docker exec bizsage-redis redis-cli PING 2>$null
  Write-Result "Redis" ($redisResult -eq "PONG") "(container: bizsage-redis)"
} catch {
  Write-Result "Redis" $false "($($_.Exception.Message))"
}

# Qdrant (port 16333)
try {
  $qdrantResult = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://localhost:16333/ 2>$null
  Write-Result "Qdrant" ($qdrantResult.StatusCode -eq 200) "(http://localhost:16333)"
} catch {
  Write-Result "Qdrant" $false "($($_.Exception.Message))"
}

# ── Application Services ────────────────────────────────────

# API (port 8080)
try {
  $apiResult = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://localhost:8080/api/health 2>$null
  Write-Result "API" ($apiResult.StatusCode -eq 200) "(http://localhost:8080)"
} catch {
  Write-Result "API" $false "($($_.Exception.Message))"
}

# AI Worker (port 8100)
try {
  $workerResult = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://localhost:8100/health 2>$null
  Write-Result "AI Worker" ($workerResult.StatusCode -eq 200) "(http://localhost:8100)"
} catch {
  Write-Result "AI Worker" $false "($($_.Exception.Message))"
}

# Collector (port 8200)
try {
  $collectorResult = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://localhost:8200/health 2>$null
  Write-Result "Collector" ($collectorResult.StatusCode -eq 200) "(http://localhost:8200)"
} catch {
  Write-Result "Collector" $false "($($_.Exception.Message))"
}

# Web (port 3000)
try {
  $webResult = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://localhost:3000 2>$null
  Write-Result "Web" ($webResult.StatusCode -eq 200) "(http://localhost:3000)"
} catch {
  Write-Result "Web" $false "($($_.Exception.Message))"
}

# ── Summary ─────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Results: $passed passed, $failed failed ===" -ForegroundColor $(if ($allPassed) { "Green" } else { "Red" })

if (-not $allPassed) {
  Write-Host ""
  Write-Host "Tip: Run 'docker compose -f infra/docker-compose.yml ps' to check container status."
  Write-Host "     Run 'infra/scripts/start-local.ps1' to start infrastructure services."
  exit 1
}

Write-Host "All services healthy." -ForegroundColor Green
exit 0
