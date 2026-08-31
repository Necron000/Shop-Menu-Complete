<#
.SYNOPSIS

.DESCRIPTION

.PARAMETER FrontendPort

.PARAMETER MetricsPort

.PARAMETER KeepTunnel

.EXAMPLE

.EXAMPLE
#>
[CmdletBinding()]
param(
    [int] $FrontendPort = 5173,
    [int] $MetricsPort  = 20241,
    [switch] $KeepTunnel
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$tunnel   = $null

function Write-Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Warn($text) { Write-Host "    $text" -ForegroundColor Yellow }

try {

    $cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue
    if (-not $cloudflared) {
        throw "cloudflared is not on PATH. Install it, or add its folder to PATH."
    }

    $vite = Get-NetTCPConnection -LocalPort $FrontendPort -State Listen -ErrorAction SilentlyContinue
    if (-not $vite) {
        Write-Warn "Nothing is listening on $FrontendPort - start the frontend first:"
        Write-Warn "  cd Frontend\shop-frontend; npm run dev"
        Write-Warn "Continuing anyway; the tunnel will 502 until Vite is up."
    }

    $localConfig = Join-Path $repoRoot 'config\application.yml'
    if (Test-Path $localConfig) {
        $pinned = Select-String -Path $localConfig -Pattern '^\s*(callback-url|frontend-result-url):'
        if ($pinned) {
            Write-Warn "config\application.yml pins these, which OVERRIDE this script:"
            foreach ($line in $pinned) { Write-Warn "  $($line.Line.Trim())" }
            Write-Warn "Comment them out to let the tunnel hostname take effect."
        }
    }

    Write-Step "Starting Cloudflare tunnel to http://localhost:$FrontendPort"

    $logFile = Join-Path $env:TEMP "cloudflared-$PID.log"
    $tunnel = Start-Process -FilePath $cloudflared.Source -PassThru -WindowStyle Hidden `
        -RedirectStandardError $logFile `
        -ArgumentList @(
            'tunnel',
            '--url', "http://localhost:$FrontendPort",
            '--metrics', "127.0.0.1:$MetricsPort"
        )

    $hostname = $null
    $deadline = (Get-Date).AddSeconds(45)

    while ((Get-Date) -lt $deadline) {
        if ($tunnel.HasExited) {
            $reason = if (Test-Path $logFile) { Get-Content $logFile -Raw } else { '(no output)' }
            throw "cloudflared exited with code $($tunnel.ExitCode):`n$reason"
        }

        try {
            $response = Invoke-RestMethod -Uri "http://127.0.0.1:$MetricsPort/quicktunnel" -TimeoutSec 2
            if ($response.hostname) {
                $hostname = $response.hostname
                break
            }
        } catch {
        }

        Start-Sleep -Milliseconds 500
    }

    if (-not $hostname) {
        throw "Timed out waiting for a tunnel hostname. cloudflared log: $logFile"
    }

    $publicBaseUrl = "https://$hostname"

    Write-Host ""
    Write-Host "  Public URL   $publicBaseUrl" -ForegroundColor Green
    Write-Host "  Callback     $publicBaseUrl/api/checkout/callback" -ForegroundColor Green
    Write-Host "  Share this URL with anyone testing a payment." -ForegroundColor Green
    Write-Host ""

    $env:PUBLIC_BASE_URL = $publicBaseUrl

    Write-Step "Starting backend with PUBLIC_BASE_URL=$publicBaseUrl"
    Write-Warn "Check the startup log says this hostname, not localhost."
    Write-Host ""

    Push-Location $repoRoot
    try {
        & .\mvnw.cmd spring-boot:run
    } finally {
        Pop-Location
    }
}
finally {
    if ($tunnel -and -not $tunnel.HasExited) {
        if ($KeepTunnel) {
            Write-Host ""
            Write-Warn "Tunnel left running (PID $($tunnel.Id)). Stop it with: Stop-Process -Id $($tunnel.Id)"
        } else {
            Write-Host ""
            Write-Step "Stopping tunnel"
            Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue
        }
    }
}
