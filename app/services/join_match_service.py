"""Passt der CLIENT des Spielers ueberhaupt zum Ziel-Server?

Die uebrigen Vorab-Pruefungen (Ban, Whitelist, voll, offline) fragen nach dem SERVER.
Hier geht es um die andere Haelfte: ein Vanilla-Client kann auf einem NeoForge-Pack
nicht landen, egal wie frei der Server ist. Bisher merkte der Spieler das erst im
Disconnect-Screen - und da ist die Lobby-Verbindung schon weg.

WAS HIER SICHER ERKENNBAR IST:
- Die Loader-FAMILIE des Clients. Bukkit liest sie ueber Player#getClientBrandName(),
  der Python-Hub aus dem minecraft:brand-Paket, das ohnehin durch die Config-Phase
  laeuft. Werte: vanilla / forge / neoforge / fabric / quilt.
- Der Loader des ZIELS und ob es ein importiertes Modpack faehrt - beides steht in der DB.
- Im Hub zusaetzlich die networked Mod-IDs des Clients (NeoForge schickt sie als
  neoforge:register-Manifest). Daraus wird ein HINWEIS, nie ein Urteil.

WAS NICHT ERKENNBAR IST - und warum dieses Modul nie endgueltig ablehnt:
- Die Modpack-VERSION. Mod-IDs tragen keine Versionsnummern; ATM10 2.0.1 gegen 2.0.2
  ist auf der Leitung unsichtbar.
- Ob ein Fabric-Client die richtigen Mods hat. Fabric/Quilt kuendigen nie eine Mod-Liste an.
- Ob der Brand ehrlich ist. Er ist ein freier String vom Client; Mods wie "Rebrand"
  setzen ihn auf "vanilla", obwohl NeoForge laeuft.

DESHALB kennt Fit nur "ok" und "confirm" - es gibt bewusst KEIN "block". Ein Fehlalarm
kostet den Spieler einen zweiten Klick, niemals den Zugang. Die harten Gruende
(Ban/Whitelist/offline/voll) bleiben davon unberuehrt und unueberstimmbar.
"""

from __future__ import annotations

from dataclasses import dataclass, field

LEVEL_OK = "ok"
LEVEL_CONFIRM = "confirm"

# Notausgang: auf False setzen schaltet den gesamten Client-Abgleich ab, ohne dass
# an den Aufrufern etwas geaendert werden muss.
JOIN_MATCH_ENABLED = True

# Ab so vielen fehlenden Pack-Mods lohnt der Hinweis. Darunter ist es Rauschen
# (Server-only-Mods, Randfaelle der Namespace-Ernte).
_MISSING_MODS_THRESHOLD = 3

# Server-Typen, die JEDEN Client annehmen - dort waere jede Meldung ein Fehlalarm.
_PLUGIN_TYPES = {"vanilla", "paper", "spigot", "purpur", "folia", "bukkit", "craftbukkit"}

# Reihenfolge ist wichtig: "neoforge" muss vor "forge" stehen, sonst schluckt der
# Teilstring-Treffer "forge" jeden NeoForge-Brand.
_KNOWN_BRANDS = ("neoforge", "forge", "quilt", "fabric", "vanilla")

# Zusaetze, die Proxys an den Brand haengen ("vanilla (Velocity)").
_BRAND_SUFFIXES = (" (velocity)", " (bungeecord)", " (proxy)", " (waterfall)")

_LOADER_LABELS = {
    "neoforge": "NeoForge",
    "forge": "Forge",
    "fabric": "Fabric",
    "quilt": "Quilt",
    "vanilla": "Vanilla",
}


@dataclass(frozen=True)
class ClientInfo:
    """Was eine Lobby ueber den Client des Spielers weiss. Alles optional."""

    brand: str = ""
    mods: frozenset = field(default_factory=frozenset)
    source: str = ""


@dataclass(frozen=True)
class Fit:
    """Urteil ueber die Client-Passung. level ist nie "block" - siehe Modul-Docstring."""

    level: str = LEVEL_OK
    text: str = ""       # voller Chat-Text OHNE Farbcodes (jede Lobby faerbt selbst)
    short: str = ""      # Kurzform fuer die Menue-Lore
    note: str = ""       # Hinweis, der NIE aufhaelt
    code: str = "ok"     # maschinenlesbarer Grund fuers Audit-Log


