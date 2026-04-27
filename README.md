# MC Server Manager

Webbasierte Verwaltungssoftware fuer mehrere Minecraft-Server auf einem Windows-Host.

## Features (Phase 1-6)

- Login mit Rollenmodell (`super_admin`, `admin`, `moderator`, `view_only`)
- SQLite Datenbank mit Auto-Init
- Dashboard mit Serverstatus und Spieleranzeige `n/x`
- Benutzerverwaltung fuer Super Admins
- Import vorhandener Serverordner (keine Struktur-Umstellung noetig)
- Start, Stopp, Neustart inklusive Statusverwaltung
- Live-Konsole via WebSocket (Senden/Empfangen)
- Loganzeige (aktuelle Session + gespeicherte Logs)
- Erweiterte Logansicht mit Level-Filter (Alle/Warnungen/Fehler), Zeilenlimit und Log-Download
- Audit-Log Ansicht
- Erweiterte Audit-Filter (Benutzer, Server, Action, Volltext, Zeitraum) + JSON API
- Dateibearbeitung fuer freigegebene Textdateien
- Konfigeditor mit 2 Modi: Freitext und Assistent (strukturierte Felder)
- Datei-Upload/Download, Textdatei anlegen, Ordner anlegen, Datei/Ordner loeschen (mit Pfadschutz)
- Java-Profile Verwaltung
- Automatische Java-Erkennung auf dem Host (inkl. Versions-Label) und Auto-Zuordnung pro Serverversion
- Optionale Java-Installation ueber Manager via `winget` (Temurin)
- Servereinstellungen (Java, RAM, Port, Startparameter)
- Scheduling fuer Start/Stop/Restart/Command (Cron oder `interval:<sekunden>`)
- Backup Scheduling (Job-Typ `backup`) inklusive Job-Historie
- Verzoegerter Neustart mit optionaler Warnmeldung (`{seconds}`)
- Neustart auch ueber Konsolenkommando `/restart`
- Backups & Restore (manuell, Download, Loeschen, Wiederherstellung, Restore-Historie)
- Server-Wizard fuer Vanilla, Paper, Spigot, Fabric, Forge, NeoForge
- Provider-Prinzip fuer spaetere Erweiterungen
- Optionaler Provisioning-Offline-Modus
- Plattform-Einstellungen (Provider aktiv/deaktivieren, Modrinth User-Agent, CurseForge API Key)
- Sicherheitsfunktionen: Login Rate-Limit, Lockout, Session-Idle-Timeout, CSRF Same-Origin Check
- Security Events Ansicht + API
- Systemstatus Seite + API (Host Summary, Disks, Prozesse)
- Modpack-Import (Preview + Execute) aus:
  - lokalem Archiv (`.zip` / `.mrpack`)
  - Modrinth (Referenz/Version-ID)
  - CurseForge (Projekt-ID/Datei-ID)
- Modpack-Suche im Import-Dialog (Modrinth/CurseForge) mit Versionsauswahl
- Modpack-Import erstellt immer einen neuen Server (Super Admin)
- Import-Protokollierung ueber Audit-Log (`modpack.import_preview`, `modpack.import_execute`)
- Modernes UI mit Light/Dark Umschaltung und ausklappbarer Sidebar

## Voraussetzungen

- Windows 10/11
- Python 3.10+ (empfohlen 3.11)
- Java-Installationen fuer die Ziel-Server (als Java-Profile hinterlegen)
- Optional fuer Java-Installation im Manager: `winget`![alt text](image.png)

## Start

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# danach .env anpassen (API Keys usw.)
uvicorn app.main:app --reload
```

Default URL: `http://127.0.0.1:8000`

## Produktiv-Deployment auf dediziertem Windows Server (Auto-Update via GitHub)

Ziel: Push auf `main` in GitHub soll den Server-PC automatisch aktualisieren.

### 1) Einmaliges Setup auf dem Server-PC

```powershell
git clone https://github.com/Thormak02/MC_Server_manager.git C:\mc_server_manager\mc_server_manager
cd C:\mc_server_manager\mc_server_manager
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# danach .env anpassen
```

### 2) Windows-Service installieren (als Admin-PowerShell)

```powershell
cd C:\mc_server_manager\mc_server_manager
powershell -ExecutionPolicy Bypass -File .\scripts\install_service.ps1 -ServiceName mc-server-manager -Port 8000
```

