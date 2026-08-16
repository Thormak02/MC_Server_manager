# Web-UI über HTTPS erreichbar machen (Reverse-Proxy mit Caddy)

Ziel: Der Manager ist unter **`https://mc.friedrich-dietrich.de`** erreichbar
(gültiges Zertifikat, ohne `:8000`). Der Minecraft-**Gateway** läuft davon
unabhängig weiter auf Port **25565** — gleicher Name, anderer Port, kein Konflikt.

Was im Repo schon vorbereitet ist:
- [`deploy/Caddyfile`](../deploy/Caddyfile) — fertige Reverse-Proxy-Konfiguration.
- Der Windows-Service startet uvicorn mit `proxy_headers=True` /
  `forwarded_allow_ips=127.0.0.1`, damit hinter dem Proxy das Schema `https`
  korrekt erkannt wird (sonst schlägt die CSRF-Prüfung bei jedem POST fehl).

> **Wichtig:** Die `proxy_headers`-Änderung muss **deployed** sein (Push nach
> `main` → Auto-Deploy, danach Service-Neustart), sonst kommt beim Login hinter
> HTTPS „CSRF validation failed".

---

## Überblick der Ports (Zielzustand)

| Zweck | Extern offen | Ziel |
| --- | --- | --- |
| Minecraft-Gateway | **25565/TCP** | Manager-Prozess (bindet 0.0.0.0:25565) |
| Web-UI HTTPS | **443/TCP** | Caddy → `127.0.0.1:8000` |
| TLS-Zertifikat (ACME) | **80/TCP** | Caddy (für Ausstellung/Erneuerung) |
| Web-UI direkt | 8000 | optional; extern **nicht mehr nötig** |

Der Gateway bindet immer `0.0.0.0:25565` — unabhängig davon, dass die Web-UI
gleich nur noch lokal (`127.0.0.1:8000`) lauscht.

---

## Schritt 1 — DNS (IONOS)

Muss auf `mc.friedrich-dietrich.de` zeigen (haben wir für Minecraft schon gesetzt;
für die Web-UI ist derselbe Eintrag nötig):

- `mc` → **CNAME** → `thormakmc.ddns.net`
- (`*.mc` → **CNAME** → `thormakmc.ddns.net` — nur für die MC-Subdomains)

No-IP hält per DDNS die IP aktuell; IONOS folgt automatisch.

## Schritt 2 — Router / Firewall

- Portweiterleitung auf den PC: **80**, **443** und **25565** (TCP).
- Windows-Firewall eingehend erlauben: **80**, **443**, **25565** (TCP).
- Port **8000** muss extern **nicht mehr** offen sein (nur lokal für Caddy).

## Schritt 3 — Manager nur noch lokal lauschen lassen

Datei `data/service_config.json` (anlegen/anpassen):

```json
{
    "listen_host": "127.0.0.1",
    "port": 8000
}
```

Danach den Manager-Dienst neu starten (Einstellungen → „Anwendung neu starten"
oder `Restart-Service mc-server-manager` in einer Admin-PowerShell). Damit ist die
UI nur noch über Caddy erreichbar (nicht mehr direkt von außen auf :8000).

> Wenn die `proxy_headers`-Änderung noch nicht deployed ist, jetzt zuerst
> Push nach `main` (Auto-Deploy) abwarten, dann neu starten.

## Schritt 4 — Caddy installieren

1. `caddy.exe` von <https://caddyserver.com/download> laden und z.B. nach
   `C:\caddy\` legen.
2. Die vorbereitete Konfig kopieren:
   `deploy/Caddyfile` → `C:\caddy\Caddyfile`
   (optional die `email`-Zeile einkommentieren; die Domainzeile stimmt bereits).
3. **Testlauf** in einer PowerShell in `C:\caddy`:
   ```powershell
   .\caddy.exe run --config .\Caddyfile
   ```
   Caddy sollte ein Zertifikat für `mc.friedrich-dietrich.de` ausstellen
   (dafür muss Port 80 von außen erreichbar sein). Mit `Strg+C` beenden.

## Schritt 5 — Caddy als Windows-Dienst (Autostart)

Am einfachsten mit **NSSM** (Non-Sucking Service Manager, <https://nssm.cc>):

```powershell
# in einer Admin-PowerShell
nssm install caddy "C:\caddy\caddy.exe" run --config "C:\caddy\Caddyfile"
nssm set caddy AppDirectory "C:\caddy"
nssm set caddy Start SERVICE_AUTO_START
nssm start caddy
```

(Alternativ WinSW oder ein Task „Beim Systemstart" mit höchsten Rechten, der
`caddy run --config C:\caddy\Caddyfile` ausführt.)

## Schritt 6 — Prüfen

- Browser: `https://mc.friedrich-dietrich.de` → Login-Seite mit gültigem
  Zertifikat (Schloss-Symbol).
- **Einloggen** (POST) muss funktionieren. Kommt „CSRF validation failed":
  - `proxy_headers`-Stand deployed? Manager-Dienst danach neu gestartet?
  - Caddy leitet `X-Forwarded-Proto: https` weiter (Standard) — nicht deaktivieren.
- Minecraft unverändert: `mc.friedrich-dietrich.de` (Port 25565) → Lobby,
  `atm10.mc.friedrich-dietrich.de` → ATM10.

---

## Hinweise

- **Zertifikat-Erneuerung** läuft automatisch (Caddy); Port **80** muss dafür
  dauerhaft erreichbar bleiben.
- **Kein Wildcard-Zertifikat nötig** — die `*.mc`-Subdomains sind Minecraft
  (kein Browser/TLS). Ein Zertifikat nur für `mc.friedrich-dietrich.de` reicht.
- **Dynamische IP:** unkritisch, da der CNAME über No-IP immer auf die aktuelle
  IP zeigt.
- **No-IP parallel:** `http://thormakmc.ddns.net:8000` funktioniert weiter, wenn
  du Port 8000 extern offen lässt. Willst du die UI ausschließlich über HTTPS,
  schließe 8000 extern und lasse `listen_host=127.0.0.1`.
- **Domainwechsel später:** nur die Domainzeile im `Caddyfile` ändern (+ DNS für
  die neue Domain) und im Manager die Zieldomain/Aliase anpassen — kein `.env`.
