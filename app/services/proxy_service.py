"""Velocity: echter Proxy als oeffentlicher Eingang (Cross-Version-Lobby-Netzwerk).

Der Manager verwaltet Velocity als externen Java-Prozess VOR allen Servern - analog zu
``viaproxy_service`` (Popen + idempotenter Reconcile + Crash-Backoff), nur maechtiger:

- Velocity ist ein **INTERNES Backend hinter dem Gateway**: es bindet nur
  ``127.0.0.1:velocity_internal_port`` (loopback, NICHT oeffentlich). Der oeffentliche
  Eingang bleibt das Gateway (network_port); es routet Vanilla-Clients hierher.
- Mit ViaVersion + ViaBackwards + ViaRewind als **Velocity-Plugins** landet **jede**
  Vanilla-Version (1.7.10 .. 26.2) in der EINEN neuesten Lobby: der Proxy uebersetzt
  abwaerts (die reife Via-Richtung). 26.2-Clients nativ, aeltere per Backwards/Rewind.
- Backends (Lobby + Vanilla-Paper-Server) laufen hinter Velocity mit **Modern
  Forwarding** (Loopback, online-mode=false + Secret). **Modded-Server sind KEINE
  Velocity-Backends** - modded Clients laufen ueber den Dispatcher in den Python-Hub.

Gated hinter ``network_mode == "velocity"`` (UNIVERSAL-Modus). Das Gateway laeuft dabei
WEITER (Eingang); Velocity ist nur das interne Vanilla-Backend. Reconcile laeuft
idempotent im Lifespan + 15s-Idle-Monitor -> ein Moduswechsel greift ohne App-Neustart.
"""

from __future__ import annotations

import shutil
import subprocess
import time
import urllib.request
from pathlib import Path
from threading import RLock
from typing import Optional

_LOCK = RLock()
_PROC: "Optional[subprocess.Popen]" = None
_LOG: "Optional[object]" = None
_STATE: dict = {}          # zuletzt gestartete Signatur -> Idempotenz beim Reconcile
_DOWNLOAD_TRIED = False     # Auto-Download der Velocity-Jar nur einmal pro Prozesslauf
_last_start_mono = 0.0      # Zeitpunkt des letzten Start-Versuchs (Crash-Erkennung + Backoff)
_crash_reported = False     # Crash-Grund nur EINMAL pro Absturz-Episode loggen
_RETRY_AFTER_CRASH = 60.0   # nach sofortigem Absturz nicht alle 15s neu starten (kein Flapping)

_VELOCITY_API = "https://fill.papermc.io/v3/projects/velocity"
# Servertypen, die als Velocity-Backend taugen (Modern Forwarding = nur Paper-basiert).
# Alles andere (vanilla/forge/neoforge/fabric/quilt) ist ein nativer Transfer-Ziel-Server.
# Nur Paper-Familie laeuft als loopback-Backend hinter Velocity (modern forwarding). Spigot/Bukkit
# sind eigenstaendige online-mode-Server (direkt + Lobby), damit Direktverbindungen echte UUIDs geben.
_BACKEND_TYPES = {"paper", "purpur", "folia"}
_READ_TIMEOUT_MS = 185000   # >= Sleep-Wake-Timeout (180 s), damit kalt startende Backends hochkommen


def _glog(event: str, detail: str = "") -> None:
    try:
        from app.services import gateway_service

        gateway_service._glog(f"velocity.{event}", detail)
    except Exception:  # noqa: BLE001
        pass


def _work_dir() -> Path:
    from app.core.config import get_settings

    d = (get_settings().data_dir / "velocity").resolve()
    (d / "plugins").mkdir(parents=True, exist_ok=True)
    return d


def _default_jar_path() -> Path:
    return _work_dir() / "velocity.jar"


def _proxy_log_path() -> Path:
    """Pfad der velocity.log. Bevorzugt das ZENTRALE Log-Verzeichnis (``data/logs`` bzw. NAS-
    ``<root>/logs``), das per Sync-Task auf die NAS gespiegelt wird -> die Proxy-Logs liegen dann
    neben den Server-Logs auf ``\\\\FriedrichNAS\\...\\MC-manager-Logs``. Fallback: lokaler
    Velocity-Arbeitsordner (falls das zentrale Logs-Verzeichnis nicht ermittelbar ist)."""
    try:
        from app.services import central_storage_service

        d = central_storage_service.logs_dir() / "velocity"
        d.mkdir(parents=True, exist_ok=True)
        return d / "velocity.log"
    except Exception:  # noqa: BLE001 - im Zweifel im Arbeitsordner loggen
        return _work_dir() / "velocity.log"


