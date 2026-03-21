param(
    [string]$PythonPath = ".venv\Scripts\python.exe",
    [string]$BackendHost = "0.0.0.0",
    [int]$BackendPort = 8080,
    [string]$BackendBaseUrl = "http://localhost:8080",
    [string]$League = "kbo",
    [string]$SourceBaseUrl = "https://api-gw.sports.naver.com",
    [int]$ScheduleImportDays = 1,
    [int]$ScheduleRefreshIntervalSec = 300,
    [int]$CrawlerIntervalSec = 15,
    [double]$CrawlerBackendTimeoutSec = 8.0,
    [int]$CrawlerBackendRetries = 2,
    [int]$CheckIntervalSec = 10,
    [int]$HealthTimeoutSec = 4,
    [switch]$EnablePreviewLineupPrecheck,
    [switch]$DisableTeamRecordSync,
    [switch]$NoDispatcher,
    [switch]$RunOnce,
    [string]$BackendApiKey
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
$backendDir = Join-Path $repoRoot "backend\api"
$dispatcherScript = Join-Path $repoRoot "crawler\live_baseball_dispatcher.py"
$envPath = Join-Path $backendDir ".env"
$logDir = Join-Path $repoRoot "log"

New-Item -ItemType Directory -Path $logDir -Force | Out-Null

$watchdogLogPath = Join-Path $logDir ("watchdog_{0}.log" -f (Get-Date -Format "yyyyMMdd"))

function Write-WatchdogLog {
    param([string]$Message)
    $line = "{0} [watchdog] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -Path $watchdogLogPath -Value $line -Encoding UTF8
}

function Resolve-PythonPath {
    param([string]$InputPath)
    if ([System.IO.Path]::IsPathRooted($InputPath)) {
        return $InputPath
    }
    return Join-Path $repoRoot $InputPath
}

function Get-ApiKeyFromEnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        throw "API key not found: missing file '$Path'"
    }
    $line = Get-Content $Path | Where-Object { $_ -match '^BASEHAPTIC_CRAWLER_API_KEY=' } | Select-Object -First 1
    if (-not $line) {
        throw "BASEHAPTIC_CRAWLER_API_KEY is not set in '$Path'"
    }
    return ($line -split "=", 2)[1].Trim()
}

function Test-BackendHealthy {
    param([string]$Url, [int]$TimeoutSec)
    try {
        $resp = Invoke-RestMethod -Uri "$Url/live" -TimeoutSec $TimeoutSec
        return ($resp.status -eq "ok")
    }
    catch {
        return $false
    }
}

function Get-ListeningPids {
    param([int]$Port)
    $rows = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $rows) {
        return ,@()
    }
    return ,@($rows | Select-Object -ExpandProperty OwningProcess -Unique)
}

function Get-ProcessSafe {
    param([int]$ProcessId)
    return Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
}

function Test-ProcessAlive {
    param([System.Diagnostics.Process]$Process)
    if ($null -eq $Process) {
        return $false
    }
    try {
        return -not $Process.HasExited
    }
    catch {
        return $false
    }
}

function Find-ExistingDispatcherProcess {
    $rows = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^python(\.exe)?$' -and
        $_.CommandLine -match 'live_(baseball|wbc)_dispatcher\.py'
    }
    foreach ($row in $rows) {
        $proc = Get-ProcessSafe -ProcessId $row.ProcessId
        if ($null -ne $proc) {
            return $proc
        }
    }
    return $null
}

function Stop-BackendProcesses {
    param([int]$Port)

    $killed = @()
    $listenerPids = Get-ListeningPids -Port $Port

    $uvicornRows = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^python(\.exe)?$' -and
        $_.CommandLine -match '-m uvicorn app\.main:app' -and
        $_.CommandLine -match "--port\s+$Port(\s|$)"
    }

    $candidatePids = @()
    if ($listenerPids) {
        $candidatePids += $listenerPids
    }
    if ($uvicornRows) {
        $candidatePids += @($uvicornRows | Select-Object -ExpandProperty ProcessId)
    }
    $candidatePids = @($candidatePids | Sort-Object -Unique)

    foreach ($pidValue in $candidatePids) {
        if ($pidValue -eq $PID) {
            continue
        }
        $proc = Get-ProcessSafe -ProcessId $pidValue
        if ($null -eq $proc) {
            continue
        }
        try {
            Stop-Process -Id $pidValue -Force -ErrorAction Stop
            $killed += $pidValue
        }
        catch {
            Write-WatchdogLog ("failed_to_stop pid={0} reason={1}" -f $pidValue, $_.Exception.Message)
        }
    }

    if ($killed.Count -gt 0) {
        Write-WatchdogLog ("stopped_backend_pids={0}" -f (($killed -join ",")))
    }
}

