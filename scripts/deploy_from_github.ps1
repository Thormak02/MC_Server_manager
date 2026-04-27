[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [string]$Branch = "main",
    [string]$ServiceName = "mc-server-manager",
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([string[]]$Arguments)

    Write-Host "git $($Arguments -join ' ')"
    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
}

$resolvedRepoPath = (Resolve-Path -LiteralPath $RepoPath).Path
if (-not (Test-Path -LiteralPath (Join-Path $resolvedRepoPath ".git"))) {
    throw "Path '$resolvedRepoPath' is not a git repository."
}

$service = $null
if (-not [string]::IsNullOrWhiteSpace($ServiceName)) {
    $service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($null -eq $service) {
        Write-Warning "Service '$ServiceName' not found. Deployment will continue without restart."
    }
    elseif ($service.Status -ne "Stopped") {
        Write-Host "Stopping service '$ServiceName'..."
        Stop-Service -Name $ServiceName -Force
        (Get-Service -Name $ServiceName).WaitForStatus("Stopped", [TimeSpan]::FromSeconds(90))
    }
}

Push-Location -LiteralPath $resolvedRepoPath
try {
    Invoke-Git @("fetch", "origin", $Branch)
    Invoke-Git @("checkout", $Branch)
    Invoke-Git @("pull", "--ff-only", "origin", $Branch)

    $venvPython = Join-Path $resolvedRepoPath ".venv\Scripts\python.exe"
    if (-not (Test-Path -LiteralPath $venvPython)) {
        Write-Host "Creating virtual environment with '$PythonExe'..."
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

if ($null -ne $service) {
    Write-Host "Starting service '$ServiceName'..."
    Start-Service -Name $ServiceName
    (Get-Service -Name $ServiceName).WaitForStatus("Running", [TimeSpan]::FromSeconds(90))
}

Write-Host "Deployment completed successfully."
