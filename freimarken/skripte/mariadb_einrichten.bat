@echo off
rem Startet mariadb_einrichten.ps1 mit Adminrechten.
rem
rem UNC-Pfade kann cmd.exe nicht als Arbeitsverzeichnis benutzen - liegt das Repository
rem auf einer Freigabe, wird deshalb ueber das Netzlaufwerk gearbeitet. Deshalb hier
rem pushd: das legt bei Bedarf selbst ein temporaeres Laufwerk an.
setlocal
pushd "%~dp0" || (echo Konnte nicht in den Skriptordner wechseln. & pause & exit /b 1)

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Hebe auf Adminrechte an ...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    popd
    exit /b
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mariadb_einrichten.ps1"
echo.
pause
popd
