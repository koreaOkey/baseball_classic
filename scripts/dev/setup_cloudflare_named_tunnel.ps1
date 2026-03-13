param(
    [Parameter(Mandatory = $true)]
    [string]$Hostname,
    [string]$TunnelName = "baseball-classic",
    [int]$LocalPort = 8080,
    [string]$ConfigPath = "infra/cloudflared/config.yml"
)

$ErrorActionPreference = "Stop"

function Resolve-CloudflaredPath {
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $candidates = @(
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links\cloudflared.exe",
        "$env:ProgramFiles\cloudflared\cloudflared.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    return $null
}

$cloudflaredExe = Resolve-CloudflaredPath
if (-not $cloudflaredExe) {
    throw "cloudflared command not found. Install first: winget install Cloudflare.cloudflared"
}

$homeCloudflaredDir = Join-Path $env:USERPROFILE ".cloudflared"
$certPath = Join-Path $homeCloudflaredDir "cert.pem"

if (-not (Test-Path $certPath)) {
    Write-Host "[cloudflare] no login cert found, starting login flow..."
    & $cloudflaredExe tunnel login
    if (-not (Test-Path $certPath)) {
        throw "cloudflared login did not produce cert.pem. Please retry login."
    }
}
else {
    Write-Host "[cloudflare] login cert already exists: $certPath"
}

$tunnelsJson = & $cloudflaredExe tunnel list --output json
$tunnels = @()
if (-not [string]::IsNullOrWhiteSpace($tunnelsJson)) {
    $tunnels = $tunnelsJson | ConvertFrom-Json
}

$existing = $tunnels | Where-Object { $_.name -eq $TunnelName } | Select-Object -First 1
if (-not $existing) {
    Write-Host "[cloudflare] creating tunnel: $TunnelName"
    & $cloudflaredExe tunnel create $TunnelName | Out-Host
    $tunnelsJson = & $cloudflaredExe tunnel list --output json
    $tunnels = $tunnelsJson | ConvertFrom-Json
    $existing = $tunnels | Where-Object { $_.name -eq $TunnelName } | Select-Object -First 1
    if (-not $existing) {
        throw "Tunnel '$TunnelName' was not found after creation."
    }
}
else {
    Write-Host "[cloudflare] tunnel already exists: $TunnelName"
}

$tunnelId = $existing.id
$credPath = Join-Path $homeCloudflaredDir "$tunnelId.json"
if (-not (Test-Path $credPath)) {
    throw "Tunnel credential file not found: $credPath"
}

Write-Host "[cloudflare] routing DNS: $Hostname -> tunnel $TunnelName"
$routeOutput = & $cloudflaredExe tunnel route dns $TunnelName $Hostname 2>&1
if ($LASTEXITCODE -ne 0) {
    $joined = ($routeOutput | Out-String).Trim()
    if ($joined -match "already exists") {
        Write-Host "[cloudflare] dns route already exists, continuing..."
    }
    else {
        throw "Failed to route DNS.`n$joined"
    }
}

$configDir = Split-Path -Parent $ConfigPath
if (-not (Test-Path $configDir)) {
    New-Item -ItemType Directory -Path $configDir -Force | Out-Null
}

$credPathNormalized = $credPath -replace "\\", "/"
$serviceUrl = "http://localhost:$LocalPort"

$configContent = @"
tunnel: $tunnelId
credentials-file: $credPathNormalized

ingress:
  - hostname: $Hostname
    service: $serviceUrl
  - service: http_status:404
"@

Set-Content -Path $ConfigPath -Value $configContent -Encoding UTF8

Write-Host ""
Write-Host "[done] Cloudflare named tunnel is configured."
Write-Host "  tunnel name : $TunnelName"
Write-Host "  tunnel id   : $tunnelId"
Write-Host "  hostname    : $Hostname"
Write-Host "  config file : $ConfigPath"
Write-Host ""
Write-Host "Run tunnel now:"
Write-Host "  ./scripts/dev/start_cloudflare_named_tunnel.ps1 -ConfigPath `"$ConfigPath`" -TunnelName `"$TunnelName`""
Write-Host ""
Write-Host "Set app backend URL to:"
Write-Host "  https://$Hostname"
