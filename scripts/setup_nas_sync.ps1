<#
.SYNOPSIS
  Richtet einen NAHT-LIVE Sync-Task ein, der die lokalen Manager-Daten
  (logs / db-snapshots / backups) laufend auf die NAS spiegelt.

  Hintergrund: Der Manager laeuft bewusst als SYSTEM-Dienst (headless Boot). Ein
  SYSTEM-Dienst kann fuer SMB keine Benutzer-Anmeldung halten (WinError 1312) und
  daher nicht direkt auf die anmeldepflichtige NAS-Freigabe schreiben. Ein normales
  Benutzerkonto kann das. Darum: Manager schreibt lokal, dieser Task (als Benutzer)
  spiegelt es naht-live auf die NAS.

  Mechanik: Der geplante Task startet JEDE MINUTE ein Loop-Skript, das ~55s lang alle
  paar Sekunden robocopy ausfuehrt (selbstheilend - stirbt der Loop, startet ihn der
  Task in der naechsten Minute neu). logs + db-snapshots werden per /MIR gespiegelt
  (inkl. LOESCHUNGEN -> alte, lokal geprunte Snapshots/Logs verschwinden auch auf der
  NAS -> Speicher bleibt schlank). Backups per /E (kein Loeschen; historische bleiben).

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
    [int]$LoopSeconds = 12,                               # Sync-Takt innerhalb einer Minute
    [string]$TaskName = "mcsm-nas-sync"
)

$ErrorActionPreference = "Stop"

# Loop-Skript schreiben. $LocalData/$NasPath/... werden JETZT eingesetzt; nur Laufzeit-
# Variablen (`$deadline, `$null) sind mit Backtick escaped und werden erst im Loop ausgewertet.
$loopPath = Join-Path $env:ProgramData "mcsm_nas_sync.ps1"
$loop = @"
# AUTO-GENERIERT von setup_nas_sync.ps1 - naht-live Spiegelung data/ -> NAS.
`$ErrorActionPreference = "SilentlyContinue"
net use "$NasPath" "$NasPassword" /user:"$NasUser" 2>`$null | Out-Null
`$deadline = (Get-Date).AddSeconds(55)
while ((Get-Date) -lt `$deadline) {
    # db-snapshots: /MIR (spiegelt Ueberschreiben + Loeschen), .tmp ausschliessen.
    if (Get-ChildItem "$LocalData\db-snapshots" -File -ErrorAction SilentlyContinue) {
        robocopy "$LocalData\db-snapshots" "$NasPath\db-snapshots" /MIR /XF *.tmp /R:0 /W:0 /NFL /NDL /NP /NJH /NJS | Out-Null
    }
    # logs: /MIR (alte, geprunte Session-Logs verschwinden auch auf der NAS).
    if (Get-ChildItem "$LocalData\logs" -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1) {
        robocopy "$LocalData\logs" "$NasPath\logs" /MIR /R:0 /W:0 /NFL /NDL /NP /NJH /NJS | Out-Null
    }
    Start-Sleep -Seconds $LoopSeconds
}
# backups seltener + OHNE Loeschen (historische Backups bleiben erhalten).
robocopy "$LocalData\backups" "$NasPath\backups" /E /R:0 /W:0 /NFL /NDL /NP /NJH /NJS | Out-Null
"@
Set-Content -LiteralPath $loopPath -Value $loop -Encoding UTF8
Write-Host "Sync-Loop-Skript geschrieben: $loopPath"

if (-not $RunAsPassword) {
    $sec = Read-Host "Windows-Passwort von $RunAsUser (fuer den geplanten Task)" -AsSecureString
    $RunAsPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
}

# Jede Minute neu starten (selbstheilend). Der Loop deckt die Minute in ~$LoopSeconds-Schritten ab.
$ps = Join-Path $env:WINDIR "System32\WindowsPowerShell\v1.0\powershell.exe"
$tr = "`"$ps`" -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$loopPath`""
schtasks /create /tn $TaskName /tr $tr /sc minute /mo 1 /ru $RunAsUser /rp $RunAsPassword /rl LIMITED /f | Out-Null
schtasks /run /tn $TaskName | Out-Null

Write-Host ""
Write-Host "Task '$TaskName' registriert: startet jede Minute neu, spiegelt alle ~$LoopSeconds s."
Write-Host "  logs + db-snapshots -> /MIR (inkl. Loeschungen), backups -> /E."
Write-Host "Pruefe $NasPath auf frische db-snapshots\mc_server_manager-current.db."
Write-Host "Entfernen spaeter:  schtasks /delete /tn $TaskName /f"
