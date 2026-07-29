# Lobby-/Gateway-Routing-Proxy — Umsetzungsplan & Prompts

Ziel: **ein** MC-Eingangspunkt (Gateway) auf einem Port. Jeder Server bekommt
einen **Hostnamen-Alias**. Ein Spieler verbindet sich mit `atm10.deinedomain`
→ das Gateway liest den Handshake, wählt anhand **Alias** (Fallback:
**Protokoll-Version**) das Backend und leitet **transparent** weiter — inkl.
**Aufwecken** schlafender Server. Es gibt keinen begehbaren Cross-Version-Hub
(Client-Limit); das Gateway ist ein unsichtbarer Router.

## Domain-Modell (Beispiel `thormakmc.de`)

Eine Domain, zwei Protokolle auf **derselben IP**, aber **verschiedenen Ports**
— daher kein Konflikt und kein Port-Multiplexing nötig:

| Aufruf | Ziel | Port |
| --- | --- | --- |
| `thormakmc.de` im **Browser** | Manager (Web-UI, HTTP) | 80/443 |
| `thormakmc.de` in **Minecraft** | Gateway → **Lobby-Server** (Apex-/Default-Route) | 25565 |
| `atm10.thormakmc.de` in **Minecraft** | Gateway → ATM10-Server (weckt ihn ggf.) | 25565 |

- **Apex/Default-Route:** Verbindet sich ein Client mit dem blanken
  `thormakmc.de` (oder einem unbekannten Hostnamen), routet das Gateway auf den
  als **„Lobby/Default" markierten** Server.
- **Versions-Vorbehalt Apex-Lobby:** Der Lobby-Server hat eine feste Version.
  **Modpack-Clients** können die (Vanilla-)Lobby nicht betreten — sie nutzen
  die **Subdomain** ihres Servers (`atm10.thormakmc.de`). Die Lobby kann die
  Modpack-Adressen per Schild/Plugin anzeigen, den Client aber nicht
  „hinüberschieben" (Client-Limit).
- **Manager auf 80/443:** i.d.R. hinter einem Reverse-Proxy (nginx/Caddy/IIS)
  mit TLS; das Gateway bleibt auf dem MC-Port.

Baut auf dem bestehenden `app/services/sleep_proxy_service.py` und
`app/services/mc_protocol.py` auf. Grundprinzipien (für alle Phasen):
- **Opt-in & standardmäßig AUS** — kein Einfluss auf bestehende Server, solange
  das Gateway deaktiviert ist.
- **Transparentes Forwarding** (kein Protokoll-Eingriff) → Forge/Fabric/
  Modpacks funktionieren, weil der Client bereits zum Ziel passt.
- **Bind ohne `SO_REUSEADDR`** (korrekte Port-Belegung, auch unter Windows).
- Nach jeder Phase: **volle Testsuite grün** (`pytest`).

---

## Externe Voraussetzungen (Gesamtüberblick)

Erst relevant ab dem Live-Betrieb (Phase 4 / Aktivierung), nicht für die
Entwicklung/Tests:

1. **DNS** (Beispiel `thormakmc.de`, alle auf dieselbe Server-IP):
   - `thormakmc.de` → A-Record auf die Server-IP (Apex; für Browser **und**
     Minecraft-Apex).
   - `*.thormakmc.de` → Wildcard-A-Record auf dieselbe IP (für die
     Server-Subdomains wie `atm10.thormakmc.de`).
   - Optional `_minecraft._tcp.thormakmc.de` → **SRV-Record**, falls das Gateway
     NICHT auf dem Standardport 25565 läuft (dann können Spieler trotzdem ohne
     `:port` verbinden).
   - Lokal testbar ohne DNS: `hosts`-Datei, z.B. `127.0.0.1 atm10.local`, dann
     im Client `atm10.local` verbinden.
2. **Manager auf 80/443**: damit `thormakmc.de` im Browser ohne Port
   funktioniert — i.d.R. Reverse-Proxy (nginx/Caddy/IIS) mit TLS vor dem
   Manager (der intern auf seinem HTTP-Port bleibt).
3. **Router/Firewall**: **Gateway-Port** (25565) **und** 80/443 nach außen
   freigeben/forwarden. Die **internen Server-Ports NICHT** öffnen.
4. **.env** auf dem Server: `MCSM_GATEWAY_ENABLED=true`,
   `MCSM_GATEWAY_PORT=25565`.
5. Pro Server einen **Alias** (Subdomain) vergeben, **einen** Server als
   **Lobby/Default** markieren, betroffene Server **einmal neu starten**
   (wechseln auf den internen Port).