# --- Velocity-Download (fill-API, wie PaperProvider) ---------------------------
def _resolve_velocity_download(version: str = "") -> tuple[str, str]:
    """(URL, Dateiname) des besten Velocity-Builds. ``version`` leer = neueste stabile."""
    from app.providers.server.common import fetch_json

    data = fetch_json(f"{_VELOCITY_API}/versions")
    entries = data.get("versions") if isinstance(data, dict) else data
    entries = entries if isinstance(entries, list) else []

    ver = (version or "").strip()
    if not ver:
        for entry in entries:  # neueste zuerst
            vid = str(((entry or {}).get("version") or {}).get("id") or "") if isinstance(entry, dict) else ""
            if vid and not any(t in vid.lower() for t in ("-rc", "-pre", "snapshot", "-exp")):
                ver = vid
                break
        if not ver and entries:
            ver = str(((entries[0] or {}).get("version") or {}).get("id") or "")
    if not ver:
        raise ValueError("Keine Velocity-Version ueber die fill-API gefunden.")

    builds = fetch_json(f"{_VELOCITY_API}/versions/{ver}/builds")
    blist = builds if isinstance(builds, list) else ((builds.get("builds") if isinstance(builds, dict) else None) or [])
    if not blist:
        raise ValueError(f"Keine Velocity-Builds fuer {ver} gefunden.")
    stable = [b for b in blist if str(b.get("channel") or "").upper() == "STABLE"]

    def _bid(b: dict) -> int:
        try:
            return int(b.get("id") or 0)
        except (TypeError, ValueError):
            return 0

    build = max(stable or blist, key=_bid)
    downloads = build.get("downloads") or {}
    dl = downloads.get("server:default") or (next(iter(downloads.values())) if downloads else {})
    url = str((dl or {}).get("url") or "")
    name = str((dl or {}).get("name") or "")
    if not url or not name:
        raise ValueError(f"Velocity-Build {ver} ohne Download-Link.")
    return url, name


def download_velocity_jar(dest: Path | None = None, version: str = "") -> Path | None:
    dest = dest or _default_jar_path()
    try:
        url, _name = _resolve_velocity_download(version)
        from app.providers.server.common import USER_AGENT

        tmp = dest.with_name(dest.name + ".part")
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=300) as resp, open(tmp, "wb") as fh:
            shutil.copyfileobj(resp, fh, length=1024 * 256)
        tmp.replace(dest)
        _glog("downloaded", f"{url.rsplit('/', 1)[-1]} -> {dest.name}")
        return dest
    except Exception as exc:  # noqa: BLE001
        _glog("download_failed", repr(exc))
        return None


# Fuer welche Zielversion zuletzt ein Download VERSUCHT wurde (Zeitpunkt). Der Reconcile
# laeuft alle 15 s - ohne Bremse wuerde ein fehlschlagender Download zum Dauerfeuer.
_JAR_VERSUCH: dict[str, float] = {}
_JAR_WIEDERHOLUNG_SEKUNDEN = 600.0


def _darf_erneut(version: str) -> bool:
    jetzt = time.monotonic()
    letzter = _JAR_VERSUCH.get(version)
    if letzter is not None and (jetzt - letzter) < _JAR_WIEDERHOLUNG_SEKUNDEN:
        return False
    _JAR_VERSUCH[version] = jetzt
    return True


def _jar_marke() -> Path:
    """Datei neben dem Jar, in der steht, fuer welche MC-Version es geholt wurde."""
    return _work_dir() / "velocity.jar.mcversion"


def _geholt_fuer() -> str:
    try:
        return _jar_marke().read_text(encoding="utf-8").strip()
    except OSError:
        return ""


def _merke_geholt_fuer(version: str) -> None:
    try:
        _jar_marke().write_text(version.strip(), encoding="utf-8")
    except OSError:
        pass


def _version_rang(version: str) -> tuple:
    """Vergleichbarer Rang einer MC-Version: "26.3" > "26.2" > "1.21.11".

    Zahlenweise, nicht alphabetisch - sonst waere "26.10" kleiner als "26.2".
    """
    import re

    return tuple(int(x) for x in re.findall(r"\d+", version or ""))


