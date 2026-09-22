# ==============================================================================
# AutoFlow AI - Windows Local Environment Automation Script
# Usage:
#   .\scripts\dev-env.ps1 check      - Verify installed developer toolchain
#   .\scripts\dev-env.ps1 init       - Initialize .env configuration file
#   .\scripts\dev-env.ps1 up         - Spin up PostgreSQL, Redis, and MinIO in Docker
#   .\scripts\dev-env.ps1 down       - Tear down Docker services
#   .\scripts\dev-env.ps1 status     - Check status of Docker services and ports
# ==============================================================================

param (
    [Parameter(Position=0)]
    [ValidateSet("check", "init", "up", "down", "status")]
    [string]$Action = "status"
)

$ErrorActionPreference = "Stop"

function Check-Tools {
    Write-Host "==> Checking AutoFlow AI Local Development Toolchain..." -ForegroundColor Cyan
    
    $tools = @("java", "flutter", "dart", "docker", "git")
    foreach ($tool in $tools) {
        $found = Get-Command $tool -ErrorAction SilentlyContinue
        if ($found) {
            Write-Host "  [OK] $tool found at: $($found.Source)" -ForegroundColor Green
        } else {
            Write-Host "  [MISSING] $tool is not in system PATH!" -ForegroundColor Red
        }
    }

    # Verify Java version
    try {
        $javaVersionOutput = (java --version | Out-String)
        if ($javaVersionOutput -match '([0-9]+\.[0-9]+\.[0-9]+)') {
            Write-Host "  [JAVA] Runtime version: $($matches[1])" -ForegroundColor Green
        } else {
            Write-Host "  [JAVA] Runtime detected: $javaVersionOutput" -ForegroundColor Green
        }
    } catch {
        Write-Host "  [ERROR] Failed to query Java runtime: $_" -ForegroundColor Red
    }
}

function Init-Env {
    Write-Host "==> Initializing environment configuration..." -ForegroundColor Cyan
    if (Test-Path ".env") {
        Write-Host "  [SKIP] .env already exists." -ForegroundColor Yellow
    } else {
        Copy-Item ".env.example" ".env"
        Write-Host "  [CREATED] Copied .env.example to .env. Review values before production usage." -ForegroundColor Green
    }
}

function Start-DockerServices {
    Write-Host "==> Starting AutoFlow infrastructure (PostgreSQL 16, Redis 7, MinIO S3)..." -ForegroundColor Cyan
    Init-Env
    docker compose up -d
    Write-Host "==> Waiting for services to become healthy..." -ForegroundColor Cyan
    docker compose ps
}

function Stop-DockerServices {
    Write-Host "==> Stopping AutoFlow infrastructure..." -ForegroundColor Cyan
    docker compose down
}

function Show-Status {
    Write-Host "==> Current AutoFlow Infrastructure Status:" -ForegroundColor Cyan
    docker compose ps
}

switch ($Action) {
    "check"  { Check-Tools }
    "init"   { Init-Env }
    "up"     { Start-DockerServices }
    "down"   { Stop-DockerServices }
    "status" { Show-Status }
}