6. **Live-Test** mit echtem Client je Version/Modpack (kann ich nicht
   simulieren).

---

## Phase 1 — Gateway-Kern (Routing + Forward + Wake)

**Externe To-dos:** keine (rein lokal testbar; optional hosts-Einträge für
Hostname-Tests).

**Prompt zum Einfügen:**

> Implementiere **Phase 1** des Lobby-/Gateway-Routing-Proxys für den MC Server
> Manager. Baue auf `app/services/sleep_proxy_service.py` und
> `app/services/mc_protocol.py` auf. Scope (Backend-Kern, noch keine UI):
>
> - **Config** (`app/core/config.py`): `gateway_enabled: bool = False`,
>   `gateway_port: int = 25565` (env `MCSM_GATEWAY_ENABLED`,
>   `MCSM_GATEWAY_PORT`).
> - **DB-Felder** am Server-Modell + Migration in `init_db._ensure_server_schema`:
>   `gateway_enabled: bool = False`, `gateway_hostname: str | None`,
>   `gateway_is_default: bool = False` (genau **ein** Server darf Default/Lobby
>   sein → für die Apex-Route). Server „hinter dem Gateway" laufen auf ihrem
>   internen Port (bestehendes `sleep_internal_port`-Konzept wiederverwenden;
>   falls kein interner Port gesetzt ist, über
>   `port_service.allocate_server_port` einen vergeben).
> - **Routing-Tabelle**: Hilfsfunktion `build_gateway_routes(db)` →
>   `{hostname: server_id}`, `{version: server_id}` (nur wenn eindeutig) und
>   `default_server_id` (der `gateway_is_default`-Server). Quelle: alle Server
>   mit `gateway_enabled=True`.
> - **Gateway-Listener**: EIN Listener auf `gateway_port` (analog `start_proxy`,
>   aber für viele Backends), Bind ohne `SO_REUSEADDR`.
> - **Routing beim Connect**: Handshake parsen; Hostname-Feld inkl.
>   `\0FML..\0`-Marker sauber abtrennen (nur Teil vor erstem `\0`).
>   Reihenfolge: (1) exakter **Alias-Match**; (2) **Apex/unbekannter Hostname**
>   → `default_server_id` (Lobby); (3) **Versions-Fallback** (nur wenn
>   eindeutig). Kein Treffer: Login → Login-Disconnect mit Liste verfügbarer
>   Aliase; Status-Ping → generische „Netzwerk"-MOTD.
> - **Transparent forwarden + Wake** über die vorhandenen Bausteine
>   (`_forward`, `_wake_server`, `is_server_ready`).
> - **Opt-in/aus**: bei `gateway_enabled=False` startet/tut das Gateway nichts.
> - **Tests**: reine Unit-Tests für die Routing-Entscheidung (Alias-Match,
>   FML-Strip, Versions-Fallback eindeutig vs. mehrdeutig, kein Treffer) +ein
>   Socket-Test für transparentes Forwarding übers Gateway (analog
>   `tests/test_sleep_proxy.py`, `_log` mocken).
>
> Wichtig: transparentes Byte-Splicing (kein Protokoll-Eingriff). Volle
> Testsuite muss grün bleiben; bestehende Server dürfen bei ausgeschaltetem
> Gateway nicht betroffen sein. Am Ende `pytest` komplett laufen lassen.

**Akzeptanzkriterien:** Unit-Tests fürs Routing grün; Socket-Test forwardet
korrekt; Gateway aus = keine Änderung; volle Suite grün.

---

## Phase 2 — Lifecycle, Sleep-Integration & Koexistenz

**Externe To-dos:** keine.

**Prompt zum Einfügen:**

> Implementiere **Phase 2** des Gateway-Features (aufbauend auf Phase 1).
>
> - **Lifecycle** (`app/main.py`): Gateway-Listener beim Startup starten (falls
>   `gateway_enabled`) und beim Shutdown sauber stoppen.
> - **Selbstheilung**: Routing-Tabelle/Listener periodisch abgleichen (analog
>   zum Idle-Monitor-`reconcile_proxies` — z.B. denselben Tick nutzen), damit
>   Alias-/Serveränderungen ohne Neustart greifen und der Port nachgebunden
>   wird, sobald er frei ist.
> - **Koexistenz mit Sleep-Proxy**: Ein Server „hinter dem Gateway" braucht
>   **keinen** eigenen öffentlichen Sleep-Proxy mehr — Wake/Forward läuft übers
>   Gateway. Regeln sauber trennen (Gateway-Server vs. Standalone-Server mit
>   eigenem Port). Idle-Shutdown bleibt für beide.
> - **server.properties**: für Gateway-Server den internen Port schreiben
>   (bestehende `effective_server_port`-Logik erweitern/wiederverwenden).
> - **Reconcile** nach Settings-Änderungen (Alias/Toggle) triggern.
> - Tests entsprechend ergänzen; volle Suite grün.

