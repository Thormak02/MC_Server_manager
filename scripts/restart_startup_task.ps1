[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$StartupTaskName,
    [string]$SelfTaskName = ""
)

$ErrorActionPreference = "Stop"

Start-Sleep -Seconds 2
Stop-ScheduledTask -TaskName $StartupTaskName -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1
Start-ScheduledTask -TaskName $StartupTaskName

if (-not [string]::IsNullOrWhiteSpace($SelfTaskName)) {
    Unregister-ScheduledTask -TaskName $SelfTaskName -Confirm:$false -ErrorAction SilentlyContinue
}
