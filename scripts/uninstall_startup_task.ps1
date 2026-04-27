[CmdletBinding()]
param(
    [string]$TaskName = "mc-server-manager-startup"
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

$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($null -eq $task) {
    Write-Host "Startup task '$TaskName' not found."
    exit 0
}

try {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
} catch {
}

Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
Write-Host "Startup task '$TaskName' removed."