function Start-BackendProcess {
    param(
        [string]$ResolvedPythonPath,
        [string]$WorkingDirectory,
        [string]$Host,
        [int]$Port
    )

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $outLog = Join-Path $logDir ("backend_watchdog_{0}.out.log" -f $stamp)
    $errLog = Join-Path $logDir ("backend_watchdog_{0}.err.log" -f $stamp)

    $args = @("-m", "uvicorn", "app.main:app", "--host", $Host, "--port", "$Port")
    $proc = Start-Process `
        -FilePath $ResolvedPythonPath `
        -ArgumentList $args `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru

    Write-WatchdogLog ("backend_started pid={0} out={1} err={2}" -f $proc.Id, $outLog, $errLog)
    return $proc
}

function Start-DispatcherProcess {
    param(
        [string]$ResolvedPythonPath,
        [string]$WorkingDirectory,
        [string]$ApiKey
    )

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $outLog = Join-Path $logDir ("dispatcher_watchdog_{0}.out.log" -f $stamp)
    $errLog = Join-Path $logDir ("dispatcher_watchdog_{0}.err.log" -f $stamp)

    $args = @(
        "crawler/live_baseball_dispatcher.py",
        "--backend-base-url", $BackendBaseUrl,
        "--backend-api-key", $ApiKey,
        "--league", $League,
        "--source-base-url", $SourceBaseUrl,
        "--schedule-import-days", "$ScheduleImportDays",
        "--schedule-refresh-interval-sec", "$ScheduleRefreshIntervalSec",
        "--crawler-interval-sec", "$CrawlerIntervalSec",
        "--crawler-backend-timeout-sec", "$CrawlerBackendTimeoutSec",
        "--crawler-backend-retries", "$CrawlerBackendRetries"
    )
    if ($EnablePreviewLineupPrecheck) {
        $args += "--enable-preview-lineup-precheck"
    }
    if ($DisableTeamRecordSync) {
        $args += "--disable-team-record-sync"
    }

    $proc = Start-Process `
        -FilePath $ResolvedPythonPath `
        -ArgumentList $args `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru

    Write-WatchdogLog ("dispatcher_started pid={0} out={1} err={2}" -f $proc.Id, $outLog, $errLog)
    return $proc
}

function Wait-ForBackendHealth {
    param([string]$Url, [int]$TimeoutSec)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-BackendHealthy -Url $Url -TimeoutSec $HealthTimeoutSec) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

$resolvedPythonPath = Resolve-PythonPath -InputPath $PythonPath
if (-not (Test-Path $resolvedPythonPath)) {
    throw "python executable not found: '$resolvedPythonPath'"
}

$resolvedApiKey = $BackendApiKey
if (-not $NoDispatcher) {
    if (-not $resolvedApiKey) {
        $resolvedApiKey = Get-ApiKeyFromEnvFile -Path $envPath
    }
}

Write-WatchdogLog ("start python={0} backend={1}:{2} dispatcher={3}" -f $resolvedPythonPath, $BackendHost, $BackendPort, (-not $NoDispatcher))

$backendProcess = $null
$dispatcherProcess = $null

if (Test-BackendHealthy -Url $BackendBaseUrl -TimeoutSec $HealthTimeoutSec) {
    $listenerPids = Get-ListeningPids -Port $BackendPort
    if ($listenerPids.Count -gt 0) {
        $backendProcess = Get-ProcessSafe -ProcessId $listenerPids[0]
        if ($null -ne $backendProcess) {
            Write-WatchdogLog ("backend_adopted pid={0}" -f $backendProcess.Id)
        }
    }
}
else {
    Stop-BackendProcesses -Port $BackendPort
    $backendProcess = Start-BackendProcess -ResolvedPythonPath $resolvedPythonPath -WorkingDirectory $backendDir -Host $BackendHost -Port $BackendPort
    $ok = Wait-ForBackendHealth -Url $BackendBaseUrl -TimeoutSec 20
    if (-not $ok) {
        Write-WatchdogLog "backend_health_timeout_after_start"
    }
}

if (-not $NoDispatcher) {
    $existingDispatcher = Find-ExistingDispatcherProcess
    if ($null -ne $existingDispatcher) {
        $dispatcherProcess = $existingDispatcher
        Write-WatchdogLog ("dispatcher_adopted pid={0}" -f $dispatcherProcess.Id)
    }
    else {
        $dispatcherProcess = Start-DispatcherProcess -ResolvedPythonPath $resolvedPythonPath -WorkingDirectory $repoRoot -ApiKey $resolvedApiKey
    }
}

do {
    $healthy = Test-BackendHealthy -Url $BackendBaseUrl -TimeoutSec $HealthTimeoutSec
    if (-not $healthy) {
        Write-WatchdogLog "backend_unhealthy_restart"
        Stop-BackendProcesses -Port $BackendPort
        $backendProcess = Start-BackendProcess -ResolvedPythonPath $resolvedPythonPath -WorkingDirectory $backendDir -Host $BackendHost -Port $BackendPort
        $ok = Wait-ForBackendHealth -Url $BackendBaseUrl -TimeoutSec 20
        if (-not $ok) {
            Write-WatchdogLog "backend_health_timeout_after_restart"
        }
    }
    elseif (-not (Test-ProcessAlive -Process $backendProcess)) {
        $listenerPids = Get-ListeningPids -Port $BackendPort
        if ($listenerPids.Count -gt 0) {
            $backendProcess = Get-ProcessSafe -ProcessId $listenerPids[0]
            if ($null -ne $backendProcess) {
                Write-WatchdogLog ("backend_recovered_or_adopted pid={0}" -f $backendProcess.Id)
            }
        }
    }

    if (-not $NoDispatcher) {
        if (-not (Test-ProcessAlive -Process $dispatcherProcess)) {
            Write-WatchdogLog "dispatcher_not_running_restart"
            $dispatcherProcess = Start-DispatcherProcess -ResolvedPythonPath $resolvedPythonPath -WorkingDirectory $repoRoot -ApiKey $resolvedApiKey
        }
    }

    if ($RunOnce) {
        break
    }
    Start-Sleep -Seconds $CheckIntervalSec
}
while ($true)

Write-WatchdogLog "exit"