_FIT_OK = Fit()


def normalize_brand(raw) -> str:
    """Roher Brand-String -> "neoforge"|"forge"|"fabric"|"quilt"|"vanilla"|"".

    Bewusst Teilstring- statt Gleichheitsvergleich: reale Brands sehen aus wie
    "fml,forge", "vanilla (Velocity)" oder "fabric". Unbekanntes (Lunar, Badlion,
    leer) ergibt "" - und "" heisst ueberall "keine Aussage moeglich".
    """
    if not isinstance(raw, str):
        return ""
    value = raw.strip().lower()
    for suffix in _BRAND_SUFFIXES:
        if value.endswith(suffix):
            value = value[: -len(suffix)].strip()
    if not value:
        return ""
    # "fml,forge" / "vanilla,fabric": der SPEZIFISCHERE Teil gewinnt, deshalb wird
    # der ganze String durchsucht statt nur das erste Komma-Segment.
    for known in _KNOWN_BRANDS:
        if known in value:
            return known
    return ""


def brand_family(loader: str) -> str:
    """Loader -> Familie. NeoForge ist ein Forge-Fork; Quilt ist Fabric-kompatibel.

    Innerhalb einer Familie wird NICHT abgelehnt: die Brand-Lage zwischen Forge und
    NeoForge ist je nach Version uneindeutig, und ein Fehlalarm ist teurer als ein
    verpasster Treffer.
    """
    if loader in ("forge", "neoforge"):
        return "forge"
    if loader in ("fabric", "quilt"):
        return "fabric"
    return loader or ""


def server_loader(server) -> str | None:
    """Server-Typ -> "plugin" (nimmt jeden Client) | Loader-Name | None (unbekannt)."""
    raw = str(getattr(server, "server_type", "") or "").strip().lower()
    if not raw:
        return None
    if raw in _PLUGIN_TYPES:
        return "plugin"
    if raw in ("forge", "neoforge", "fabric", "quilt"):
        return raw
    return None


def _loader_label(loader: str) -> str:
    return _LOADER_LABELS.get(loader, loader.capitalize() if loader else "?")


def shared_required_mods(server) -> frozenset:
    """Mods, die der Client fuer dieses Ziel WIRKLICH braucht.

    Der Schnitt aus zwei unvollstaendigen Mengen kuerzt beider Fehler gegeneinander weg:
    - der mods/-Ordner enthaelt auch reine SERVER-Mods, die kein Client hat;
    - das Replay-Manifest enthaelt auch die CLIENT-only-Mods dessen, der es aufnahm.
    Was in BEIDEN steht, ist verlaesslich pack-relevant.

    Leer, wenn eine der Quellen fehlt - dann schweigt die Mod-Regel.
    """
    try:
        from app.services import hub_replay_service, modpack_router_service

        installed = modpack_router_service.server_mod_ids_cached(server)
        if not installed:
            return frozenset()
        replay_path = hub_replay_service.replay_path_for(str(getattr(server, "slug", "") or ""))
        pack = hub_replay_service.replay_mod_namespaces(replay_path)
        if not pack:
            return frozenset()
        return frozenset({m.lower() for m in installed} & {m.lower() for m in pack})
    except Exception:  # noqa: BLE001 - Ernte darf den Wechsel nie stoeren
        return frozenset()


def _pack_hint(db, server) -> tuple[str, str]:
    """(Pack-Bezeichnung, Bezugsweg) - beides leer, wenn nichts Brauchbares bekannt ist."""
    try:
        from app.services import modpack_service

        return modpack_service.client_pack_hint(db, int(server.id))
    except Exception:  # noqa: BLE001
        return "", ""


def _has_modpack_state(db, server) -> bool:
    """Faehrt das Ziel ein IMPORTIERTES Modpack?

    Bewusst diese Bedingung statt "es liegen Jars im mods-Ordner": ein Fabric-Server
    mit nur Lithium/FerriteCore hat Jars, nimmt aber Vanilla-Clients problemlos an.
    Ein importiertes Pack dagegen heisst, dass der Client dasselbe Pack braucht.
    """
    try:
        from app.services import modpack_service

        return modpack_service.get_server_modpack_state(db, int(server.id)) is not None
    except Exception:  # noqa: BLE001
        return False


