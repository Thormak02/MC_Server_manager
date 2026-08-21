[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$StartupTaskName,
    [string]$SelfTaskName = ""
)

$ErrorActionPreference = "Continue"   # ein Kill-Fehler darf den Neustart NICHT abbrechen

Start-Sleep -Seconds 2

# 1) Task-Instanz stoppen (beendet die powershell, die run_prod.ps1 faehrt).
Stop-ScheduledTask -TaskName $StartupTaskName -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

# 2) WICHTIG: den eigentlichen Manager-Prozess killen. Stop-ScheduledTask beendet NUR die
#    Task-Instanz, NICHT den abgekoppelten uvicorn (python -m uvicorn app.main). Der laeuft
#    sonst weiter, haelt den Port, und die frisch gestartete Instanz kann nicht binden ->
#    der ALTE Code bedient weiter (Symptom: "Neustart tut nichts, Code bleibt alt").
#    Gezielt ueber die Kommandozeile (portunabhaengig, trifft nur DIESEN Manager).
try {
    Get-CimInstance Win32_Process -Filter "Name = 'python.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'uvicorn' -and $_.CommandLine -match 'app\.main' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
} catch { }
Start-Sleep -Seconds 2

# 3) Frisch starten (bindet den Port jetzt, weil der alte Prozess weg ist).
Start-ScheduledTask -TaskName $StartupTaskName

if (-not [string]::IsNullOrWhiteSpace($SelfTaskName)) {
    Unregister-ScheduledTask -TaskName $SelfTaskName -Confirm:$false -ErrorAction SilentlyContinue
}