def _netz_version() -> str:
    """Die HOECHSTE Minecraft-Version unter den Velocity-Backends.

    Nicht die der Lobby: durch den Proxy laufen alle Paper-Backends, und ein Client der
    neuesten Version kommt gar nicht erst herein, wenn der Proxy sie nicht kennt. Eine
    Lobby auf 26.2 neben einem Server auf 26.3 hat genau das erzeugt - der Proxy wies
    26.3-Clients ab ("Incompatible client! Please use 1.7.2-26.2").
    """
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.server import Server as _Server

    try:
        with SessionLocal() as db:
            versionen = [
                str(s.mc_version or "").strip()
                for s in db.scalars(select(_Server).where(_Server.gateway_enabled.is_(True)))
                if str(s.server_type or "").lower() in _BACKEND_TYPES
            ]
    except Exception:  # noqa: BLE001 - darf den Start nie verhindern
        return ""
    versionen = [v for v in versionen if v]
    return max(versionen, key=_version_rang) if versionen else ""


# Kleinste Java-Fassung, die wir ueberhaupt verlangen (Velocity 3.x braucht mindestens 17).
_JAVA_MIN = 17
# Eine .class-Datei traegt ihre Fassung in den Bytes 6-7. 61 = Java 17, 69 = Java 25.
_KLASSE_ZU_JAVA = 44


def java_major_fuer_jar(jar: str | Path) -> int:
    """Welche Java-Hauptversion verlangt dieses Jar?

    Gelesen wird die Klassendatei-Version der Hauptklasse (aus dem Manifest). Ein fest
    verdrahtetes Mindestmass geht so lange gut, bis das Jar auf etwas Neuerem gebaut wird -
    dann startet der Prozess, wirft UnsupportedClassVersionError und stirbt, im Minutentakt,
    ohne dass irgendwo steht warum.
    """
    import re
    import zipfile

    def _aus_klasse(rohdaten: bytes) -> int | None:
        if len(rohdaten) >= 8 and rohdaten[:4] == b"\xca\xfe\xba\xbe":
            major = int.from_bytes(rohdaten[6:8], "big")
            if 45 <= major <= 99:
                return max(_JAVA_MIN, major - _KLASSE_ZU_JAVA)
        return None

    try:
        with zipfile.ZipFile(jar) as z:
            try:
                manifest = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
                treffer = re.search(r"Main-Class:\s*([\w.$]+)", manifest)
            except KeyError:
                treffer = None
            if treffer:
                pfad = treffer.group(1).strip().replace(".", "/") + ".class"
                try:
                    gefunden = _aus_klasse(z.read(pfad)[:8])
                    if gefunden:
                        return gefunden
                except KeyError:
                    pass
            # Keine Hauptklasse gefunden -> irgendeine normale Klasse nehmen. NICHT die
            # unter META-INF/versions/: das sind Zusatzfassungen fuer neuere Laufzeiten,
            # die das Jar gerade NICHT voraussetzt.
            for name in z.namelist():
                if name.endswith(".class") and not name.startswith("META-INF/"):
                    gefunden = _aus_klasse(z.read(name)[:8])
                    if gefunden:
                        return gefunden
    except Exception:  # noqa: BLE001 - im Zweifel das Mindestmass, nie abstuerzen
        pass
    return _JAVA_MIN


def _resolve_jar(cfg: dict) -> str | None:
    """Jar-Pfad: Setting -> vorhandenes Jar (bei Versionswechsel erneuert) -> Download.

    Frueher stand hier "einmalig Auto-Download": lag die Datei da, wurde sie fuer immer
    benutzt. Ein Proxy, der die Version seiner Server nicht nativ kann, laesst niemanden
    mehr herein - oder, mit ViaVersion davor, uebersetzt still und verliert dabei
    Plugin-Nachrichten. Beides sah der Manager nicht.
    """
    global _DOWNLOAD_TRIED
    jar = (cfg.get("jar") or "").strip()
    if jar and Path(jar).is_file():
        return jar          # ausdruecklich gesetzt -> der Nutzer bestimmt, nicht wir
    default = _default_jar_path()
    ziel = _netz_version()

    if default.is_file():
        if ziel and _geholt_fuer() != ziel and _darf_erneut(ziel):
            _glog("jar_outdated",
                  f"Velocity-Jar wurde fuer '{_geholt_fuer() or 'unbekannt'}' geholt, das "
                  f"Netzwerk laeuft auf '{ziel}' - lade neu.")
            if download_velocity_jar(default, cfg.get("version") or ""):
                _merke_geholt_fuer(ziel)
            else:
                _glog("jar_update_failed",
                      f"Neuladen fuer '{ziel}' fehlgeschlagen - der Proxy laeuft weiter "
                      f"mit der alten Fassung. Clients dieser Version kommen womoeglich "
                      f"nicht herein, und der Lobby-Wechsel bleibt stumm.")
        return str(default)

    if not _DOWNLOAD_TRIED:
        _DOWNLOAD_TRIED = True
        got = download_velocity_jar(default, cfg.get("version") or "")
        if got:
            _merke_geholt_fuer(ziel)
            return str(got)
    return None