Damit laeuft die App dauerhaft als Dienst und startet nach Reboot automatisch.
Falls bereits ein (altes/defektes) `mc-server-manager` existiert:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install_service.ps1 -ServiceName mc-server-manager -Port 8000 -Reinstall
```

### Alternative (empfohlen bei Service-Problemen): Startup Task vor Login

Wenn der pywin32-Dienst auf dem Host nicht startet (z. B. `ModuleNotFoundError: servicemanager`), nutze den geplanten Task unter `SYSTEM`.
Der Task startet bei Boot (vor Benutzer-Login) und startet den Manager auf Port `8000`.

```powershell
cd C:\mc_server_manager\mc_server_manager
powershell -ExecutionPolicy Bypass -File .\scripts\install_startup_task.ps1 -TaskName mc-server-manager-startup -Port 8000 -ListenHost 0.0.0.0 -RemoveBrokenService
```

Status pruefen:

```powershell
Get-ScheduledTask -TaskName mc-server-manager-startup
Get-ScheduledTaskInfo -TaskName mc-server-manager-startup
Test-NetConnection -ComputerName 127.0.0.1 -Port 8000
```

### 3) Self-hosted GitHub Runner auf dem Server-PC installieren

In GitHub unter `Settings -> Actions -> Runners` einen Windows self-hosted Runner fuer dieses Repository anlegen und als Windows-Service laufen lassen.

Wichtig: Der Runner-Service-Account muss den Dienst `mc-server-manager` stoppen/starten duerfen (oder als lokaler Admin laufen).

### 4) Repository Variables in GitHub setzen

In `Settings -> Secrets and variables -> Actions -> Variables`:

- `DEPLOY_PATH` = `C:\mc_server_manager\mc_server_manager` (Pflicht)
- `DEPLOY_BRANCH` = `main` (optional, Default = ausloesender Branch)
- `DEPLOY_SERVICE_NAME` = `mc-server-manager` (optional)
- `DEPLOY_PYTHON_EXE` = `python` oder voller Pfad zu `python.exe` (optional)

### 5) Workflow

Die Datei `.github/workflows/deploy-windows-server.yml` deployed bei jedem Push auf `main` automatisch:

- `git pull --ff-only`
- `pip install -r requirements.txt` im `.venv`
- Dienstneustart

Manuelles Ausloesen ist zusaetzlich ueber `workflow_dispatch` moeglich.

## Konfiguration (.env)

Wichtige Variablen:

- `MCSM_SECRET_KEY` Session-Secret
- `MCSM_INITIAL_SUPERADMIN_USERNAME` / `MCSM_INITIAL_SUPERADMIN_PASSWORD`
- `MCSM_SQLITE_PATH` Pfad zur DB (Default: `data/mcsm.sqlite3`)
- `MCSM_SESSION_IDLE_TIMEOUT_SECONDS` Idle-Timeout fuer Sessions
- `MCSM_CSRF_PROTECTION_ENABLED` Same-Origin Schutz fuer Schreib-Requests
- `MCSM_LOGIN_RATE_LIMIT_WINDOW_SECONDS` Zeitfenster fuer Login-Rate-Limit
- `MCSM_LOGIN_RATE_LIMIT_MAX_ATTEMPTS` max. Fehlversuche pro Fenster
- `MCSM_LOGIN_LOCKOUT_SECONDS` Sperrdauer nach zu vielen Fehlversuchen
- `MCSM_PASSWORD_MIN_LENGTH` / `MCSM_PASSWORD_REQUIRE_*` Passwortregeln
- `MCSM_SCHEDULER_TIMEZONE` Zeitzone (Default: `Europe/Berlin`)
- `MCSM_RESTART_WARNING_TEMPLATE` Warntext, `{seconds}` wird ersetzt
- `MCSM_RESTART_DEFAULT_DELAY_SECONDS` Standard-Delay fuer Neustartwarnungen
- `MCSM_PROVISIONING_OFFLINE_MODE` `true` fuer Offline-Setup ohne Downloads
- `MCSM_DEFAULT_SERVER_ROOT` Optionaler Basisordner fuer neue Server (leer => Desktop Standard)
- `MCSM_DEFAULT_BACKUP_ROOT` Optionaler Basisordner fuer Backups
- `MCSM_MODRINTH_ENABLED` / `MCSM_CURSEFORGE_ENABLED` Provider global aktivieren/deaktivieren
- `MCSM_TLS_CA_BUNDLE_PATH` Optionales PEM-Bundle fuer eigene/Firmen-Root-CAs
- `MCSM_TLS_SKIP_VERIFY` Notfall-/Debug-Schalter zum Abschalten der TLS-Pruefung (nicht empfohlen)

## Erstlogin

Beim ersten Start wird automatisch ein Super Admin angelegt:

- Benutzername: Wert aus `MCSM_INITIAL_SUPERADMIN_USERNAME`
- Passwort: Wert aus `MCSM_INITIAL_SUPERADMIN_PASSWORD`

Vor Produktivbetrieb in `.env` aendern.

## Pfade

- `data/` enthaelt SQLite DB + Scheduler-State
- Standard fuer neue Server ist `Desktop\mc_servers` (wenn nicht ueber `.env` oder Einstellungen geaendert)
- Bei automatischer Erstellung (ohne Zielpfad) bekommt jeder Server einen eigenen Unterordner im Basisordner
- Importierte Server werden nicht verschoben oder umgebaut

## Hinweise

- `.env` und Laufzeitdaten sind in `.gitignore` ausgeschlossen.
- Live-Konsole und Ressourcenmonitor benoetigen laufende Serverprozesse fuer sinnvolle Werte.

## Phase 3-6 API Endpunkte

- `POST /api/servers/{server_id}/files/upload` (multipart: `upload`, `target_dir`, `overwrite`)
- `DELETE /api/servers/{server_id}/files?path=<relativ>&recursive=true|false`
- `POST /api/servers/{server_id}/directories` (form: `relative_dir`)
- `GET /api/audit-logs` (Filter: `user_id`, `server_id`, `action`, `q`, `date_from`, `date_to`, `limit`)
- `GET /api/servers/{server_id}/backups`
- `POST /api/servers/{server_id}/backups`
- `DELETE /api/backups/{backup_id}`
- `POST /api/backups/{backup_id}/restore`
- `GET /api/security-events`
- `GET /api/system/summary`
- `GET /api/system/processes`
- `GET /api/platform-settings`
- `PATCH /api/platform-settings/{provider_name}`
- `POST /api/modpacks/import-preview` (multipart/form-data)
- `POST /api/modpacks/import-execute` (form-data)
