# MC Server Manager

Phase 1 bis Phase 6 Basis fuer eine webbasierte Minecraft-Server-Verwaltung mit:

- Login via Benutzername + Passwort
- Rollenmodell (`super_admin`, `admin`, `moderator`, `view_only`)
- SQLite Datenbank (automatische Tabellen-Initialisierung)
- Dashboard mit Sicht auf zugewiesene Server
- Super-Admin Benutzerverwaltung (Anlegen, Deaktivieren, Passwort-Reset)
- Import bestehender Serverordner mit Erkennung von Startdatei/Servertyp
- Start, Stopp und Neustart von importierten Servern
- Laufzeit-Statusverwaltung (`running`/`stopped`)
- Live Konsole mit WebSocket-Streaming und Kommandoeingabe
- Loganzeige (aktuelle Sitzung + gespeicherte Session-Logdateien)
- Audit-Log Ansicht im Webinterface
- Dateibearbeitung fuer freigegebene Textdateien im Serverordner
- Java-Profile Verwaltung in den Einstellungen
- Servereinstellungen (Java-Profil, RAM, Port, Startparameter, Auto-Restart)
- Scheduling fuer Start/Stop/Restart/Command (Cron oder interval:<sekunden>)
- Verzoegerter Neustart mit optionaler Warnmeldung (`{seconds}`)
- Neustart auch ueber Konsolenkommando `/restart`
- Server-Wizard fuer Vanilla, Paper, Spigot, Fabric und Forge
- Provider-Prinzip mit austauschbaren `server_providers`
- Optionaler Provisioning-Offline-Modus fuer Test/Setup ohne Download
- Modernes UI mit Light/Dark Umschaltung und ausklappbarem Seitenmenue
- Dashboard mit Spieleranzeige im Format `n/x` pro Server
- Konfigeditor mit 2 Modi: Freitext und Assistent (Dropdown/strukturierte Felder)
- Ressourcenmonitor mit Host- und Serververbrauch (CPU/RAM je Server)

## Start

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload
```

Default URL: `http://127.0.0.1:8000`

## Erstlogin

Die Anwendung legt beim ersten Start automatisch einen Super Admin an:

- Benutzername: Wert aus `MCSM_INITIAL_SUPERADMIN_USERNAME`
- Passwort: Wert aus `MCSM_INITIAL_SUPERADMIN_PASSWORD`

Wichtig: Werte vor Produktivbetrieb in `.env` anpassen.
