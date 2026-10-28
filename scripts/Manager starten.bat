@echo off
REM ===================================================================
REM  MC Server Manager STARTEN
REM
REM  Der Manager laeuft als geplante Aufgabe "mc-server-manager-startup"
REM  unter SYSTEM (headless, auch ohne angemeldeten Benutzer). Starten
REM  und Stoppen braucht deshalb Administratorrechte - diese Datei holt
REM  sie sich selbst, es kommt eine Nachfrage von Windows.
REM
REM  Fuer eine Desktop-Verknuepfung: Rechtsklick -> Senden an -> Desktop.
REM ===================================================================
setlocal
set TASK=mc-server-manager-startup
set PORT=8000
chcp 65001 >nul 2>&1

net session >nul 2>&1
if errorlevel 1 (
    echo Administratorrechte werden angefordert ...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo.
echo  MC Server Manager wird gestartet ...
echo.

schtasks /query /tn "%TASK%" >nul 2>&1
if errorlevel 1 (
    echo  FEHLER: Die Aufgabe "%TASK%" gibt es nicht.
    echo  Einmalig einrichten mit: scripts\install_startup_task.ps1
    echo.
    pause
    exit /b 1
)

REM Laeuft er schon? Dann nicht ein zweites Mal starten - die zweite Instanz
REM bekaeme den Port nicht und der alte Code bediente weiter.
powershell -NoProfile -Command "exit (@(Get-CimInstance Win32_Process -Filter \"Name = 'python.exe'\" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match 'uvicorn' -and $_.CommandLine -match 'app\.main' }).Count)"
if not errorlevel 1 goto starten
echo  Der Manager laeuft bereits. Nichts zu tun.
echo  Oberflaeche: http://localhost:%PORT%
echo.
pause
exit /b 0

:starten
schtasks /run /tn "%TASK%" >nul
if errorlevel 1 (
    echo  FEHLER: Die Aufgabe liess sich nicht starten.
    echo.
    pause
    exit /b 1
)

echo  Gestartet. Warte, bis die Oberflaeche antwortet ...
powershell -NoProfile -Command "$ok=$false; foreach ($i in 1..40) { Start-Sleep -Milliseconds 750; try { $null = Invoke-WebRequest -Uri 'http://localhost:%PORT%/login' -UseBasicParsing -TimeoutSec 3; $ok=$true; break } catch { } }; if ($ok) { Write-Host '  Der Manager antwortet.' } else { Write-Host '  Noch keine Antwort nach 30 s - siehe data\logs\startup-task.err.log' }"

echo.
echo  Oberflaeche: http://localhost:%PORT%
echo.
echo  Hinweis: laufende Minecraft-Server sind davon nicht betroffen.
echo  Der Manager findet sie beim Start wieder.
echo.
pause
