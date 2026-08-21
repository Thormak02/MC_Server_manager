<#
.SYNOPSIS
  Richtet einen Sync-Task ein, der die lokalen Manager-Daten (logs / backups / db-snapshots)
  regelmaessig auf die NAS spiegelt.

  Hintergrund: Der Manager laeuft bewusst als SYSTEM-Dienst (headless Boot ohne Login).
  Ein SYSTEM-Dienst kann fuer SMB aber KEINE Benutzer-Anmeldung halten (WinError 1312),
  kann also nicht direkt auf eine anmeldepflichtige NAS-Freigabe schreiben. Ein NORMALES
  Benutzerkonto kann das sehr wohl. Darum: Manager schreibt lokal, dieser Task (als Benutzer)
  spiegelt es auf die NAS.

  EINMALIG als Administrator auf dem Server-PC ausfuehren.

.EXAMPLE
  .\setup_nas_sync.ps1 -NasPath "\\FriedrichNAS\FriedrichNAS\MC-manager-Logs" `
      -NasUser "smb-benutzer" -NasPassword "smb-passwort" -RunAsUser "Friedrich"
#>
[CmdletBinding()]
param(
    [string]$LocalData = "C:\mc_server_manager\mc_server_manager\data",
    [Parameter(Mandatory = $true)][string]$NasPath,       # \\Host\Freigabe\MC-manager-Logs
    [Parameter(Mandatory = $true)][string]$NasUser,       # NAS-SMB-Benutzer
    [Parameter(Mandatory = $true)][string]$NasPassword,   # NAS-SMB-Passwort
    [Parameter(Mandatory = $true)][string]$RunAsUser,     # Windows-Konto mit NAS-Zugang (z.B. Friedrich)
    [string]$RunAsPassword,                               # Windows-Passwort (sonst wird gefragt)
    [int]$IntervalMinutes = 10,
    [string]$TaskName = "mcsm-nas-sync"
)

$ErrorActionPreference = "Stop"

$batPath = Join-Path $env:ProgramData "mcsm_nas_sync.bat"
$bat = @"
@echo off
rem NAS-Freigabe im Benutzerkontext authentifizieren (ignoriert 'schon verbunden').
net use "$NasPath" "$NasPassword" /user:"$NasUser" >nul 2>&1
robocopy "$LocalData\logs"         "$NasPath\logs"         /E /R:1 /W:1 /NFL /NDL /NP
robocopy "$LocalData\backups"      "$NasPath\backups"      /E /R:1 /W:1 /NFL /NDL /NP
robocopy "$LocalData\db-snapshots" "$NasPath\db-snapshots" /E /R:1 /W:1 /NFL /NDL /NP
exit /b 0
"@
Set-Content -LiteralPath $batPath -Value $bat -Encoding OEM
Write-Host "Sync-Skript geschrieben: $batPath"

if (-not $RunAsPassword) {
    $sec = Read-Host "Windows-Passwort von $RunAsUser (fuer den geplanten Task)" -AsSecureString
    $RunAsPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
}

# schtasks (statt Register-ScheduledTask): /sc minute /mo N wiederholt zuverlaessig unbegrenzt.
schtasks /create /tn $TaskName /tr "cmd /c `"$batPath`"" /sc minute /mo $IntervalMinutes `
    /ru $RunAsUser /rp $RunAsPassword /rl LIMITED /f | Out-Null
schtasks /run /tn $TaskName | Out-Null

Write-Host ""
Write-Host "Task '$TaskName' registriert + einmal gestartet (alle $IntervalMinutes Min als $RunAsUser)."
Write-Host "Pruefe jetzt $NasPath auf frische Dateien in logs/ backups/ db-snapshots/."
Write-Host "Entfernen spaeter:  schtasks /delete /tn $TaskName /f"
