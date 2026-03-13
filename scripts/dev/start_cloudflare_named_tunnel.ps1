param(
    [string]$ConfigPath = "infra/cloudflared/config.yml",
    [string]$TunnelName = ""
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

    $wingetPackageRoot = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages"
    if (Test-Path $wingetPackageRoot) {
        $wingetExe = Get-ChildItem $wingetPackageRoot -Directory -ErrorAction SilentlyContinue `
            | Where-Object { $_.Name -like "Cloudflare.cloudflared*" } `
            | ForEach-Object {
                Join-Path $_.FullName "cloudflared.exe"
            } `
            | Where-Object { Test-Path $_ } `
            | Select-Object -First 1
        if ($wingetExe) {
            return $wingetExe
        }
    }

    return $null
}

$cloudflaredExe = Resolve-CloudflaredPath
if (-not $cloudflaredExe) {
    Write-Error "cloudflared command not found. Install first: winget install Cloudflare.cloudflared"
}

if (-not (Test-Path $ConfigPath)) {
    Write-Error "config file not found: $ConfigPath`nCopy infra/cloudflared/config.example.yml -> infra/cloudflared/config.yml and fill values first."
}

$resolvedConfigPath = (Resolve-Path $ConfigPath).Path

Write-Host "[cloudflare] starting named tunnel with config: $resolvedConfigPath"

if ([string]::IsNullOrWhiteSpace($TunnelName)) {
    & $cloudflaredExe tunnel --config $resolvedConfigPath run
}
else {
    & $cloudflaredExe tunnel --config $resolvedConfigPath run $TunnelName
}