# --- Konfiguration (velocity.toml + forwarding.secret + Via-Plugins) -----------
def _lobby_mc_version(db) -> str:
    """MC-Version der Lobby (fuer die passenden Via-Plugin-Builds)."""
    from sqlalchemy import select

    from app.models.server import Server as _Server

    lobby = db.scalar(select(_Server).where(_Server.gateway_is_default.is_(True)))
    return str(getattr(lobby, "mc_version", "") or "") if lobby else ""


def velocity_backends(db) -> tuple[list[dict], str | None]:
    """Velocity-Backends (Lobby + Vanilla-Paper-Server) + Name der Default-Lobby.

    Backend = gateway_enabled Server eines Bukkit-Typs (Modern Forwarding faehig).
    Adresse = ``127.0.0.1:<port>`` (Backend bindet loopback, nur ueber Velocity
    erreichbar). Modded-/Vanilla-Server sind KEINE Backends (nativer Transfer).
    """
    from sqlalchemy import select

    from app.models.server import Server as _Server
    from app.services import gateway_service

    backends: list[dict] = []
    lobby_name: str | None = None
    rows = db.scalars(select(_Server).where(_Server.gateway_enabled.is_(True))).all()
    used: set[str] = set()
    for srv in rows:
        if str(srv.server_type or "").lower() not in _BACKEND_TYPES:
            continue
        if not srv.port:
            continue
        alias = gateway_service.clean_hostname(srv.gateway_hostname) or f"srv{srv.id}"
        name = alias
        suffix = 2
        while name in used:   # eindeutige Backend-Namen erzwingen
            name = f"{alias}{suffix}"
            suffix += 1
        used.add(name)
        backends.append({
            "name": name,
            "address": f"127.0.0.1:{int(srv.port)}",
            "alias": alias,
            "is_lobby": bool(srv.gateway_is_default),
        })
        if srv.gateway_is_default:
            lobby_name = name
    if lobby_name is None and backends:
        lobby_name = backends[0]["name"]
    return backends, lobby_name


def _toml_escape(value: str) -> str:
    """Fuer einen TOML-Basic-String escapen. Steuerzeichen (Newline/Tab/CR) sind in
    Basic-Strings VERBOTEN -> escapen, sonst ist die velocity.toml ungueltig und
    Velocity startet nicht (z.B. mehrzeilige MOTD)."""
    out = str(value).replace("\\", "\\\\").replace('"', '\\"')
    out = out.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    # Restliche Steuerzeichen (<0x20) als \uXXXX kodieren.
    return "".join(ch if ch >= " " or ch in "\\\"" else f"\\u{ord(ch):04X}" for ch in out)


# Fallback, falls sich die Version nicht aus dem Jar lesen laesst.
_CONFIG_VERSION_FALLBACK = "2.8"


def velocity_config_version(jar: str | Path | None = None) -> str:
    """Die ``config-version``, die DIESES Velocity-Jar erwartet.

    Steht in der Datei eine aeltere Version, laeuft sie bei jedem Start durch Velocitys
    Migration statt direkt gelesen zu werden. Eine fest eingetragene Fremdversion laeuft
    zudem auseinander, sobald das Jar aktualisiert wird - deshalb wird sie aus der
    Vorlage im Jar gelesen (``default-velocity.toml``).
    """
    import re
    import zipfile

    pfad = Path(jar) if jar else (_work_dir() / "velocity.jar")
    try:
        with zipfile.ZipFile(pfad) as z:
            text = z.read("default-velocity.toml").decode("utf-8", "replace")
    except (OSError, KeyError, zipfile.BadZipFile):
        return _CONFIG_VERSION_FALLBACK
    treffer = re.search(r'^\s*config-version\s*=\s*"([^"]+)"', text, re.MULTILINE)
    return treffer.group(1) if treffer else _CONFIG_VERSION_FALLBACK


def _vorlage_aus_jar(jar: str | Path | None = None) -> str:
    """Die mitgelieferte default-velocity.toml aus dem Jar - oder leer."""
    import zipfile

    pfad = Path(jar) if jar else (_work_dir() / "velocity.jar")
    try:
        with zipfile.ZipFile(pfad) as z:
            return z.read("default-velocity.toml").decode("utf-8", "replace")
    except (OSError, KeyError, zipfile.BadZipFile):
        return ""


