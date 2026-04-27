[CmdletBinding()]
param(
    [string]$ServiceName = "mc-server-manager",
    [string]$DisplayName = "MC Server Manager",
    [string]$ListenHost = "0.0.0.0",
    [int]$Port = 8000
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

if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    throw "Service '$ServiceName' already exists."
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$runScript = Join-Path $repoRoot "scripts\run_prod.ps1"

if (-not (Test-Path -LiteralPath $runScript)) {
    throw "Start script not found: '$runScript'."
}

$binaryPath = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$runScript`" -ListenHost `"$ListenHost`" -Port $Port"

New-Service `
    -Name $ServiceName `
    -DisplayName $DisplayName `
    -Description "Minecraft Server Manager (FastAPI/uvicorn)" `
    -BinaryPathName $binaryPath `
    -StartupType Automatic

Start-Service -Name $ServiceName
Write-Host "Service '$ServiceName' installed and started."
