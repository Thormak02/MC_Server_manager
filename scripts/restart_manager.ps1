[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [string]$ServiceName = "mc-server-manager",
    [string]$StartupTaskName = "mc-server-manager-startup"
)

$ErrorActionPreference = "Stop"

$resolvedRepoPath = (Resolve-Path -LiteralPath $RepoPath).Path

$service = $null
if (-not [string]::IsNullOrWhiteSpace($ServiceName)) {
    $service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
}

if ($null -ne $service) {
    if ($service.Status -ne "Stopped") {
        Write-Output "Stopping service '$ServiceName'..."
        Stop-Service -Name $ServiceName -Force
        (Get-Service -Name $ServiceName).WaitForStatus("Stopped", [TimeSpan]::FromSeconds(90))
    }
    Write-Output "Starting service '$ServiceName'..."
    Start-Service -Name $ServiceName
    (Get-Service -Name $ServiceName).WaitForStatus("Running", [TimeSpan]::FromSeconds(90))
    Write-Output "Service '$ServiceName' restarted."
    exit 0
}

$startupTask = $null
if (-not [string]::IsNullOrWhiteSpace($StartupTaskName)) {
    $startupTask = Get-ScheduledTask -TaskName $StartupTaskName -ErrorAction SilentlyContinue
}

if ($null -eq $startupTask) {
    throw "Neither service '$ServiceName' nor startup task '$StartupTaskName' found."
}

$helperScriptPath = Join-Path $resolvedRepoPath "scripts\restart_startup_task.ps1"
if (-not (Test-Path -LiteralPath $helperScriptPath)) {
    throw "Restart helper script not found: '$helperScriptPath'."
}

$helperTaskName = "$StartupTaskName-manual-restart"
$existingHelper = Get-ScheduledTask -TaskName $helperTaskName -ErrorAction SilentlyContinue
if ($null -ne $existingHelper) {
    Unregister-ScheduledTask -TaskName $helperTaskName -Confirm:$false -ErrorAction SilentlyContinue
}

$windowsPowerShell = Join-Path $env:WINDIR "System32\WindowsPowerShell\v1.0\powershell.exe"
$helperArgs = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$helperScriptPath`"",
    "-StartupTaskName", "`"$StartupTaskName`"",
    "-SelfTaskName", "`"$helperTaskName`""
) -join " "

$action = New-ScheduledTaskAction -Execute $windowsPowerShell -Argument $helperArgs -WorkingDirectory $resolvedRepoPath
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1)
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10)
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest

Register-ScheduledTask `
    -TaskName $helperTaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Force | Out-Null

Start-ScheduledTask -TaskName $helperTaskName
Write-Output "Scheduled task restart helper '$helperTaskName' for '$StartupTaskName'."