def ping_passthrough_als_abschnitt(jar: str | Path | None = None) -> bool:
    """Erwartet dieses Velocity ``[ping-passthrough]`` als Abschnitt (ab 4.x)?

    In 3.x ist es ein Schluessel mit einem Wort ("DISABLED"), in 4.x ein Abschnitt mit
    fuenf Schaltern. Die falsche Form laesst Velocity 4 beim Lesen der Konfiguration
    abbrechen - mit einem ClassCastException, der nichts ueber die Ursache verraet.
    """
    import re

    return bool(re.search(r"^\s*\[ping-passthrough\]", _vorlage_aus_jar(jar), re.MULTILINE))


def render_velocity_toml(cfg: dict, backends: list[dict], lobby_name: str | None,
                         *, forwarding_mode: str = "modern") -> str:
    """velocity.toml aus Backends + Laufzeit-Config bauen.

    ``forwarding_mode``: 'modern' (Paper-only, sicher, Secret) oder 'legacy' (BungeeCord;
    noetig sobald ein Spigot/Bukkit-Backend im Netz ist - Velocitys Modus ist global)."""
    bind_port = int(cfg["bind_port"])
    domain = (cfg.get("domain") or "").strip()
    motd = _toml_escape(cfg.get("motd") or "Willkommen im Netzwerk")

    lines: list[str] = []
    lines.append(f'config-version = "{cfg.get("config_version") or velocity_config_version()}"')
    # Loopback-Bind: Velocity ist ein INTERNES Backend hinter dem Gateway (oeffentlich ist
    # nur der Gateway-Port). Kein oeffentlicher Bind -> kein Umgehen des Routers.
    lines.append(f'bind = "127.0.0.1:{bind_port}"')
    lines.append(f'motd = "{motd}"')
    lines.append(f'show-max-players = {int(cfg.get("max_players") or 100)}')
    lines.append("online-mode = true")
    lines.append("force-key-authentication = true")
    lines.append("prevent-client-proxy-connections = false")
    if (forwarding_mode or "modern").strip().lower() == "legacy":
        # BungeeCord/legacy: kein Secret. Backends brauchen spigot.yml bungeecord=true.
        lines.append('player-info-forwarding-mode = "legacy"')
    else:
        lines.append('player-info-forwarding-mode = "modern"')
        lines.append('forwarding-secret-file = "forwarding.secret"')
    lines.append("announce-forge = false")
    # Eine neue Anmeldung ERSETZT eine alte Sitzung desselben Spielers.
    #
    # Mit false weist Velocity sie ab: "Du bist bereits mit diesem Proxy verbunden!".
    # Das trifft zwei Faelle, die beide weh tun:
    #   * Geistersitzungen. Verschwindet ein Client ohne sauberes Abmelden (Fenster zu,
    #     Netz weg), bleibt die halboffene Verbindung im Proxy stehen - gemessen ueber
    #     zwei Stunden. Der Spieler kommt bis zu einem Proxy-Neustart nicht mehr herein.
    #   * Den Rueckweg der Lobby. Ein nativer Transfer fuehrt den Client zum SELBEN Proxy
    #     zurueck; die alte Sitzung ist dabei noch einen Moment registriert.
    # Gefahrlos ist das, weil Velocity hier online-mode = true faehrt: wer sich neu
    # anmeldet, ist nachweislich derselbe Spieler. Erst ohne Anmeldepruefung waere das
    # ein Weg, andere hinauszuwerfen.
    lines.append("kick-existing-players = true")
    # Durchreichen bleibt AUS: die Status-Antwort je Server holt das Gateway direkt beim
    # gemeinten Server (siehe gateway_service), und am Apex gehoert die Netzwerk-MOTD hin.
    # Nur die SCHREIBWEISE richtet sich nach dem Jar.
    abschnittsform = cfg.get("ping_passthrough_abschnitt")
    if abschnittsform is None:
        abschnittsform = ping_passthrough_als_abschnitt()
    if not abschnittsform:
        lines.append('ping-passthrough = "DISABLED"')
    lines.append("")
    if abschnittsform:
        lines.append("[ping-passthrough]")
        for schalter in ("version", "players", "description", "favicon", "modinfo"):
            lines.append(f"{schalter} = false")
        lines.append("")
    lines.append("[servers]")
    for be in backends:
        # Namen als QUOTED key: ein Alias mit Punkt (z.B. "1.21.1") wuerde als bare key
        # zu verschachtelten TOML-Tabellen zerfallen und das Backend nie definieren.
        lines.append(f'"{_toml_escape(be["name"])}" = "{be["address"]}"')
    try_list = f'"{_toml_escape(lobby_name)}"' if lobby_name else ""
    lines.append(f"try = [{try_list}]")
    lines.append("")
    lines.append("[forced-hosts]")
    if domain:
        for be in backends:
            lines.append(f'"{_toml_escape(be["alias"])}.{_toml_escape(domain)}" = ["{be["name"]}"]')
    lines.append("")
    lines.append("[advanced]")
    lines.append(f"read-timeout = {_READ_TIMEOUT_MS}")
    # Der Dispatcher schickt 767-Vanilla-Clients per Transfer-Paket auf die vanlobby ->
    # Velocity. Ohne accepts-transfers (Velocity-Default: false) weist Velocity solche
    # transferierten Verbindungen ab -> Client: "Connection failed while transferring to
    # the server". Muss im [advanced]-Block stehen, sonst ignoriert Velocity es.
    lines.append("accepts-transfers = true")
    # Der BungeeCord-Kanal ist der Weg, auf dem die Lobby Spieler INTERN umschaltet
    # ("Connect"). Velocitys Vorgabe ist true - aber der Manager schreibt diese Datei
    # vollstaendig selbst, und was er nicht hinschreibt, haengt an einer Vorgabe, die er
    # nicht kontrolliert. Eine Zeile, die eine Unbekannte beseitigt: ohne diesen Kanal
    # verschwinden Menue-Klick und /lobby spurlos, ohne jede Meldung irgendwo.
    lines.append("bungee-plugin-message-channel = true")
    lines.append("")
    lines.append("[query]")
    lines.append("enabled = false")
    lines.append("")
    return "\n".join(lines)


