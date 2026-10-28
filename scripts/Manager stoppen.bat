@echo off
REM ===================================================================
REM  MC Server Manager STOPPEN
REM
REM  Zwei Schritte, und der zweite ist der wichtige:
REM   1. Die geplante Aufgabe beenden - das beendet nur die PowerShell,
REM      die run_prod.ps1 faehrt.
REM   2. Den eigentlichen Manager-Prozess (uvicorn) beenden. Ohne diesen
REM      Schritt laeuft er weiter, haelt Port 8000 und eine frisch
REM      gestartete Instanz kann nicht binden - Symptom: "Neustart tut
REM      nichts, der alte Code bedient weiter".
REM
REM  LAUFENDE MINECRAFT-SERVER BLEIBEN AN. Sie sind eigene Prozesse; der
REM  Manager findet sie beim naechsten Start wieder. Wer sie auch stoppen
REM  will, macht das vorher in der Oberflaeche.
REM
REM  Fuer eine Desktop-Verknuepfung: Rechtsklick -> Senden an -> Desktop.
REM ===================================================================
setlocal
set TASK=mc-server-manager-startup
chcp 65001 >nul 2>&1

net session >nul 2>&1
if errorlevel 1 (
    echo Administratorrechte werden angefordert ...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo.
echo  MC Server Manager wird gestoppt ...
echo.

schtasks /end /tn "%TASK%" >nul 2>&1

powershell -NoProfile -Command "$p = @(Get-CimInstance Win32_Process -Filter \"Name = 'python.exe'\" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match 'uvicorn' -and $_.CommandLine -match 'app\.main' }); if ($p.Count -eq 0) { Write-Host '  Es lief kein Manager-Prozess.' } else { $p | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Seconds 2; $r = @(Get-CimInstance Win32_Process -Filter \"Name = 'python.exe'\" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match 'uvicorn' -and $_.CommandLine -match 'app\.main' }); if ($r.Count -eq 0) { Write-Host ('  Beendet: ' + $p.Count + ' Prozesse beendet.') } else { Write-Host '  ACHTUNG: ein Manager-Prozess laeuft noch.' } }"

echo.
echo  Laufende Minecraft-Server sind weiterhin an.
echo.
pause
