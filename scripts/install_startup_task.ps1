[CmdletBinding()]
param(
    [string]$TaskName = "mc-server-manager-startup",
    [string]$ListenHost = "0.0.0.0",
    [int]$Port = 8000,
    [switch]$RemoveBrokenService
)

$ErrorActionPreference = "Stop"

function Test-IsAdministrator {
    $currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentIdentity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-IsAdministrator)) {
    throw "Run this script in an elevated PowerShell session (Administrator)."
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$runScript = Join-Path $repoRoot "scripts\run_prod.ps1"
$venvPython = Join-Path $repoRoot ".venv\Scripts\python.exe"
$windowsPowerShell = Join-Path $env:WINDIR "System32\WindowsPowerShell\v1.0\powershell.exe"

if (-not (Test-Path -LiteralPath $runScript)) {
    throw "Run script not found: '$runScript'."
}
if (-not (Test-Path -LiteralPath $venvPython)) {
    throw "Virtualenv python not found: '$venvPython'. Run setup first."
}
if (-not (Test-Path -LiteralPath $windowsPowerShell)) {
    throw "PowerShell executable not found: '$windowsPowerShell'."
}

if ($RemoveBrokenService) {
    $service = Get-Service -Name "mc-server-manager" -ErrorAction SilentlyContinue
    if ($null -ne $service) {
        if ($service.Status -ne "Stopped") {
            Stop-Service -Name "mc-server-manager" -Force -ErrorAction SilentlyContinue
        }
        & sc.exe delete "mc-server-manager" | Out-Null
    }
}

$actionArgs = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$runScript`"",
    "-ListenHost", "`"$ListenHost`"",
    "-Port", "$Port"
) -join " "

$action = New-ScheduledTaskAction -Execute $windowsPowerShell -Argument $actionArgs -WorkingDirectory $repoRoot
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 0) `
    -RestartCount 999 `
    -RestartInterval (New-TimeSpan -Minutes 1)
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Force | Out-Null

Start-ScheduledTask -TaskName $TaskName
Write-Host "Startup task '$TaskName' installed and started."