def _write_runtime_config(cfg: dict) -> tuple[str, list[str], str]:
    """velocity.toml + forwarding.secret schreiben (KEIN Netzwerk).

    Rueckgabe: (Signatur, Backend-Namen, Lobby-MC-Version). Die Signatur dient der
    Idempotenz - der Via-Plugin-Download (Netzwerk) passiert NUR beim tatsaechlichen
    (Neu-)Start, nicht bei jedem 15s-Reconcile."""
    from app.db.session import SessionLocal

    wd = _work_dir()
    # forwarding.secret schreiben (der Reconcile hat es via ensure_ schon erzeugt).
    secret = (cfg.get("secret") or "").strip()
    if secret:
        (wd / "forwarding.secret").write_text(secret, encoding="utf-8")

    with SessionLocal() as db:
        backends, lobby_name = velocity_backends(db)
        # Die HOECHSTE Version im Netz, nicht die der Lobby: die Via-Builds muessen zu dem
        # passen, was der Proxy tatsaechlich bedienen soll.
        mc_version = _netz_version() or _lobby_mc_version(db)
        from app.services import server_service
        forwarding_mode = server_service.velocity_forwarding_mode(db)
    toml_text = render_velocity_toml(cfg, backends, lobby_name, forwarding_mode=forwarding_mode)
    (wd / "velocity.toml").write_text(toml_text, encoding="utf-8")

    signature = f"{toml_text}||secret={secret}"
    return signature, [be["name"] for be in backends], mc_version


def _install_via_plugins(mc_version: str) -> None:
    """Via-Plugins (Velocity-Builds) zur hoechsten Netzversion laden (best-effort).

    Das Ergebnis wird PROTOKOLLIERT. Vorher wurde es weggeworfen: fand der Manager fuer
    eine neue Minecraft-Version keinen Build, behielt er stillschweigend die alten Jars -
    und niemand erfuhr, dass der Uebersetzer dem Netz hinterherhinkt.
    """
    try:
        from app.providers.server.common import offline_mode_enabled
        from app.services import viaversion_service

        if mc_version and not offline_mode_enabled():
            ok, meldung = viaversion_service.install_velocity_plugins(
                _work_dir() / "plugins", mc_version)
            if not ok:
                _glog("via_veraltet",
                      f"{meldung} Fuer '{mc_version}' konnte kein Via-Build geladen werden - "
                      f"die vorhandenen Jars bleiben in Betrieb und koennen aelter sein als "
                      f"das Netz.")
            elif "Nicht geladen" in meldung:
                _glog("via_teilweise", meldung)
    except Exception:  # noqa: BLE001
        pass


def is_running() -> bool:
    # LOCK-FREI: nur das Prozess-Handle lesen + pollen (beides guenstig/atomar in CPython).
    # So blockiert weder der 15s-Idle-Monitor noch die /settings-Seite, waehrend ein
    # (Neu-)Start gerade Downloads erledigt.
    p = _PROC
    return p is not None and p.poll() is None


