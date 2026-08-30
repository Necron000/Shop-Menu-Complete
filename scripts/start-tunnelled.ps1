<#
.SYNOPSIS
    Starts a Cloudflare quick tunnel, discovers its hostname, and boots the
    backend with PUBLIC_BASE_URL already pointing at it.

.DESCRIPTION
    A quick tunnel gets a new random *.trycloudflare.com hostname on every
    launch, and iyzico.callback-url is baked into each payment at startup. Doing
    that by hand means a remote buyer pays, gets redirected to a hostname that no
    longer exists (or to localhost), and the order sits at AWAITING_PAYMENT.

    This script removes the copy-paste: it reads the hostname back from
    cloudflared's own metrics API rather than scraping the banner it prints, and
    hands it to Spring as PUBLIC_BASE_URL, which application.yml already uses for
    both iyzico.callback-url and iyzico.frontend-result-url.

    The tunnel points at the Vite dev server, not the backend: Vite proxies /api
    to :8080, so one hostname serves the app, the callback and the result page.

.PARAMETER FrontendPort
    Port the Vite dev server is listening on. Default 5173.

.PARAMETER MetricsPort
    Local port for cloudflared's metrics API, where the hostname is read from.
    Default 20241. Change it only if something else already holds that port.

.PARAMETER KeepTunnel
    Leave the tunnel running after the backend exits, so restarting the backend
    keeps the same hostname. Without this the tunnel is stopped on the way out.

.EXAMPLE
    .\scripts\start-tunnelled.ps1

.EXAMPLE
    .\scripts\start-tunnelled.ps1 -KeepTunnel
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
    # --- preflight -------------------------------------------------------

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

    # config/application.yml overrides the classpath config outright, so an
    # uncommented callback-url there silently beats the PUBLIC_BASE_URL this
    # script exports. Catch that rather than let it look like the script failed.
    $localConfig = Join-Path $repoRoot 'config\application.yml'
    if (Test-Path $localConfig) {
        $pinned = Select-String -Path $localConfig -Pattern '^\s*(callback-url|frontend-result-url):'
        if ($pinned) {
            Write-Warn "config\application.yml pins these, which OVERRIDE this script:"
            foreach ($line in $pinned) { Write-Warn "  $($line.Line.Trim())" }
            Write-Warn "Comment them out to let the tunnel hostname take effect."
        }
    }

    # --- tunnel ----------------------------------------------------------

    Write-Step "Starting Cloudflare tunnel to http://localhost:$FrontendPort"

    $logFile = Join-Path $env:TEMP "cloudflared-$PID.log"
    $tunnel = Start-Process -FilePath $cloudflared.Source -PassThru -WindowStyle Hidden `
        -RedirectStandardError $logFile `
        -ArgumentList @(
            'tunnel',
            '--url', "http://localhost:$FrontendPort",
            '--metrics', "127.0.0.1:$MetricsPort"
        )

    # The metrics API serves the assigned hostname as JSON once the tunnel is
    # registered. Polling it beats parsing the ASCII box out of stderr.
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
            # metrics server not up yet - keep waiting
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

    # --- backend ---------------------------------------------------------

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
