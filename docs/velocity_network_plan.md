# Velocity-Lobby-Netzwerk – Umsetzungsplan

Ziel (Wunsch des Betreibers): Spieler verbinden sich **einmal** mit einer Adresse,
landen in einer **Lobby** und wechseln **im Spiel** (Command / Schild / Portal) zu
den einzelnen Servern – möglichst **unabhängig von der Client-Version**.

Das ist das klassische **Proxy-Netzwerk** (BungeeCord/Velocity), **nicht** das bisher
gebaute Gateway. Das Gateway (`gateway_service.py`) ist ein Hostname-Router
(Byte-Splicer): Es verbindet einen Client mit **genau einem** Backend und kann danach
weder das Protokoll übersetzen noch den Server im Spiel wechseln. Für den o. g.
Wunsch brauchen wir einen echten Proxy: **Velocity**.

## Grundlegende Fakten (wichtig, ehrlich)

1. **Vanilla-Client ↔ Server: Protokollversion muss passen.** Ein 1.21.11-Client
   kann einen 26.2-Server **nicht** direkt betreten. Nur **ViaVersion**
   (+ViaBackwards/+ViaRewind) auf dem Proxy übersetzt Protokolle.
2. **In-Game-Serverwechsel** (Lobby → SMP) erfordert einen echten Proxy, der die
   Client-Session hält und das Backend tauscht. Das kann nur Velocity/Bungee.
3. **Offline-Mode-Pflicht + Firewall (Sicherheit!):** Hinter Velocity laufen die
   Backends in `online-mode=false`. Damit **niemand Benutzernamen fälschen** kann,
   dürfen die Backends **nur über Velocity** erreichbar sein – d. h. sie binden an
   `127.0.0.1` und werden **nicht** nach außen freigegeben. Nach außen ist **nur**
   Velocitys Port (25565) offen.
4. **26.2 + ViaVersion ist Neuland (2026).** Ob Via „1.21.11-Client ↔ 26.2-Server"
   schon übersetzen kann, ist ungewiss. **Empfehlung:** Die **Lobby läuft auf einer
   breit unterstützten stabilen Version** (z. B. aktuelles 1.21.x). 26.2 ist dann
   nur ein **Zielserver**, den 26.2-fähige Clients (nativ oder via Via) erreichen –
   nicht die Eingangstür für alle.
5. **Backends zunächst Paper/Purpur.** „Modern Forwarding" ist nativ nur in
   Paper-basierten Servern. Fabric/Forge brauchen ein Forwarding-Mod → später.

## Technische Bausteine

- **Velocity** kommt über dieselbe API wie Paper: `fill.papermc.io/v3/projects/velocity`
  (Download-Key `server:default`). Neueste **stabile** Version verwenden
  (Snapshots ausblenden). Velocity 4.x benötigt Java 25 → deckt die neue
  Auto-Java-Installation bereits ab; 3.5.x braucht Java 17+.
- **velocity.toml**: `bind = "0.0.0.0:25565"`, `player-info-forwarding-mode = "modern"`,
  `[servers]`-Tabelle aus den Netzwerk-Servern, `try = ["lobby"]`,
  `forwarding-secret-file = "forwarding.secret"`.
- **forwarding.secret**: gemeinsames Geheimnis Proxy ↔ Backends (auto-generiert).
- **Paper-Backend**: `paper-global.yml` → `proxies.velocity.{enabled:true,
  secret:<secret>, online-mode:true}` und `server.properties` → `online-mode=false`,
  `server-ip=127.0.0.1`, interner Port.
- **ViaVersion/-Backwards/-Rewind** als **Velocity-Plugins** (`velocity/plugins/`).
- **Serverwechsel im Spiel**: `/server <name>` bringt Velocity mit. Schilder/Portale/
  NPCs → optionales Lobby-Plugin auf dem Lobby-Backend (später/dokumentiert).

## Phasen