def evaluate_fit(db, server, client) -> Fit:
    """Passt client zu server? Liefert nie "block" - hoechstens "confirm"."""
    if not JOIN_MATCH_ENABLED or client is None or server is None or db is None:
        return _FIT_OK
    try:
        return _evaluate_fit_inner(db, server, client)
    except Exception:  # noqa: BLE001 - eine kaputte Pruefung darf niemanden aufhalten
        return _FIT_OK


def _evaluate_fit_inner(db, server, client) -> Fit:
    target = server_loader(server)

    # R0: Paper/Spigot/Vanilla nehmen jeden Client - auch modded. Nichts zu melden.
    if target is None or target == "plugin":
        return _FIT_OK

    # R1: Ohne importiertes Modpack keine Loader-Regel (Lithium-Fall, s.o.).
    if not _has_modpack_state(db, server):
        return _FIT_OK

    # R2: Kein verwertbarer Brand -> keine Aussage. Betrifft Lunar/Badlion/OptiFine
    #     und alles, was wir nicht kennen.
    client_loader = normalize_brand(client.brand)
    if not client_loader:
        return _FIT_OK

    mc_version = str(getattr(server, "mc_version", "") or "").strip()
    name = str(getattr(server, "name", "") or "Der Server")
    pack_label, pack_link = _pack_hint(db, server)

    # R3: Andere Loader-Familie -> das kann nicht klappen. Rueckfrage, keine Absage.
    if brand_family(client_loader) != brand_family(target):
        head = (f"{name} laeuft mit {_loader_label(target)}"
                + (f" {mc_version}" if mc_version else "")
                + f" - dein Client meldet sich als {_loader_label(client_loader)}.")
        parts = [head]
        if pack_label:
            parts.append(f"Du brauchst: {pack_label}")
        if pack_link:
            # Trenner, weil der Bezugsweg auch ein Satz sein kann ("In der CurseForge-App:
            # Import > Code: ..."); ohne ihn klebt er an der Pack-Bezeichnung.
            parts.append(f"- {pack_link}")
        return Fit(
            level=LEVEL_CONFIRM,
            text=" ".join(parts),
            short=f"Braucht {_loader_label(target)}" + (f" {mc_version}" if mc_version else ""),
            code="loader",
        )

    # R4: Richtige Familie, aber es fehlen etliche Pack-Mods -> Verdacht, kein Urteil.
    #     Nur der Hub kennt die Mod-Liste des Clients; Bukkit liefert sie nie.
    if client.mods:
        required = shared_required_mods(server)
        if required:
            have = {m.lower() for m in client.mods}
            missing = sorted(required - have)
            if len(missing) >= _MISSING_MODS_THRESHOLD:
                shown = ", ".join(missing[:3])
                rest = len(missing) - 3
                text = (f"Dir fehlen vermutlich {len(missing)} Mods fuer {name}: {shown}"
                        + (f" (+{rest} weitere)" if rest > 0 else "")
                        + ". Das ist nur ein Verdacht - es kann auch an reinen Servermods liegen.")
                if pack_label:
                    text += f" Passend waere: {pack_label}"
                return Fit(
                    level=LEVEL_CONFIRM,
                    text=text,
                    short=f"{len(missing)} Mods fehlen vermutlich",
                    code="mods",
                )

    return _FIT_OK


def profile_from_payload(raw):
    """Den client-Block einer Endpoint-Anfrage einlesen.

    Alles Unerwartete (fehlend, Liste, Zahl, kaputte Typen) ergibt None - und
    None schaltet den Client-Abgleich fuer diese Anfrage einfach ab.
    """
    if not isinstance(raw, dict):
        return None
    brand = raw.get("brand")
    brand = brand.strip()[:64] if isinstance(brand, str) else ""
    mods_raw = raw.get("mods")
    mods = set()
    if isinstance(mods_raw, (list, tuple)):
        for entry in mods_raw:
            if isinstance(entry, str) and entry.strip():
                mods.add(entry.strip().lower())
    source = raw.get("source")
    source = source.strip()[:16] if isinstance(source, str) else ""
    if not brand and not mods:
        return None
    return ClientInfo(brand=brand, mods=frozenset(mods), source=source)
