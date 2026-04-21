# MC Server Manager

Web-based management software for multiple Minecraft servers on a Windows host.

## Features (Phase 1-6)

- Login with roles (`super_admin`, `admin`, `moderator`, `view_only`)
- SQLite database with auto-init
- Dashboard with server status and player count `n/x`
- Super Admin user management
- Import existing server folders (no restructuring required)
- Start, stop, restart with runtime status tracking
- Live console via WebSocket (send/receive)
- Log view (current session + stored logs)
- Extended log view with level filters (all/warnings/errors), line limit, and log download
- Audit log view
- Extended audit filters (user, server, action, full text, time range) + JSON API
- File editor for whitelisted text files
- Config editor with 2 modes: raw text and assistant (structured fields)
- File upload/download, text file creation, directory creation, file/directory delete (path-protected)
- Java profile management
- Automatic Java detection on the host (with version labels) and auto-assignment per server version
- Optional Java installation via manager using `winget` (Temurin)
- Server settings (Java, RAM, port, start parameters)
- Scheduling for start/stop/restart/command (cron or `interval:<seconds>`)
- Backup scheduling (job type `backup`) including job history
- Delayed restart with optional warning message (`{seconds}`)
- Restart via console command `/restart`
- Backups & restore (manual creation, download, deletion, restore, restore history)
- Server wizard for Vanilla, Paper, Spigot, Fabric, Forge, NeoForge
- Provider pattern for future extensions
- Optional provisioning offline mode
- Platform settings (enable/disable providers, Modrinth user-agent, CurseForge API key)
- Security features: login rate limiting, lockout, session idle timeout, CSRF same-origin checks
- Security events page + API
- System status page + API (host summary, disks, processes)
- Modpack import (preview + execute) from:
  - local archive (`.zip` / `.mrpack`)
  - Modrinth (reference/version id)
  - CurseForge (project id/file id)
- Modpack search in the import page (Modrinth/CurseForge) with version selection
- Modpack import always creates a new server (Super Admin)
- Import protocol logging via audit log (`modpack.import_preview`, `modpack.import_execute`)
- Modern UI with light/dark toggle and collapsible sidebar

## Requirements

- Windows 10/11
- Python 3.10+ (3.11 recommended)
- Java installations for the target servers (configure as Java profiles)
- Optional for Java install in manager: `winget`

## Start

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# then edit .env (API keys, etc.)
uvicorn app.main:app --reload
```

Default URL: `http://127.0.0.1:8000`

## Configuration (.env)

Key variables:

- `MCSM_SECRET_KEY` session secret
- `MCSM_INITIAL_SUPERADMIN_USERNAME` / `MCSM_INITIAL_SUPERADMIN_PASSWORD`
- `MCSM_SQLITE_PATH` database path (default: `data/mcsm.sqlite3`)
- `MCSM_SESSION_IDLE_TIMEOUT_SECONDS` session idle timeout
- `MCSM_CSRF_PROTECTION_ENABLED` same-origin protection for write requests
- `MCSM_LOGIN_RATE_LIMIT_WINDOW_SECONDS` window for login rate limiting
- `MCSM_LOGIN_RATE_LIMIT_MAX_ATTEMPTS` max failed attempts per window
- `MCSM_LOGIN_LOCKOUT_SECONDS` lockout duration after too many failures
- `MCSM_PASSWORD_MIN_LENGTH` / `MCSM_PASSWORD_REQUIRE_*` password rules
- `MCSM_SCHEDULER_TIMEZONE` timezone (default: `Europe/Berlin`)
- `MCSM_RESTART_WARNING_TEMPLATE` warning text, `{seconds}` is replaced
- `MCSM_RESTART_DEFAULT_DELAY_SECONDS` default delay for restart warnings
- `MCSM_PROVISIONING_OFFLINE_MODE` `true` for offline setup without downloads
- `MCSM_DEFAULT_SERVER_ROOT` optional base directory for new servers (empty => desktop default)
- `MCSM_DEFAULT_BACKUP_ROOT` optional base directory for backups
- `MCSM_MODRINTH_ENABLED` / `MCSM_CURSEFORGE_ENABLED` global provider enable flags

## First Login

On first start a Super Admin is created:

- Username: value from `MCSM_INITIAL_SUPERADMIN_USERNAME`
- Password: value from `MCSM_INITIAL_SUPERADMIN_PASSWORD`

Change these in `.env` before production use.

## Paths

- `data/` contains SQLite DB + scheduler state
- Default base folder for new servers is `Desktop\mc_servers` (unless changed via `.env` or Settings UI)
- For automatic creation (no target path provided), each server gets its own subdirectory in that base folder
- Imported servers are not moved or restructured

## Notes

- `.env` and runtime data are excluded via `.gitignore`.
- Live console and resource monitor require running server processes for meaningful values.

## Phase 3-6 API Endpoints

- `POST /api/servers/{server_id}/files/upload` (multipart: `upload`, `target_dir`, `overwrite`)
- `DELETE /api/servers/{server_id}/files?path=<relative>&recursive=true|false`
- `POST /api/servers/{server_id}/directories` (form: `relative_dir`)
- `GET /api/audit-logs` (filters: `user_id`, `server_id`, `action`, `q`, `date_from`, `date_to`, `limit`)
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