def start_velocity(cfg: dict) -> bool:
    """Velocity-Prozess starten. Idempotent: gleiche Config-Signatur -> No-op.

    WICHTIG: die potenziell langen NETZWERK-Schritte (Velocity-Jar-Download, Java-Resolve/
    -Autoinstall, Via-Plugin-Download) laufen BEWUSST OHNE ``_LOCK``. Der Lock wird nur fuer
    den schnellen Prozess-Tausch (terminate/Popen) gehalten -> der Idle-Monitor und
    HTTP-Anfragen (is_running) bleiben responsiv, auch waehrend Minuten dauernder Downloads.
    """
    global _PROC, _LOG, _STATE

    # --- 1) Alles Langsame ausserhalb des Locks (Download/Install/Config schreiben) ---
    jar = _resolve_jar(cfg)                          # ggf. Velocity-Jar-Download
    if not jar:
        _glog("jar_missing", "Velocity-Jar fehlt und Auto-Download fehlgeschlagen.")
        return False

    from app.db.session import SessionLocal
    from app.services import java_runtime_service

    # Nicht raten, sondern das Jar fragen: ein neueres Velocity braucht ein neueres Java.
    gebraucht = java_major_fuer_jar(jar)
    with SessionLocal() as db:
        java = java_runtime_service.resolve_java_binary(db, gebraucht)  # ggf. JDK-Autoinstall
    if not java:
        _glog("java_missing",
              f"Kein Java {gebraucht}+ fuer Velocity verfuegbar (Auto-Install fehlgeschlagen). "
              f"Dieses velocity.jar ist fuer Java {gebraucht} gebaut - mit einer aelteren "
              f"Laufzeit startet es, wirft UnsupportedClassVersionError und stirbt sofort.")
        return False

    signature, backend_names, mc_version = _write_runtime_config(cfg)   # nur Dateien, kein Netz
    desired = {"jar": jar, "java": java, "sig": signature}
    if is_running() and _STATE == desired:
        return True   # unveraendert -> KEIN Neustart, KEIN Plugin-Download

    _install_via_plugins(mc_version)                 # Via-Plugin-Download (nur bei Start)

    # --- 2) Nur der schnelle Prozess-Tausch unter dem Lock ---
    with _LOCK:
        if is_running() and _STATE == desired:
            return True                              # ein Parallel-Reconcile war schneller
        _stop_locked()
        wd = _work_dir()
        try:
            _LOG = open(_proxy_log_path(), "ab")  # noqa: SIM115 - Handle lebt bis stop (zentral -> NAS)
            _PROC = subprocess.Popen(
                [java, "-jar", str(Path(jar).resolve())],
                cwd=str(wd), stdout=_LOG, stderr=subprocess.STDOUT,
                stdin=subprocess.PIPE,
            )
        except OSError as exc:
            _glog("spawn_failed", repr(exc))
            _stop_locked()
            return False
        _STATE = desired
    _glog("started", f"port={cfg['bind_port']} backends={','.join(backend_names) or '-'}")
    return True


def _stop_locked() -> None:
    global _PROC, _LOG, _STATE
    if _PROC is not None:
        try:
            _PROC.terminate()
            try:
                _PROC.wait(timeout=8)
            except Exception:  # noqa: BLE001
                _PROC.kill()
        except Exception:  # noqa: BLE001
            pass
        _glog("stopped")
    if _LOG is not None:
        try:
            _LOG.close()
        except Exception:  # noqa: BLE001
            pass
    _PROC = None
    _LOG = None
    _STATE = {}


def stop_velocity() -> None:
    with _LOCK:
        _stop_locked()


def reconcile_velocity_async() -> None:
    """reconcile_velocity in einem Hintergrund-Thread anstossen.

    Fuer Aufrufer im HTTP-Request-Thread (Setup-Button / Settings-Form): der erste
    (Neu-)Start laedt ggf. Velocity-Jar + Java + Via-Plugins (Minuten) - das darf die
    Antwort nicht blockieren. Der Idle-Monitor haelt es danach selbstheilend am Leben."""
    import threading

    threading.Thread(target=reconcile_velocity, name="velocity-reconcile", daemon=True).start()


def _read_log_tail(lines: int = 20) -> str:
    try:
        data = _proxy_log_path().read_text(
            encoding="utf-8", errors="ignore").splitlines()
    except OSError:
        return ""
    return " / ".join(ln.strip() for ln in data[-lines:] if ln.strip())[:900]