### Phase 1 – Velocity-Provisionierung & Lifecycle
- Neuer verwalteter Proxy („velocity"): Jar über fill-API laden (stabil), Java
  über Auto-Install sicherstellen, `velocity.toml` + `forwarding.secret` erzeugen.
- Start/Stop als verwalteter Prozess; Velocity wird die öffentliche Eingangstür
  (25565), sobald **Netzwerk-Modus = Velocity**.
- Einstellungen: **Netzwerk-Modus** (`Aus` | `Gateway` | `Velocity`),
  Velocity-Version, Forwarding-Secret (anzeigen/rotieren). Gateway und Velocity
  schließen sich am selben Port gegenseitig aus → klarer Umschalter.

### Phase 2 – Backend-Anbindung (Forwarding) & Sicherheit
- Pro Server: „Teil des Netzwerks" + Backend-Name. Beim Start schreibt der Manager
  automatisch `online-mode=false`, `server-ip=127.0.0.1`, internen Port und (bei
  Paper) die `paper-global.yml`-Velocity-Sektion mit dem Secret.
- `velocity.toml` `[servers]` aus den Netzwerk-Mitgliedern generieren, `try=[lobby]`.
- Sicherheits-Check/Hinweis: Backends nur lokal, ausschließlich 25565 nach außen.
- Vorerst nur Paper/Purpur-Backends; Fabric/Forge klar als „später" markiert.

### Phase 3 – Cross-Version (ViaVersion) & Lobby-UX
- Via-Plugins nach `velocity/plugins/` laden/aktualisieren (auto).
- Lobby-Kennzeichnung; `/server` nutzbar; optionales Lobby-Plugin für
  Schilder/Portale dokumentieren.
- Lobby-Basisversion (stabil, breit unterstützt) empfehlen/erzwingen; Via-Grenzen
  bei 26.2 transparent im UI anzeigen.

### Phase 4 – Sleep-Integration, Tests, Doku, Go-Live
- **Sleep-on-Demand mit Velocity** (der kniffligste Teil): Lobby bleibt an; weitere
  Server schlafen und werden beim Wechsel geweckt. Option A: bestehenden
  Sleep-Proxy zwischen Velocity und Backend behalten (Velocity zeigt auf den
  Sleep-Port, der weckt). Option B: Wake-Hook. Entwurf + Umsetzung.
- Migration/Koexistenz mit dem bestehenden Gateway; Umschalter absichern.
- Tests, Dokumentation, Live-Rollout (DNS/Firewall bleiben wie beim Gateway).

## Lobby-UX: Wie Spieler zwischen Servern wechseln (Phase 3)

- **`/server <name>`** bringt Velocity von Haus aus mit. `/server` listet alle
  Backends; `/server smp` wechselt sofort. Funktioniert ohne Zusatz-Plugin.
- **`<name>.<domain>`** verbindet dank `[forced-hosts]` direkt mit einem Backend
  (z. B. `smp.mc.friedrich-dietrich.de`).
- **Schilder / NPCs / Portale** sind KEINE Velocity-Funktion, sondern brauchen ein
  **Plugin auf dem Lobby-Backend** (die Lobby ist ein normaler Paper-Server):
  z. B. *SignServer*/*DeluxeMenus* (Klick-Schilder/Menüs) oder ein Portal-Plugin,
  das intern `/server <name>` bzw. eine BungeeCord/Velocity-Nachricht sendet.
  Solche Plugins installiert man über *Mods & Inhalte* auf dem Lobby-Server.
- **Cross-Version:** Ist *ViaVersion* aktiv (Standard), lädt der Manager
  ViaVersion/-Backwards/-Rewind automatisch nach `velocity/plugins/`. Damit
  verbinden sich Clients unterschiedlicher Versionen; Grenzen richten sich nach
  dem, was das Via-Team aktuell unterstützt (sehr neue Versionen ggf. erst später).

## Sleep-Integration im Velocity-Netzwerk (Phase 4)

Backends koennen weiter schlafen und werden beim `/server`-Wechsel automatisch
geweckt – ohne die bestehende Sleep-Mechanik neu zu erfinden:

- Ein Velocity-Backend mit **Sleep** bekommt einen Sleep-Proxy, der **nur auf
  `127.0.0.1:<server.port>`** lauscht (nach aussen dicht). Der echte Server laeuft
  auf `sleep_internal_port` (ebenfalls loopback).
- **velocity.toml** zeigt fuer dieses Backend auf `127.0.0.1:<server.port>` – also
  auf den lokalen Sleep-Proxy. Verbindet Velocity dorthin (z. B. via `/server smp`),
  weckt der Proxy den Server und leitet transparent weiter.
- Die **Lobby** bleibt idealerweise dauerhaft an (Sleep aus), damit der Einstieg
  immer sofort klappt. Andere Backends duerfen schlafen.
- `read-timeout` in velocity.toml ist erhoeht (185 s, >= Wake-Timeout 180 s), damit
  auch ein langsam (z. B. Forge) kalt startendes Backend hochkommt, bevor Velocity
  abbricht. Der **erste** Beitritt zu einem schlafenden Server kann dennoch dauern.
- `server.port` eines Sleep-Backends ist im Netzwerk nur intern (lokaler
  Sleep-Proxy-Port). Kollidiert er mit dem Velocity-Port, verschiebt der Manager ihn
  automatisch auf einen freien Port.

## Go-Live-Checkliste (Velocity)

1. **Einstellungen → Netzwerk-Modus = `velocity`** setzen. Velocity wird geladen
   (neueste stabile 3.x, Java per Auto-Install), `velocity.toml` + `forwarding.secret`
   erzeugt. ViaVersion ist standardmaessig an.
2. **Lobby festlegen:** Auf dem Lobby-Server (stabiles 1.21.x, Paper) unter
   *Einstellungen* „Teil des Velocity-Netzwerks" + Backend-Name `lobby` + „Als Lobby".
   Sleep dort **aus**.
3. **Weitere Server** analog als Netzwerk-Backend markieren (Name z. B. `smp`),
   Sleep nach Wunsch **an**. Servertyp Paper/Purpur.
4. **Neu starten:** Jeden Netzwerk-Server einmal neu starten – der Manager schreibt
   Forwarding (online-mode=false, loopback) + traegt das Backend in velocity.toml ein.
5. **Firewall/Portfreigabe:** Nach aussen **nur** den Velocity-Port (25565) an den
   Host weiterleiten. Die Backends bleiben lokal (127.0.0.1) – niemals direkt
   freigeben (sonst Username-Spoofing durch offline-mode).
6. **DNS:** Wie beim Gateway – `deine-domain` (bzw. `*.mc.domain`) auf die
   oeffentliche IP. Direktverbindung `smp.<domain>` funktioniert dank forced-hosts.
7. **Lobby-Wechsel testen:** Mit `/server smp`, `<name>.<domain>` oder einem
   Schild/Portal-Plugin auf der Lobby.

## Offene Entscheidung vor Phase 1
- **Lobby-Basisversion**: Für „jede Version rein" sollte die Lobby **nicht** 26.2
  sein, sondern eine stabile, breit von Via unterstützte 1.21.x. 26.2 wird ein
  Zielserver. (Hängt davon ab, welche Client-Versionen deine Spieler nutzen.)
