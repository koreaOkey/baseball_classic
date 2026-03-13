param(
    [int]$Port = 8080
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

$localUrl = "http://localhost:$Port"
Write-Host "[cloudflare] starting quick tunnel -> $localUrl"
Write-Host "[cloudflare] after startup, copy the https://*.trycloudflare.com URL and use it as mobile backendBaseUrl"

& $cloudflaredExe tunnel --url $localUrl
