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
- Servereinstellungen (Java, RAM, Port, Startparameter)
- Scheduling fuer Start/Stop/Restart/Command (Cron oder `interval:<sekunden>`)
- Backup Scheduling (Job-Typ `backup`) inklusive Job-Historie
- Verzoegerter Neustart mit optionaler Warnmeldung (`{seconds}`)
- Neustart auch ueber Konsolenkommando `/restart`
- Backups & Restore (manuell, Download, Loeschen, Wiederherstellung, Restore-Historie)
- Server-Wizard fuer Vanilla, Paper, Spigot, Fabric, Forge
- Provider-Prinzip fuer spaetere Erweiterungen
- Optionaler Provisioning-Offline-Modus
- Ressourcenmonitor (Host + Server CPU/RAM, live aktualisiert)
- Modernes UI mit Light/Dark Umschaltung und ausklappbarer Sidebar

## Voraussetzungen

- Windows 10/11
- Python 3.10+ (empfohlen 3.11)
- Java-Installationen fuer die Ziel-Server (als Java-Profile hinterlegen)

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

## Konfiguration (.env)

Wichtige Variablen:

- `MCSM_SECRET_KEY` Session-Secret
- `MCSM_INITIAL_SUPERADMIN_USERNAME` / `MCSM_INITIAL_SUPERADMIN_PASSWORD`
- `MCSM_SQLITE_PATH` Pfad zur DB (Default: `data/mcsm.sqlite3`)
- `MCSM_SCHEDULER_TIMEZONE` Zeitzone (Default: `Europe/Berlin`)
- `MCSM_RESTART_WARNING_TEMPLATE` Warntext, `{seconds}` wird ersetzt
- `MCSM_RESTART_DEFAULT_DELAY_SECONDS` Standard-Delay fuer Neustartwarnungen
- `MCSM_PROVISIONING_OFFLINE_MODE` `true` fuer Offline-Setup ohne Downloads
- `MCSM_DEFAULT_SERVER_ROOT` Optionaler Basisordner fuer neue Server (leer => Desktop Standard)
- `MCSM_DEFAULT_BACKUP_ROOT` Optionaler Basisordner fuer Backups

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

## Phase 3-4 API Endpunkte

- `POST /api/servers/{server_id}/files/upload` (multipart: `upload`, `target_dir`, `overwrite`)
- `DELETE /api/servers/{server_id}/files?path=<relativ>&recursive=true|false`
- `POST /api/servers/{server_id}/directories` (form: `relative_dir`)
- `GET /api/audit-logs` (Filter: `user_id`, `server_id`, `action`, `q`, `date_from`, `date_to`, `limit`)
- `GET /api/servers/{server_id}/backups`
- `POST /api/servers/{server_id}/backups`
- `DELETE /api/backups/{backup_id}`
- `POST /api/backups/{backup_id}/restore`
