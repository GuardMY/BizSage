param(
  [switch]$SkipInfrastructure,
  [switch]$SkipDependencyInstall,
  [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path "$PSScriptRoot\..\.."
$LogDir = Join-Path $Root "logs\local"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Test-CommandAvailable {
  param([string]$Name)
  return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-PortOpen {
  param(
    [string]$HostName,
    [int]$Port
  )
  try {
    $client = New-Object System.Net.Sockets.TcpClient
    $async = $client.BeginConnect($HostName, $Port, $null, $null)
    $connected = $async.AsyncWaitHandle.WaitOne(500)
    if ($connected) {
      $client.EndConnect($async)
    }
    $client.Close()
    return $connected
  } catch {
    return $false
  }
}

function Wait-Port {
  param(
    [string]$Name,
    [string]$HostName,
    [int]$Port,
    [int]$TimeoutSeconds = 60
  )
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-PortOpen -HostName $HostName -Port $Port) {
      Write-Host "$Name is reachable on $HostName`:$Port."
      return $true
    }
    Start-Sleep -Seconds 2
  }
  Write-Warning "$Name did not become reachable on $HostName`:$Port within $TimeoutSeconds seconds."
  return $false
}

function Start-LoggedProcess {
  param(
    [string]$Name,
    [string]$WorkingDirectory,
    [string]$Command,
    [int]$Port
  )

  if (Test-PortOpen -HostName "localhost" -Port $Port) {
    Write-Host "$Name appears to already be running on port $Port; skipping."
    return
  }

  $stdout = Join-Path $LogDir "$Name.out.log"
  $stderr = Join-Path $LogDir "$Name.err.log"
  $pidFile = Join-Path $LogDir "$Name.pid"
  $arguments = @(
    "-NoLogo",
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-Command",
    $Command
  )

  $process = Start-Process `
    -FilePath "powershell.exe" `
    -ArgumentList $arguments `
    -WorkingDirectory $WorkingDirectory `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -WindowStyle Hidden `
    -PassThru

  Set-Content -Path $pidFile -Value $process.Id
  Write-Host "Started $Name (PID $($process.Id)). Logs: $stdout / $stderr"
}

function Ensure-PythonService {
  param(
    [string]$Name,
    [string]$Directory
  )

  $venvPython = Join-Path $Directory ".venv\Scripts\python.exe"
  if (-not (Test-Path $venvPython)) {
    if ($SkipDependencyInstall) {
      throw "$Name virtual environment is missing. Re-run without -SkipDependencyInstall."
    }
    Write-Host "Creating Python virtual environment for $Name..."
    Push-Location $Directory
    try {
      python -m venv .venv
      & $venvPython -m pip install -r requirements.txt
    } finally {
      Pop-Location
    }
  }
}

function Ensure-WebDependencies {
  param([string]$Directory)
  if (-not (Test-Path (Join-Path $Directory "node_modules"))) {
    if ($SkipDependencyInstall) {
      throw "Web node_modules is missing. Re-run without -SkipDependencyInstall."
    }
    Write-Host "Installing Web dependencies..."
    Push-Location $Directory
    try {
      npm install
    } finally {
      Pop-Location
    }
  }
}

if (-not $SkipInfrastructure) {
  if (-not (Test-CommandAvailable "docker")) {
    throw "Docker CLI is not available. Install Docker Desktop, or run with -SkipInfrastructure after starting MySQL/Redis/Qdrant yourself."
  }
  Write-Host "Starting BizSage infrastructure..."
  & "$PSScriptRoot\start-local.ps1"
  Wait-Port -Name "MySQL" -HostName "localhost" -Port 3306 -TimeoutSeconds 90 | Out-Null
  Wait-Port -Name "Redis" -HostName "localhost" -Port 6379 -TimeoutSeconds 60 | Out-Null
  Wait-Port -Name "Qdrant" -HostName "localhost" -Port 6333 -TimeoutSeconds 60 | Out-Null
}

$ApiDir = Join-Path $Root "services\api"
$AiWorkerDir = Join-Path $Root "services\ai-worker"
$CollectorDir = Join-Path $Root "services\collector"
$WebDir = Join-Path $Root "apps\web"

Ensure-PythonService -Name "ai-worker" -Directory $AiWorkerDir
Ensure-PythonService -Name "collector" -Directory $CollectorDir
Ensure-WebDependencies -Directory $WebDir

Start-LoggedProcess `
  -Name "api" `
  -WorkingDirectory $ApiDir `
  -Command '$env:BIZSAGE_PROFILE="dev"; mvn spring-boot:run' `
  -Port 8080

Start-LoggedProcess `
  -Name "ai-worker" `
  -WorkingDirectory $AiWorkerDir `
  -Command '.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8100' `
  -Port 8100

Start-LoggedProcess `
  -Name "collector" `
  -WorkingDirectory $CollectorDir `
  -Command '.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8200' `
  -Port 8200

Start-LoggedProcess `
  -Name "web" `
  -WorkingDirectory $WebDir `
  -Command 'npm run dev' `
  -Port 3000

Write-Host ""
Write-Host "BizSage local stack startup requested."
Write-Host "API:        http://localhost:8080/api/health"
Write-Host "AI worker:  http://localhost:8100/health"
Write-Host "Collector:  http://localhost:8200/health"
Write-Host "Web:        http://localhost:3000"
Write-Host "Logs:       $LogDir"

if ($OpenBrowser) {
  Start-Process "http://localhost:3000"
}