def log_tail(lines: int = 80) -> str:
    """Rohes Ende von velocity.log (mehrzeilig) fuer die Diagnose-Anzeige im UI.

    Zeigt u.a. welche ViaVersion wirklich laedt und bis zu welcher Protokoll-/MC-Version
    der Proxy Clients akzeptiert ("... range 1.7.2-26.2" bzw. ViaVersion-Ladezeile)."""
    try:
        data = _proxy_log_path().read_text(
            encoding="utf-8", errors="ignore").splitlines()
    except OSError:
        return ""
    tail = [ln.rstrip() for ln in data[-lines:]]
    return "\n".join(tail)[-6000:]


def installed_via_plugins() -> list[str]:
    """Jar-Namen der aktuell im Velocity-plugins-Ordner liegenden Via-Plugins (Diagnose).

    So sieht man im UI SCHWARZ AUF WEISS, welche ViaVersion/ViaBackwards/ViaRewind-Version
    der Proxy tatsaechlich geladen hat - statt aus dem Quelltext zu raten."""
    try:
        plugins = _work_dir() / "plugins"
        return sorted(p.name for p in plugins.glob("*.jar"))
    except OSError:
        return []


def installed_velocity_version() -> str:
    """Zuletzt heruntergeladene Velocity-Version (aus .velocity-version), leer wenn unbekannt."""
    try:
        return (_work_dir() / ".velocity-version").read_text(encoding="utf-8").strip()
    except OSError:
        return ""


def restart_velocity() -> None:
    """Velocity ZWINGEND neu starten und dabei die NEUESTEN Via-Plugins frisch ziehen.

    Wichtig, weil ein Neustart der Paper-Lobby (Server #7) den SEPARATEN Velocity-Proxy
    NICHT neu startet - der Proxy laeuft sonst mit den Via-Jars weiter, die er beim ersten
    Start geladen hat (u.U. veraltet). Diese Funktion stoppt den Proxy, setzt den Crash-
    Backoff zurueck und laesst reconcile ihn frisch (inkl. Via-Neuinstallation) hochziehen.
    """
    global _last_start_mono, _crash_reported
    stop_velocity()            # setzt _STATE zurueck -> naechster start ist ein echter Neustart
    _last_start_mono = 0.0     # Backoff zuruecksetzen -> sofortiger Neustart erlaubt
    _crash_reported = False
    reconcile_velocity()       # nicht laufend -> start_velocity() -> _install_via_plugins()


def restart_velocity_async() -> None:
    """restart_velocity im Hintergrund (Via-Download + Java/Jar-Resolve dauern)."""
    import threading

    threading.Thread(target=restart_velocity, name="velocity-restart", daemon=True).start()


def reconcile_velocity() -> None:
    """Velocity-Prozess an ``network_mode == velocity`` + Config angleichen (selbstheilend).

    Crash-Erkennung wie beim ViaProxy: stirbt der Prozess sofort, wird der Grund
    (Ende von velocity.log) EINMAL in den Audit-Log gelegt und danach nur gedrosselt
    neu gestartet (kein Flapping)."""
    global _last_start_mono, _crash_reported
    from app.services import app_setting_service as s

    cfg = s.get_velocity_config_runtime()
    if not cfg["enabled"]:
        if is_running():
            stop_velocity()
        _crash_reported = False
        _last_start_mono = 0.0
        return

    # Velocity ist jetzt ein INTERNES Backend (loopback:velocity_internal_port) HINTER dem
    # Gateway - kein Kampf mehr um den network_port, das Gateway bleibt der Eingang.
    if is_running():
        _crash_reported = False
        start_velocity(cfg)   # idempotent: gleiche Signatur -> No-op, sonst Neustart
        return

    now = time.monotonic()
    if _last_start_mono and (now - _last_start_mono) < _RETRY_AFTER_CRASH:
        if not _crash_reported:
            _crash_reported = True
            tail = _read_log_tail()
            _glog("crashed",
                  f"Velocity sofort beendet/nicht gestartet. Log-Ende: {tail}" if tail
                  else "Velocity nicht gestartet - Java/Jar/Config pruefen (keine Log-Ausgabe).")
        return
    # Start VERSUCHEN und den Zeitpunkt IMMER merken (auch bei Fehlschlag) -> der 60s-
    # Backoff greift auch, wenn Jar/Java fehlen (kein Download-Hammern alle 15s).
    ok = start_velocity(cfg)
    _last_start_mono = now
    _crash_reported = False if ok else _crash_reported