**Akzeptanzkriterien:** Gateway startet/stoppt mit der App; Alias-Änderung wirkt
ohne Neustart (spätestens per Reconcile-Tick); Gateway-Server bekommen keinen
doppelten Listener; Suite grün.

---

## Phase 3 — UI & UX

**Externe To-dos:** keine.

**Prompt zum Einfügen:**

> Implementiere **Phase 3** (UI) des Gateway-Features.
>
> - **Servereinstellungen** (`server_detail.html` + `servers`-Router +
>   `server_service.update_server_settings`): Toggle „Über Lobby/Gateway
>   erreichbar" + Feld „Hostname-Alias" (Kleinbuchstaben/Slug, **eindeutig** —
>   Validierung mit klarer Fehlermeldung) + Toggle „Als Lobby/Default"
>   (genau **einer**; beim Setzen die Markierung anderer Server zurücksetzen).
>   Hinweis, dass ein Neustart nötig ist, wenn der Server gerade läuft.
> - **Globale Gateway-Einstellungen** (Einstellungsseite): an/aus + Port
>   anzeigen/bearbeitbar (schreibt in App-Settings/.env-Hinweis).
> - **Dashboard**: je Server den Alias/Route + Gateway-Status anzeigen;
>   Status-Ping ans Gateway beantwortet MOTD mit Liste der verfügbaren Server.
> - **Verbindungshinweis** im UI: „Verbinde dich mit `<alias>.<deinedomain>`".
> - Tests für die neuen Felder/Validierung; volle Suite grün.

**Akzeptanzkriterien:** Alias pro Server setzbar + eindeutig validiert; Gateway
global schaltbar; Dashboard zeigt Routen; Suite grün.

---

## Phase 4 — Tests, Doku & Go-Live

**Externe To-dos (jetzt relevant!):** siehe „Domain-Modell" und „Externe
Voraussetzungen" oben — konkret:
1. **DNS**: `thormakmc.de` (A) + `*.thormakmc.de` (Wildcard-A) auf die
   Server-IP; optional SRV, falls Gateway-Port ≠ 25565.
2. **Manager auf 80/443** (Reverse-Proxy + TLS), damit `thormakmc.de` im
   Browser ohne Port funktioniert.
3. **Port-Forwarding/Firewall**: 25565 + 80/443 öffnen; interne Server-Ports zu.
4. **.env**: `MCSM_GATEWAY_ENABLED=true`, `MCSM_GATEWAY_PORT=25565`.
5. Pro Server **Alias** vergeben, **einen** als **Lobby/Default** markieren,
   betroffene Server **neu starten**.
6. **Live-Test**: `thormakmc.de` → Lobby; `atm10.thormakmc.de` → ATM10 (fährt
   ggf. hoch); `seasons.thormakmc.de` → Through-the-Seasons; usw.

**Prompt zum Einfügen:**

> Implementiere **Phase 4** (Härtung + Doku) des Gateway-Features.
>
> - **Tests**: Randfälle absichern — doppelte Aliase, kein Treffer (Version &
>   Hostname), FML-Marker, langsamer Wake (Timeout→Reconnect), Legacy-Ping
>   (<1.7) wird sauber abgewiesen.
> - **Doku** (`README` + dieses Dokument aktualisieren): Wildcard-DNS-Setup,
>   Port-Forwarding, `.env`-Variablen, Verbindungsadressen, Grenzen (kein
>   Cross-Version-Hub).
> - **Verifikations-Checkliste** für den manuellen Live-Test ergänzen.
> - Volle Suite grün.

**Akzeptanzkriterien:** Randfall-Tests grün; Doku vollständig; manuelle
Checkliste vorhanden.

---

## Kurz-Checkliste „extern" (nur für Go-Live)

- [ ] DNS: `thormakmc.de` (A) + `*.thormakmc.de` (Wildcard-A) → Server-IP
- [ ] Manager auf 80/443 erreichbar (Reverse-Proxy + TLS)
- [ ] Router/Firewall: 25565 + 80/443 offen; interne Ports zu
- [ ] `.env`: `MCSM_GATEWAY_ENABLED=true`, `MCSM_GATEWAY_PORT=25565`
- [ ] Aliase gesetzt, **ein** Server als Lobby/Default markiert
- [ ] betroffene Server neu gestartet
- [ ] Live-Test: Apex → Lobby, Subdomains → jeweilige Server
