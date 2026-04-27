[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [string]$Branch = "main",
    [string]$ServiceName = "mc-server-manager",
    [string]$StartupTaskName = "mc-server-manager-startup",
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([string[]]$Arguments)

    Write-Output "git $($Arguments -join ' ')"
    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
}

function Invoke-StartupTaskRestart {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoPath,
        [Parameter(Mandatory = $true)]
        [string]$TaskName
    )

    $helperScriptPath = Join-Path $RepoPath "scripts\restart_startup_task.ps1"
    if (-not (Test-Path -LiteralPath $helperScriptPath)) {
        throw "Restart helper script not found: '$helperScriptPath'."
    }

    $helperTaskName = "$TaskName-update-restart"
    $existingHelper = Get-ScheduledTask -TaskName $helperTaskName -ErrorAction SilentlyContinue
    if ($null -ne $existingHelper) {
        Unregister-ScheduledTask -TaskName $helperTaskName -Confirm:$false -ErrorAction SilentlyContinue
    }

    $windowsPowerShell = Join-Path $env:WINDIR "System32\WindowsPowerShell\v1.0\powershell.exe"
    $helperArgs = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", "`"$helperScriptPath`"",
        "-StartupTaskName", "`"$TaskName`"",
        "-SelfTaskName", "`"$helperTaskName`""
    ) -join " "

    $action = New-ScheduledTaskAction -Execute $windowsPowerShell -Argument $helperArgs -WorkingDirectory $RepoPath
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
    Write-Output "Scheduled task restart helper '$helperTaskName' for '$TaskName'."
}

$resolvedRepoPath = (Resolve-Path -LiteralPath $RepoPath).Path
if (-not (Test-Path -LiteralPath (Join-Path $resolvedRepoPath ".git"))) {
    throw "Path '$resolvedRepoPath' is not a git repository."
}

$service = $null
$startupTask = $null
if (-not [string]::IsNullOrWhiteSpace($ServiceName)) {
    $service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
}
if (-not [string]::IsNullOrWhiteSpace($StartupTaskName)) {
    $startupTask = Get-ScheduledTask -TaskName $StartupTaskName -ErrorAction SilentlyContinue
}

$useStartupTask = $null -ne $startupTask

if ($useStartupTask) {
    Write-Output "Startup task '$StartupTaskName' detected and preferred for runtime restart."
}
elseif ($null -ne $service) {
    if ($service.Status -ne "Stopped") {
        Write-Output "Stopping service '$ServiceName'..."
        Stop-Service -Name $ServiceName -Force
        (Get-Service -Name $ServiceName).WaitForStatus("Stopped", [TimeSpan]::FromSeconds(90))
    }
}
else {
    Write-Warning "Service '$ServiceName' and startup task '$StartupTaskName' not found. Deployment continues without restart target."
}

Push-Location -LiteralPath $resolvedRepoPath
try {
    Invoke-Git @("fetch", "origin", $Branch)
    Invoke-Git @("checkout", $Branch)
    Invoke-Git @("pull", "--ff-only", "origin", $Branch)

    $venvPython = Join-Path $resolvedRepoPath ".venv\Scripts\python.exe"
    if (-not (Test-Path -LiteralPath $venvPython)) {
        Write-Output "Creating virtual environment with '$PythonExe'..."
        & $PythonExe -m venv .venv
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create virtual environment with '$PythonExe'."
        }
    }

    if (-not (Test-Path -LiteralPath $venvPython)) {
        throw "Virtual environment not found at '$venvPython'."
    }

    & $venvPython -m pip install --upgrade pip
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to upgrade pip."
    }

    & $venvPython -m pip install -r requirements.txt
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to install requirements."
    }

    if (-not (Test-Path -LiteralPath ".env")) {
        if (Test-Path -LiteralPath ".env.example") {
            Copy-Item -LiteralPath ".env.example" -Destination ".env"
            Write-Warning "'.env' was missing and has been created from '.env.example'. Please review secrets."
        }
        else {
            Write-Warning "'.env' is missing and '.env.example' was not found."
        }
    }
}
finally {
    Pop-Location
}

if ($useStartupTask) {
    Invoke-StartupTaskRestart -RepoPath $resolvedRepoPath -TaskName $StartupTaskName
}
elseif ($null -ne $service) {
    Write-Output "Starting service '$ServiceName'..."
    Start-Service -Name $ServiceName
    (Get-Service -Name $ServiceName).WaitForStatus("Running", [TimeSpan]::FromSeconds(90))
}

Write-Output "Deployment completed successfully."
