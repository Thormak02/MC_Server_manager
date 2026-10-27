"""Welten, Configs und Plugins/Mods von einem bestehenden Server auf einen anderen holen.

Der Manager kann Server bereits 1:1 duplizieren (``server_service.duplicate_server``) - das
erzeugt aber immer einen NEUEN Server mit identischem Typ und identischer Version. Hier geht
es um den anderen Fall: eine Welt (oder Configs, oder Plugins/Mods) von einem BESTEHENDEN
Server auf einen anderen BESTEHENDEN holen, auch ueber Typ- und Versionsgrenzen hinweg.

Drei Dinge machen das zu mehr als einem Ordner-Kopieren:

1. WELTSTIL. Wo die Dimensionen liegen, haengt am Server-Typ:

     getrennt  (Bukkit-Familie: Spigot/Paper/Purpur/Folia)
               ``world/`` + ``world_nether/DIM-1/`` + ``world_the_end/DIM1/``
               -> drei Ordner auf Serverebene, jeder mit eigenem level.dat

     gemeinsam (Vanilla/Forge/Fabric/NeoForge)
               ``world/`` mit ``world/DIM-1/`` und ``world/DIM1/`` darin
               -> EIN Ordner, die Dimensionen als Unterordner

   Die Nutzdaten je Dimension (region, entities, poi, data) sind in beiden Stilen gleich
   aufgebaut - der Unterschied ist allein, wo der ``DIM-1``/``DIM1``-Ordner haengt. Deshalb
   ist der Umbau ein Verschieben dieser Ordner, kein Umschreiben von Dateien.

   Ein 1:1-Kopieren von Bukkit nach Vanilla laesst Nether und End verschwinden: die Welt
   laedt, aber der Server findet die Dimensionen nicht am erwarteten Platz und generiert
   sie neu. Niemand sieht einen Fehler - nur die Bauten sind weg.

   Die 26.x-Umstellung auf ``world/dimensions/minecraft/<dim>/`` braucht hier KEINE eigene
   Behandlung: sie passiert innerhalb des Weltordners und macht Minecraft beim ersten Laden
   selbst. Was der Manager verschieben muss, ist nur die Achse getrennt <-> gemeinsam.

2. VERSIONSRICHTUNG. Minecraft migriert Weltdaten nur VORWAERTS. 26.2 -> 26.3 erledigt der
   Server selbst - die level.dat beim Start, jeden Chunk erst beim Betreten, also NICHT in
   einem Durchgang (unumkehrbar). 26.3 -> 26.2 zerstoert die
   Welt - sie laedt entweder nicht mehr oder verliert Bloecke und Entities, die es in der
   aelteren Version nicht gab. Abwaerts wird deshalb gar nicht erst kopiert.

3. SERVERSPEZIFISCHE DATEIEN. ``session.lock`` (Laufzeit-Sperre) und ``uid.dat`` (Welt-UUID,
   nur Bukkit) duerfen NICHT mitwandern: zwei Welten mit derselben UUID verwirren Plugins,
   die Welten darueber auseinanderhalten. In ``server.properties`` sind Ports, level-name,
   online-mode und die Velocity-Weiterleitung an DIESEN Server gebunden - sie bleiben beim
   Config-Transfer stehen (siehe EIGENE_PROPERTIES).

Beide beteiligten Server muessen gestoppt sein, und sie bleiben es fuer die Dauer des
Transfers: solange laufen beide IDs in der Start-Sperre von server_service.
"""

from __future__ import annotations

import gzip
import re
import shutil
import zipfile
from datetime import datetime
from pathlib import Path

from sqlalchemy.orm import Session

from app.models.server import Server

# Bukkit-Familie (fuer Plugin-Kompatibilitaet). ACHTUNG: sagt NICHTS ueber das Weltlayout -
# dafuer siehe stil_aus_typ.
BUKKIT_TYPEN = frozenset({"paper", "spigot", "bukkit", "purpur", "folia"})

# Spigot und CraftBukkit legen die Dimensionen in eigene Weltordner - unabhaengig von der
# Version. Paper und seine Forks haben das in 26.1 aufgegeben und nutzen seitdem den
# Vanilla-Stil (alle Dimensionen in EINEM Weltordner).
#
# Belegt an zwei Servern dieses Nutzers, beide Minecraft 26.2:
#   Paper 26.2  -> "Preparing level \"world\"", danach minecraft:overworld / the_nether /
#                  the_end in derselben Welt; "world_nether" kommt im Log nicht ein Mal vor.
#   Spigot 26.2 -> "World Settings For [world]", "[world_nether]", "[world_the_end]".
# Dazu Papers eigene Ankuendigung zu 26.1 ("Paper previously inherited the decade-old Bukkit
# way of storing world data") und die Migrationsdoku, die den direkten Weg Spigot -> Paper
# ab 26.1 fuer unmoeglich erklaert.
SPIGOT_LAYOUT_TYPEN = frozenset({"spigot", "bukkit"})
PAPER_VANILLA_LAYOUT_AB = "26.1"
# Mod-Loader: Mods wandern nur zwischen GLEICHEN Loadern (Forge-Mods laufen nicht auf
# NeoForge und umgekehrt - die Mod-Metadaten unterscheiden sich).
MOD_TYPEN = frozenset({"forge", "neoforge", "fabric", "quilt"})

STIL_GETRENNT = "getrennt"
STIL_GEMEINSAM = "gemeinsam"

# session.lock ist die Laufzeit-Sperre des laufenden Servers und wird neu angelegt.
#
# uid.dat wandert BEWUSST MIT: darin steht die Identitaet der Welt, und jede playerdata
# speichert genau diese UUID als die Welt, in der der Spieler zuletzt war. Ohne sie
# erscheint jeder, der im Nether oder in einer Zusatzwelt ausgeloggt war, in der Overworld -
# mit seinen alten Koordinaten, also womoeglich im Stein oder ueber der Leere. Auch Plugins,
# die Welten ueber die UUID fuehren (bei diesem Nutzer der Distant-Horizons-Support),
# verlieren sonst ihre Zuordnung.
# Damit nicht zwei Welten dieselbe Identitaet tragen, wird die uid.dat der QUELLE nach
# erfolgreichem Transfer entfernt - sie bekommt beim naechsten Start eine neue. Der Transfer
# ist damit ein Umzug, keine Kopie.
NICHT_KOPIEREN = frozenset({"session.lock"})

# Spielerbezogene Weltordner - abwaehlbar, wenn die Welt als Vorlage dienen soll.
# Minecraft 26.1 hat sie unter players/ zusammengefasst (playerdata -> players/data,
# stats -> players/stats, advancements -> players/advancements). Beide Schreibweisen
# stehen hier, damit der Haken auf alten UND neuen Welten wirkt - mit nur den alten
# Namen hatte er auf jeder 26.x-Welt gar keine Wirkung.
SPIELERDATEN_ORDNER = ("playerdata", "stats", "advancements", "players")

# Wo die Nutzdaten einer Dimension liegen koennen, relativ zum Weltordner. Bis 1.21 ist das
# DIM-1 bzw. DIM1; ab 26.x haben Mojang und Paper alles nach dimensions/<ns>/<dim>/ verschoben
# (siehe world_bake_service.overworld_region_dir). Der Stil-Umbau verschiebt diese Pfade
# zwischen Huelle und Hauptweltordner - in beiden Strukturen bleibt der relative Pfad gleich.
DIM_PFADE = {
    "nether": ("DIM-1", "dimensions/minecraft/the_nether"),
    "end": ("DIM1", "dimensions/minecraft/the_end"),
}

# Dateien, die nach dem Auslagern in einer Huelle zurueckbleiben duerfen. Alles andere sind
# Nutzdaten - bleiben die liegen, wird die Huelle NICHT geloescht, sondern abgebrochen.
# paper-world.yml gehoert dazu: Paper legt pro Welt eine an, und das ist eine Config, keine
# Weltdaten. Ohne diesen Eintrag kam ein Nutzer bei Paper -> Vanilla/Forge/Fabric gar nicht
# durch und bekam als Erklaerung nur "Daten an einem Platz, den der Manager nicht zuordnen
# kann".
HUELLE_RESTE = frozenset({"level.dat", "level.dat_old", "uid.dat", "session.lock",
                          "paper-world.yml", "paper-world-defaults.yml", "spigot.yml",
                          "bukkit.yml"})

# Weltglobale Dateien, die Paper ab 26.1 im Overworld-Dimensionsordner fuehrt, die Vanilla
# aber im data-Ordner der Weltwurzel erwartet. Wandern sie beim Wechsel zur Vanilla-Familie
# nicht mit, fallen Spielregeln, Wetter, der Wandering-Trader-Timer und die geplanten
# Ereignisse auf Standard zurueck - lautlos.
PAPER_WELTGLOBAL = ("game_rules.dat", "scheduled_events.dat", "wandering_trader.dat",
                    "weather.dat", "world_gen_settings.dat")
PAPER_WELTGLOBAL_QUELLE = "dimensions/minecraft/overworld/data/minecraft"
PAPER_WELTGLOBAL_ZIEL = "data/minecraft"

# server.properties-Schluessel, die an DIESEN Server gebunden sind und beim Config-Transfer
# unangetastet bleiben. Ports und level-name wuerden kollidieren; online-mode,
# prevent-proxy-connections und enforce-secure-profile gehoeren zum Netzwerk-Setup des
# Zielservers (Spigot laeuft nativ online, hinter Velocity gelten andere Werte); max-players
# und motd stellt der Nutzer pro Server ein.
EIGENE_PROPERTIES = frozenset({
    "server-port", "server-ip", "query.port", "rcon.port", "rcon.password",
    "enable-rcon", "enable-query", "broadcast-rcon-to-ops",
    "level-name", "level-seed",
    "online-mode", "prevent-proxy-connections", "enforce-secure-profile",
    "accepts-transfers", "accept-transfers",
    "max-players", "motd",
    # Zugangskontrolle: eine vom Quellserver mitgebrachte "white-list=false" wuerde den
    # Zielserver fuer alle oeffnen - genau die Richtung, die weh tut. Die Listen selbst
    # verwaltet global_access_service.
    "white-list", "enforce-whitelist",
    # Weltgebunden, nicht serverweit: beschreibt, wie Chunks generiert werden, und steht
    # fuer vorhandene Chunks ohnehin in level.dat (wie level-seed).
    "level-type", "generator-settings",
    # allow-nether=false der Quelle wuerde genau den Nether unerreichbar machen, den der
    # Transfer vorher aufwendig an den richtigen Platz gelegt hat. hardcore aendert die
    # Spielregeln des Zielservers grundlegend.
    "allow-nether", "allow-end", "hardcore",
})

# YAML-Pfade, die das Netzwerk-Setup DIESES Servers beschreiben. Beim Config-Transfer werden
# die Dateien uebernommen, diese Werte danach aber auf den Stand des Zielservers
# zurueckgeschrieben: ein mitgebrachtes `settings.bungeecord: true` oder ein fremdes
# Velocity-Geheimnis bricht die Anmeldung am Zielserver komplett
# (vgl. server_service.cleanup_velocity_leftovers, das genau diese Stellen aufraeumt).
NETZWERK_YAML = (
    ("spigot.yml", ("settings", "bungeecord")),
    ("config/paper-global.yml", ("proxies", "velocity", "enabled")),
    ("config/paper-global.yml", ("proxies", "velocity", "online-mode")),
    ("config/paper-global.yml", ("proxies", "velocity", "secret")),
    ("config/paper-global.yml", ("proxies", "bungee-cord", "online-mode")),
)

# Weltbezogene Werte aus server.properties: sie beschreiben, wie sich DIESE Welt spielt,
# nicht den Server. Kommt die Welt mit, sollten sie mitkommen - sonst springt die
# Schwierigkeit und dieselbe Welt fuehlt sich anders an. Sie stehen bewusst NICHT in
# EIGENE_PROPERTIES, sondern werden beim Welt-Transfer gezielt uebernommen.
WELT_PROPERTIES = ("difficulty", "gamemode", "force-gamemode", "pvp", "spawn-protection",
                   "spawn-monsters", "spawn-animals", "spawn-npcs", "allow-flight",
                   "view-distance", "simulation-distance", "max-world-size")

# Server-weite Config-Dateien (ohne server.properties, die wird gefiltert uebernommen).
CONFIG_DATEIEN = (
    "bukkit.yml", "spigot.yml", "paper.yml", "paper-global.yml", "paper-world-defaults.yml",
    "purpur.yml", "pufferfish.yml", "commands.yml", "help.yml", "permissions.yml",
    "wepif.yml", "bukkit.yml.bak",
)
# Config-Ordner: bei Paper liegen die neuen YAMLs darin, bei Forge/Fabric die Mod-Configs.
CONFIG_ORDNER = ("config",)


# ---------------------------------------------------------------------- Erkennung

def ist_bukkit(server_type: str | None) -> bool:
    return (server_type or "").strip().lower() in BUKKIT_TYPEN


def basis(server: Server) -> Path:
    return Path(server.base_path).expanduser().resolve()


def level_name(server: Server) -> str:
    """Weltname aus server.properties (``level-name``), Default ``world``."""
    return weltname_aus_ordner(Path(server.base_path).expanduser())


def weltname_aus_ordner(server_base) -> str:
    """Derselbe Weltname, aber nur aus dem Ordnerpfad - ohne Server-Objekt.

    Der Backup-Service braucht ihn in dieser Form: ``_iter_backup_files`` bekommt nur einen
    Pfad. Hart verdrahtet war dort "world", womit ein Server mit ``level-name=lobby`` ein
    Welt-Backup OHNE Welt bekam - und das faellt erst auf, wenn man es braucht.
    """
    try:
        pfad = Path(server_base) / "server.properties"
        if pfad.is_file():
            for roh in pfad.read_text(encoding="utf-8", errors="ignore").splitlines():
                zeile = roh.strip()
                if zeile.startswith("#") or not zeile.startswith("level-name="):
                    continue
                wert = zeile.split("=", 1)[1].strip()
                return wert if ist_sicherer_weltname(wert) else "world"
    except Exception:  # noqa: BLE001 - eine unlesbare Datei ist kein Grund zu scheitern
        return "world"
    return "world"


# Positivliste statt Verbotsliste: Buchstaben, Ziffern, Leerzeichen, Punkt, Minus,
# Unterstrich. Ein blosses Verbot von / und \ reicht unter Windows NICHT - "C:welt" hat
# keinen Schraegstrich, trotzdem verwirft `Path(basis) / "C:welt"` die Basis komplett und
# der Transfer wuerde ausserhalb beider Serverordner schreiben.
_WELTNAME = re.compile(r"^[A-Za-z0-9 ._-]{1,64}$")

# Windows vergibt diese Namen an Geraete - eine Datei/ein Ordner mit dem Namen (auch mit
# Endung, auch in anderer Gross-/Kleinschreibung) ist nicht anlegbar.
_GERAETENAMEN = frozenset(
    {"CON", "PRN", "AUX", "NUL", "CLOCK$"}
    | {f"COM{i}" for i in range(1, 10)}
    | {f"LPT{i}" for i in range(1, 10)}
)


def ist_sicherer_weltname(wert: str) -> bool:
    """Taugt der Wert als Ordnername direkt unter dem Serverordner?

    Nur dann darf er den Transfer steuern - ein level-name kommt aus einer Datei, die
    Nutzer mit Dateirechten bearbeiten duerfen.
    """
    wert = (wert or "").strip()
    if not _WELTNAME.match(wert):
        return False
    # Fuehrende/abschliessende Punkte und Leerzeichen: Windows schneidet sie beim Anlegen
    # still ab, der Ordner heisst dann anders als erwartet ("welt." -> "welt").
    if wert != wert.strip(" ."):
        return False
    if wert.split(".", 1)[0].upper() in _GERAETENAMEN:
        return False
    return True


def stil_aus_typ(server: Server) -> str:
    """Welches Weltlayout die Server-Software ERWARTET - Typ UND Version entscheiden.

    Die Version ist hier nicht optional: Paper 26.1 hat die Bukkit-Weltablage aufgegeben.
    Ein Paper 26.3 als "getrennt" zu behandeln hiesse, ihm Nether und End in Ordner zu
    legen, die es nicht mehr liest - die Dimensionen wuerden neu generiert.
    """
    typ = (server.server_type or "").strip().lower()
    if typ in SPIGOT_LAYOUT_TYPEN:
        return STIL_GETRENNT
    if typ in BUKKIT_TYPEN:
        from app.providers.server.common import is_version_at_least

        # is_version_at_least laesst Snapshot-Kennungen (26w14a) durch - fuer Paper-Builds
        # sind die praktisch nicht in Gebrauch, und die neuere Annahme ist die sicherere.
        return (STIL_GEMEINSAM
                if is_version_at_least(server.mc_version or "", PAPER_VANILLA_LAYOUT_AB)
                else STIL_GETRENNT)
    return STIL_GEMEINSAM


def stil_auf_platte(base: Path, name: str) -> str | None:
    """Weltstil an den vorhandenen Ordnern ablesen, oder ``None``.

    Verlaesslicher als die Ableitung aus dem Server-Typ, weil es zeigt, was der Server
    tatsaechlich geschrieben hat. ``None`` heisst "nicht entscheidbar": eine Welt ohne
    Nether und End sieht in beiden Stilen gleich aus.
    """
    getrennt = any((base / f"{name}_{rest}").is_dir() for rest in ("nether", "the_end"))
    gemeinsam = any((base / name / relativ).is_dir()
                    for pfade in DIM_PFADE.values() for relativ in pfade)
    if getrennt and not gemeinsam:
        return STIL_GETRENNT
    if gemeinsam and not getrennt:
        return STIL_GEMEINSAM
    return None


def stil(server: Server) -> str:
    """Weltstil der QUELLE: wo ihre Dimensionsdaten tatsaechlich liegen.

    Hier zaehlt die Platte, denn sie beschreibt, was kopiert werden muss. Nur wenn sich
    nichts ablesen laesst (keine Dimensionen vorhanden), entscheidet der Servertyp.
    """
    erkannt = stil_auf_platte(basis(server), level_name(server))
    return erkannt or stil_aus_typ(server)


def ziel_stil(server: Server) -> str:
    """Weltstil des ZIELS: wo seine Server-Software die Dimensionen ERWARTET.

    Hier zaehlt der Servertyp, nicht die Platte. Ein Paper-Server, in dessen Ordner noch ein
    ``world/DIM-1`` aus einer frueheren Vanilla-Installation liegt, sieht auf der Platte wie
    "gemeinsam" aus - Paper liest den Nether aber aus ``world_nether/``. Nach der Platte zu
    gehen hiesse, die Welt in eine Struktur zu bringen, die der Zielserver nicht liest:
    Nether und End wuerden neu generiert.

    Nur wenn der Typ unbekannt ist, hilft ein Blick auf die Platte weiter.
    """
    aus_typ = stil_aus_typ(server)
    typ = (server.server_type or "").strip().lower()
    if typ in BUKKIT_TYPEN or typ in MOD_TYPEN or typ == "vanilla":
        return aus_typ
    # Unbekannter Typ: dann hilft nur noch, was auf der Platte liegt.
    return stil_auf_platte(basis(server), level_name(server)) or aus_typ


def welt_ordner(server: Server) -> list[Path]:
    """Alle zur Welt gehoerenden Ordner auf Serverebene (1 bei gemeinsam, bis 3 bei getrennt)."""
    base, name = basis(server), level_name(server)
    ordner = [base / name]
    if stil(server) == STIL_GETRENNT:
        ordner += [base / f"{name}_nether", base / f"{name}_the_end"]
    return [p for p in ordner if p.is_dir()]


def data_version(welt_dir: Path) -> int | None:
    """``DataVersion`` aus ``level.dat`` - die interne Formatnummer der Welt.

    Steigt mit jeder Minecraft-Version (auch mit Snapshots) und ist damit der genauere
    Massstab als die Versionskennung in der Datenbank, die nach einem manuellen JAR-Tausch
    nicht mehr stimmen muss. ``None``, wenn die Datei fehlt oder nicht lesbar ist.
    """
    try:
        roh = gzip.decompress((welt_dir / "level.dat").read_bytes())
        wurzel = _nbt(roh)
    except Exception:  # noqa: BLE001 - eine fehlende/kaputte level.dat ist kein Fehlerfall
        return None
    daten = wurzel.get("Data") if isinstance(wurzel.get("Data"), dict) else wurzel
    wert = daten.get("DataVersion", wurzel.get("DataVersion"))
    try:
        return int(wert)
    except (TypeError, ValueError):
        return None


def _nbt(roh: bytes) -> dict:
    """NBT-Leser von world_bake_service mitbenutzen (kein zweiter Parser, keine neue
    Abhaengigkeit)."""
    from app.services.world_bake_service import read_nbt

    gelesen = read_nbt(roh)
    return gelesen if isinstance(gelesen, dict) else {}


# ------------------------------------------------------------- Pruefungen & Vorschau

def _version_tupel(kennung: str | None):
    """Versionskennung als vergleichbarer Schluessel, oder ``None`` bei Snapshots.

    Zwei Fallen, die ein reiner Tupelvergleich nicht trifft:

    * ``1.21`` und ``1.21.0`` sind DIESELBE Version, als Tupel aber ``(1,21)`` und
      ``(1,21,0)`` - und ``(1,21,0) > (1,21)``. Ein Transfer von 1.21.0 nach 1.21 waere
      sonst als Downgrade abgelehnt worden. Deshalb werden abschliessende Nullen entfernt.
    * ``1.21.4-rc1`` ist AELTER als ``1.21.4`` (DataVersion 4184 gegen 4189), nach dem
      Abschneiden des Suffix aber gleich. Deshalb traegt der Schluessel einen Rang:
      0 fuer eine Vorabversion (-pre, -rc), 1 fuer das fertige Release.

    ``26w14a`` und andere Snapshot-Kennungen lassen sich nicht ordnen -> None; dann
    entscheidet in pruefe_richtung die DataVersion.
    """
    roh = (kennung or "").strip()
    kopf, _, suffix = roh.partition("-")
    if not kopf or not kopf.replace(".", "").isdigit():
        return None
    try:
        zahlen = tuple(int(teil) for teil in kopf.split("."))
    except ValueError:
        return None
    while len(zahlen) > 1 and zahlen[-1] == 0:
        zahlen = zahlen[:-1]
    return (zahlen, 0 if suffix.strip() else 1)


def pruefe_richtung(quelle: Server, ziel: Server) -> str | None:
    """Meldung, wenn die Welt abwaerts wandern wuerde (-> Transfer verweigern), sonst ``None``.

    Erste Wahl ist die Versionskennung der beiden Server: sie beschreibt, was der Zielserver
    IST, und ist damit die richtige Grenze. Laesst sie kein Urteil zu (Snapshot-Kennungen,
    leere Felder), wird auf die DataVersion der beiden Welten ausgewichen - die steht in
    level.dat und ist das, was die Server tatsaechlich geschrieben haben.
    """
    qv, zv = _version_tupel(quelle.mc_version), _version_tupel(ziel.mc_version)
    if qv is not None and zv is not None:
        if qv > zv:
            return (f"Die Welt kommt von Minecraft {quelle.mc_version}, der Zielserver laeuft "
                    f"auf {ziel.mc_version}. Minecraft kann Welten nur hoch-, nie "
                    f"herunterrechnen - die Welt wuerde dabei zerstoert.")
        return None

    quell_welt = basis(quelle) / level_name(quelle)
    ziel_welt = basis(ziel) / level_name(ziel)
    qd, zd = data_version(quell_welt), data_version(ziel_welt)
    if qd is not None and zd is not None and qd > zd:
        return (f"Die Welt hat das neuere Weltformat {qd}, der Zielserver schreibt {zd}. "
                f"Minecraft kann Welten nur hoch-, nie herunterrechnen - die Welt wuerde "
                f"dabei zerstoert.")
    return None


def _doppelte_staende_kurz(quelle: Server) -> list[dict]:
    """Spieler mit zwei Staenden - fuer die Anzeige im Dialog."""
    from app.services import world_identity_service as wis

    try:
        return wis.doppelte_staende(basis(quelle) / level_name(quelle), basis(quelle))
    except Exception:  # noqa: BLE001 - die Vorschau darf daran nicht scheitern
        return []


def moeglichkeiten(quelle: Server, ziel: Server) -> dict:
    """Was zwischen diesen beiden Servern uebertragbar ist - Grundlage fuer die Haken im Dialog."""
    qt = (quelle.server_type or "").strip().lower()
    zt = (ziel.server_type or "").strip().lower()

    # Die Minecraft-Version zaehlt mit: Mods sind hart an sie gebunden, und Plugins, die
    # auf Server-Interna (NMS) zugreifen, brechen bei jedem Versionssprung.
    gleiche_version = (_version_tupel(quelle.mc_version) is not None
                       and _version_tupel(quelle.mc_version) == _version_tupel(ziel.mc_version))

    if ist_bukkit(qt) and ist_bukkit(zt):
        grund = "Beide Server sind Bukkit-basiert - Plugins sind kompatibel."
        if not gleiche_version:
            grund = (f"Beide Server sind Bukkit-basiert, laufen aber auf verschiedenen "
                     f"Minecraft-Versionen ({quelle.mc_version} und {ziel.mc_version}). "
                     f"Plugins, die auf Server-Interna zugreifen, brechen dabei.")
        erweiterungen = {"moeglich": True, "art": "plugins", "ordner": "plugins",
                         "grund": grund}
    elif qt in MOD_TYPEN and qt == zt:
        if not gleiche_version:
            # Nicht nur ein Schoenheitsfehler: Mods fuer die falsche Version sind ein
            # Absturz beim Start, und _uebernimm_erweiterungen loescht vorher die
            # funktionierenden JARs des Ziels.
            erweiterungen = {"moeglich": False, "art": "", "ordner": "",
                             "grund": (f"Mods sind an die Minecraft-Version gebunden - "
                                       f"{quelle.mc_version} passt nicht zu "
                                       f"{ziel.mc_version}.")}
        else:
            erweiterungen = {"moeglich": True, "art": "mods", "ordner": "mods",
                             "grund": f"Beide Server nutzen {qt} {ziel.mc_version} - Mods "
                                      f"sind kompatibel."}
    elif qt in MOD_TYPEN and zt in MOD_TYPEN:
        erweiterungen = {"moeglich": False, "art": "", "ordner": "",
                         "grund": f"{qt}-Mods laufen nicht auf {zt} (andere Mod-Schnittstelle)."}
    elif (ist_bukkit(qt) and zt in MOD_TYPEN) or (qt in MOD_TYPEN and ist_bukkit(zt)):
        erweiterungen = {"moeglich": False, "art": "", "ordner": "",
                         "grund": "Plugins und Mods sind keine austauschbaren Formate."}
    else:
        erweiterungen = {"moeglich": False, "art": "", "ordner": "",
                         "grund": "Mindestens einer der beiden Server kennt keine Plugins/Mods."}

    return {
        "welt": {"moeglich": bool(welt_ordner(quelle)),
                 "grund": "" if welt_ordner(quelle) else "Der Quellserver hat noch keine Welt."},
        "configs": {"moeglich": True, "grund": ""},
        "erweiterungen": erweiterungen,
    }


def vorschau(quelle: Server, ziel: Server) -> dict:
    """Alles, was der Dialog vor dem Transfer anzeigt: Groesse, Stilwechsel, Migration, Blocker."""
    from app.services.process_service import is_running
    from app.services.server_service import _dir_total_size

    quell_ordner = welt_ordner(quelle)
    quell_struktur, ziel_struktur = stil(quelle), ziel_stil(ziel)
    quell_welt = basis(quelle) / level_name(quelle)

    bytes_gesamt = sum(_dir_total_size(p) for p in quell_ordner)
    qd = data_version(quell_welt)
    zd = data_version(basis(ziel) / level_name(ziel))

    # Umgebaut wird nur, wenn es ueberhaupt Dimensionen zu verschieben gibt: eine Welt,
    # in der niemand im Nether war, besteht in beiden Stilen aus genau einem Ordner.
    struktur_umbau = quell_struktur != ziel_struktur and len(quell_ordner) > 1
    hinweise: list[str] = []
    if struktur_umbau:
        if ziel_struktur == STIL_GEMEINSAM:
            hinweise.append(
                "Nether und End werden in den Hauptweltordner einsortiert "
                f"({ziel.server_type} erwartet sie dort, nicht als eigene Ordner).")
        else:
            hinweise.append(
                "Nether und End werden in eigene Weltordner ausgelagert "
                f"({ziel.server_type} erwartet sie so).")
    if qd is not None and zd is not None and qd < zd:
        hinweise.append(
            f"Die Welt ist aelter als die bisherige Welt des Zielservers (Format {qd} statt "
            f"{zd}). Minecraft rechnet sie hoch - die level.dat beim Start, jeden Chunk "
            f"erst, wenn ihn jemand betritt. Danach laeuft die Welt nicht mehr auf der "
            f"alten Version.")
    elif _version_tupel(quelle.mc_version) and _version_tupel(ziel.mc_version) \
            and _version_tupel(quelle.mc_version) < _version_tupel(ziel.mc_version):
        hinweise.append(
            f"Die Welt kommt von Minecraft {quelle.mc_version} und wird beim ersten Start auf "
            f"{ziel.mc_version} hochgerechnet - nicht umkehrbar. Das passiert nicht in "
            f"einem Durchgang: die level.dat beim Start, jeder Chunk erst beim Betreten. "
            f"Ein Fehler in einem entlegenen Chunk faellt also erst spaeter auf.")

    blocker: list[str] = []
    if quelle.id == ziel.id:
        blocker.append("Quelle und Ziel sind derselbe Server.")
    for srv, rolle in ((quelle, "Quellserver"), (ziel, "Zielserver")):
        if is_running(srv.id):
            blocker.append(f"Der {rolle} laeuft noch - fuer einen konsistenten Transfer muss "
                           f"er gestoppt sein.")
        if (srv.status or "").strip().lower() == "provisioning":
            blocker.append(f"Der {rolle} wird gerade erstellt oder kopiert.")
    richtung = pruefe_richtung(quelle, ziel)
    if richtung:
        blocker.append(richtung)

    # Deckungsgleich mit dem echten Transfer bleiben: was pruefe_transfer ablehnt, muss hier
    # schon als Blocker stehen - sonst gibt der Dialog den Knopf frei und der POST scheitert
    # erst danach mit einer Fehlermeldung.
    try:
        pruefe_transfer(quelle, ziel, welt=bool(quell_ordner),
                        configs=not quell_ordner, erweiterungen=False)
    except ValueError as exc:
        meldung = str(exc)
        if meldung not in blocker:
            blocker.append(meldung)
    except Exception:  # noqa: BLE001 - die Vorschau darf an einer Pruefung nicht scheitern
        pass

    try:
        risiken = warnungen(quelle, ziel, welt=bool(quell_ordner))
    except Exception:  # noqa: BLE001 - die Vorschau darf an einer Warnung nicht scheitern
        risiken = []

    return {
        "quelle": {"id": quelle.id, "name": quelle.name, "typ": quelle.server_type,
                   "version": quelle.mc_version, "welt": level_name(quelle),
                   "stil": quell_struktur, "data_version": qd,
                   "ordner": [p.name for p in quell_ordner], "bytes": bytes_gesamt},
        "ziel": {"id": ziel.id, "name": ziel.name, "typ": ziel.server_type,
                 "version": ziel.mc_version, "welt": level_name(ziel),
                 "stil": ziel_struktur, "data_version": zd},
        "zusatzwelten": zusatz_welten(quelle),
        "doppelte_staende": _doppelte_staende_kurz(quelle),
        "moeglichkeiten": moeglichkeiten(quelle, ziel),
        "struktur_umbau": struktur_umbau,
        "hinweise": hinweise,
        "warnungen": risiken,
        "blocker": blocker,
    }


# ------------------------------------------------------------------ Bausteine

def _zielname(ordner_name: str, quell_welt: str, ziel_welt: str) -> str:
    """Weltordnername auf den level-name des Ziels umschreiben.

    ``lobby`` -> ``world``, ``lobby_nether`` -> ``world_nether``. Die Welt landet damit
    unter dem Namen, den der Zielserver in server.properties stehen hat - der bleibt
    unangetastet, damit Plugins und Bookmarks des Zielservers weiter passen.
    """
    rest = ordner_name[len(quell_welt):] if ordner_name.startswith(quell_welt) else ""
    return f"{ziel_welt}{rest}"


def _welt_filter(spielerdaten: bool, ohne_dimensionen: bool = False):
    """``skip``-Funktion fuer _robust_copy_tree: was beim Welt-Transfer nicht mitkommt.

    ``ohne_dimensionen`` laesst die Dimensions-Unterordner im Hauptweltordner weg. Das
    braucht der Fall "zwei Strukturen gleichzeitig": bei einem Spigot-Server gelten die
    eigenen Weltordner, und ein uebrig gebliebenes ``world/DIM-1`` ist ein Rest, der die
    echten Daten beim Umbau ueberschreiben wuerde.
    """
    dim_pfade = {relativ for pfade in DIM_PFADE.values() for relativ in pfade}

    def skip(rel: Path) -> bool:
        if rel.name in NICHT_KOPIEREN:
            return True
        # Nur die Ordner direkt in der Welt-Wurzel - ein "stats" tiefer im Baum gehoert
        # zu einem Datapack und bleibt drin.
        if not spielerdaten and len(rel.parts) == 1 and rel.name in SPIELERDATEN_ORDNER:
            return True
        if ohne_dimensionen:
            als_text = rel.as_posix()
            if any(als_text == p or als_text.startswith(p + "/") for p in dim_pfade):
                return True
        return False
    return skip


def _verschiebe(von_pfad: Path, nach_pfad: Path) -> None:
    nach_pfad.parent.mkdir(parents=True, exist_ok=True)
    if nach_pfad.exists():
        shutil.rmtree(nach_pfad, ignore_errors=True)
    shutil.move(str(von_pfad), str(nach_pfad))


def _huelle_reste(huelle: Path) -> list[str]:
    """Welche Nutzdaten in einer Nether-/End-Huelle zurueckgeblieben sind.

    Leere Liste heisst: alles Wichtige ist ausgezogen, die Huelle darf weg. Ist noch etwas
    drin, hat der Umbau die Dimension NICHT gefunden - dann darf die Huelle nicht geloescht
    werden, sonst waeren Nether und End stillschweigend verschwunden.
    """
    rest: list[str] = []
    for eintrag in huelle.rglob("*"):
        if eintrag.is_dir():
            continue
        if eintrag.parent == huelle and eintrag.name in HUELLE_RESTE:
            continue
        rest.append(eintrag.relative_to(huelle).as_posix())
    return rest


def _baue_stil_um(temp: Path, welt: str, von: str, nach: str) -> list[str]:
    """Dimensionsdaten zwischen eigenem Weltordner und Unterordner der Hauptwelt verschieben.

    Arbeitet ausschliesslich im Zwischenordner, bevor die Welt an ihren Platz kommt - die
    bestehende Welt des Zielservers ist zu diesem Zeitpunkt noch unberuehrt. Deshalb ist ein
    Abbruch hier gefahrlos und immer besser als ein Umbau auf Verdacht.
    """
    if von == nach:
        return []
    haupt = temp / welt
    meldungen: list[str] = []

    if nach == STIL_GEMEINSAM:
        for rest, pfade in (("nether", DIM_PFADE["nether"]), ("the_end", DIM_PFADE["end"])):
            huelle = temp / f"{welt}_{rest}"
            if not huelle.is_dir():
                continue
            bewegt: list[str] = []
            zuletzt: str | None = None
            for relativ in pfade:
                quelle = huelle / relativ
                if not quelle.is_dir():
                    continue
                _verschiebe(quelle, haupt / relativ)
                bewegt.append(relativ)
                zuletzt = relativ

            # Der data-Ordner der Huelle gehoert zur Dimension, nicht zur Huelle:
            # Bossbars, Spielregeln, geplante Ereignisse und Zufallsfolgen dieser Welt.
            # Seit 26.1 ist er zusaetzlich namespaced (data/minecraft/...). Ohne diesen
            # Schritt gilt er als "nicht zuordenbar" und bricht den Transfer ab - obwohl
            # es ganz normale Weltdaten sind.
            huellen_daten = huelle / "data"
            if huellen_daten.is_dir() and zuletzt:
                for eintrag in list(huellen_daten.iterdir()):
                    ziel_pfad = haupt / zuletzt / "data" / eintrag.name
                    if ziel_pfad.exists():
                        meldungen.append(
                            f"'{eintrag.name}' aus {huelle.name}/data uebersprungen - "
                            f"unter {zuletzt}/data liegt schon eine Datei dieses Namens.")
                        continue
                    _verschiebe(eintrag, ziel_pfad)
                shutil.rmtree(huellen_daten, ignore_errors=True)
                bewegt.append("data")
            uebrig = _huelle_reste(huelle)
            if uebrig:
                raise ValueError(
                    f"Im Weltordner '{huelle.name}' liegen Daten an einem Platz, den der "
                    f"Manager nicht zuordnen kann ({', '.join(uebrig[:4])}"
                    f"{' ...' if len(uebrig) > 4 else ''}). Die Uebernahme wurde abgebrochen, "
                    f"damit Nether und End nicht stillschweigend verloren gehen. Die "
                    f"bisherige Welt des Zielservers ist unveraendert.")
            # Bukkit fuehrt datapacks/ pro Weltordner. In der gemeinsamen Struktur gibt
            # es nur eins - also mitnehmen, was dort noch nicht liegt, statt deswegen den
            # ganzen Transfer abzubrechen.
            huellen_packs = huelle / "datapacks"
            if huellen_packs.is_dir():
                for pack in list(huellen_packs.iterdir()):
                    ziel_pack = haupt / "datapacks" / pack.name
                    if ziel_pack.exists():
                        meldungen.append(
                            f"Datapack '{pack.name}' aus {huelle.name} uebersprungen - in "
                            f"{welt}/datapacks liegt schon eines mit diesem Namen.")
                        continue
                    _verschiebe(pack, ziel_pack)
                    bewegt.append(f"datapacks/{pack.name}")
                shutil.rmtree(huellen_packs, ignore_errors=True)

            hatte_level_dat = (huelle / "level.dat").is_file()
            shutil.rmtree(huelle, ignore_errors=True)
            if bewegt:
                meldungen.append(f"{', '.join(bewegt)} nach {welt}/ einsortiert.")
            if rest == "the_end" and hatte_level_dat:
                # In der getrennten Struktur steht der Drachenkampf-Stand (Data.DragonFight:
                # besiegt?, Portal, Gateways) in der level.dat der End-Welt; in der
                # gemeinsamen Struktur gehoert er in die level.dat der Hauptwelt. Den Stand
                # umzuschreiben braeuchte einen NBT-Schreiber, den der Manager nicht hat -
                # deshalb benannt statt stillschweigend verloren.
                meldungen.append(
                    "Hinweis: Der Stand des Enderdrachen (besiegt, Portal, Gateways) wandert "
                    "bei diesem Strukturwechsel nicht mit - der Drache kann auf dem "
                    "Zielserver erneut erscheinen.")
        return meldungen

    for rest, pfade in (("nether", DIM_PFADE["nether"]), ("the_end", DIM_PFADE["end"])):
        huelle = temp / f"{welt}_{rest}"
        bewegt = []
        for relativ in pfade:
            quelle = haupt / relativ
            if not quelle.is_dir():
                continue
            # Der data-Ordner der Dimension wandert mit ihr nach aussen: in der
            # getrennten Struktur fuehrt jede Welt ihren eigenen.
            dim_daten = quelle / "data"
            _verschiebe(quelle, huelle / relativ)
            if (huelle / relativ / "data").is_dir():
                _verschiebe(huelle / relativ / "data", huelle / "data")
            bewegt.append(relativ)
        if not bewegt:
            continue
        # Bukkit erwartet in JEDEM Weltordner eine level.dat (Seed, Gamerules, Spawn);
        # welche Dimension es ist, ergibt sich aus dem Unterordner. Ohne die Datei legt der
        # Server die Welt als leere Neuwelt an.
        quell_lvl = haupt / "level.dat"
        if quell_lvl.is_file():
            shutil.copy2(quell_lvl, huelle / "level.dat")
        meldungen.append(f"{', '.join(bewegt)} nach {huelle.name}/ ausgelagert.")
    return meldungen


def _sortiere_paper_weltglobal(welt: Path, nach_vanilla: bool) -> list[str]:
    """Die weltglobalen Dateien zwischen Papers und Vanillas Ablage umsortieren.

    Paper ab 26.1 fuehrt sie unter ``dimensions/minecraft/overworld/data/minecraft/``,
    Vanilla im ``data/minecraft/`` der Weltwurzel. Das ist keine Ordner-, sondern eine
    Dateiverschiebung - sie faellt deshalb durch den Stil-Umbau hindurch.

    Arbeitet im Zwischenordner und verschiebt nur, was tatsaechlich da ist.
    """
    von_rel = PAPER_WELTGLOBAL_QUELLE if nach_vanilla else PAPER_WELTGLOBAL_ZIEL
    nach_rel = PAPER_WELTGLOBAL_ZIEL if nach_vanilla else PAPER_WELTGLOBAL_QUELLE
    bewegt: list[str] = []
    for name in PAPER_WELTGLOBAL:
        von_pfad = welt / von_rel / name
        if not von_pfad.is_file():
            continue
        nach_pfad = welt / nach_rel / name
        if nach_pfad.exists():
            continue
        nach_pfad.parent.mkdir(parents=True, exist_ok=True)
        try:
            shutil.move(str(von_pfad), str(nach_pfad))
            bewegt.append(name)
        except OSError:
            continue
    if not bewegt:
        return []
    return [f"Weltglobale Daten nach {nach_rel}/ umsortiert ({', '.join(bewegt)}) - "
            f"Spielregeln, Wetter und Generator-Einstellungen bleiben damit erhalten."]


def _uebernimm_welt_properties(quelle: Server, ziel: Server) -> list[str]:
    """Die weltbezogenen Werte aus server.properties mitnehmen (siehe WELT_PROPERTIES)."""
    from app.services.server_service import _upsert_server_property, read_server_property

    uebernommen: list[str] = []
    for schluessel in WELT_PROPERTIES:
        wert = read_server_property(quelle, schluessel)
        if wert is None or wert == read_server_property(ziel, schluessel):
            continue
        if _upsert_server_property(ziel, schluessel, wert) is None:
            uebernommen.append(f"{schluessel}={wert}")
    if not uebernommen:
        return []
    return [f"Weltbezogene Einstellungen mitgenommen: {', '.join(uebernommen)} - damit "
            f"spielt sich die Welt wie vorher."]


def _kopiere_properties(quelle: Server, ziel: Server) -> str:
    """server.properties uebernehmen - ohne die Schluessel aus EIGENE_PROPERTIES."""
    from app.services.server_service import _upsert_server_property

    quell_datei = basis(quelle) / "server.properties"
    if not quell_datei.is_file():
        return "server.properties der Quelle nicht gefunden - uebersprungen."
    uebernommen = 0
    for roh in quell_datei.read_text(encoding="utf-8", errors="ignore").splitlines():
        zeile = roh.strip()
        if not zeile or zeile.startswith("#") or "=" not in zeile:
            continue
        schluessel, wert = zeile.split("=", 1)
        schluessel = schluessel.strip()
        if not schluessel or schluessel in EIGENE_PROPERTIES:
            continue
        if _upsert_server_property(ziel, schluessel, wert.strip()) is None:
            uebernommen += 1
    return (f"server.properties: {uebernommen} Einstellungen uebernommen "
            f"(Ports, level-name, online-mode und Netzwerk bleiben beim Zielserver).")


# ------------------------------------------------------- Spieleridentitaet (UUIDs)

def forwarding_aktiv(server: Server) -> bool:
    """Haengt ein authentifizierender Proxy vor diesem Server?

    Dann vergibt NICHT der Server die UUIDs, sondern der Proxy reicht die echten
    Mojang-UUIDs durch - auch wenn in server.properties ``online-mode=false`` steht. Genau
    das ist die Konstellation eines Velocity-Backends, und wer nur online-mode liest, haelt
    so einen Server faelschlich fuer einen Offline-Server.
    """
    import yaml

    base = basis(server)
    for datei, pfad in (("config/paper-global.yml", ("proxies", "velocity", "enabled")),
                        ("spigot.yml", ("settings", "bungeecord"))):
        voll = base / datei
        if not voll.is_file():
            continue
        try:
            daten = yaml.safe_load(voll.read_text(encoding="utf-8", errors="ignore")) or {}
        except Exception:  # noqa: BLE001
            continue
        if _yaml_tiefenwert(daten, pfad) is True:
            return True
    return False


def erwartete_uuid_art(server: Server) -> str:
    """Welche Spieler-UUIDs dieser Server verwendet: ``"v4"``, ``"v3"`` oder ``"unklar"``.

    * ``v4`` - die echten Mojang-UUIDs. Bei online-mode=true, und ebenso hinter einem
      Proxy, der selbst authentifiziert.
    * ``v3`` - vom Server aus dem Spielernamen erzeugt (MD5 ueber "OfflinePlayer:<name>").
      Nur bei online-mode=false OHNE Proxy davor.
    """
    from app.services.server_service import read_server_property

    roh = (read_server_property(server, "online-mode") or "").strip().lower()
    if roh in {"true", "1"}:
        return "v4"
    if forwarding_aktiv(server):
        return "v4"
    if roh in {"false", "0"}:
        return "v3"
    return "unklar"


def _spielerdaten_pfade(welt_dir: Path) -> list[tuple[Path, str]]:
    """Wo die Spielerdateien liegen - alte und 26.1er Schreibweise."""
    return [
        (welt_dir / "playerdata", "*.dat"),
        (welt_dir / "players" / "data", "*.dat"),
        (welt_dir / "stats", "*.json"),
        (welt_dir / "players" / "stats", "*.json"),
    ]


def gemessene_uuid_art(welt_dir: Path) -> str | None:
    """Welche UUID-Art die Spielerdateien einer Welt TATSAECHLICH tragen.

    ``"v4"``, ``"v3"``, ``"gemischt"`` oder ``None`` (keine Spielerdateien vorhanden).
    Das ist die Tatsache auf der Platte - unabhaengig davon, was in irgendeiner Config
    steht, und damit der verlaesslichere der beiden Wege.
    """
    from app.services.avatar_service import ist_online_uuid

    v4 = v3 = 0
    for ordner, muster in _spielerdaten_pfade(welt_dir):
        if not ordner.is_dir():
            continue
        for datei in ordner.glob(muster):
            name = datei.stem
            if name.endswith("_old"):
                continue
            if len(name.replace("-", "")) != 32:
                continue
            if ist_online_uuid(name):
                v4 += 1
            else:
                v3 += 1
    if v4 and v3:
        return "gemischt"
    if v4:
        return "v4"
    if v3:
        return "v3"
    return None


def spieleridentitaet_warnung(quelle: Server, ziel: Server) -> str | None:
    """Warnung, wenn die Spielerdaten der Welt beim Ziel niemandem mehr zugeordnet werden.

    Die Dateien heissen ``playerdata/<uuid>.dat``. Passt die UUID-Art nicht zu dem, was
    der Zielserver verwendet, liegen die Daten vollstaendig da - nur unter Namen, nach
    denen nie gefragt wird: jeder Spieler startet mit leerem Inventar am Spawn.
    """
    gemessen = gemessene_uuid_art(basis(quelle) / level_name(quelle))
    if gemessen is None:
        return None
    if gemessen == "gemischt":
        from app.services import world_identity_service as wis

        betroffen = wis.doppelte_staende(basis(quelle) / level_name(quelle), basis(quelle))
        if not betroffen:
            return ("In der Welt liegen Spielerdaten unter beiden UUID-Arten (echte "
                    "Mojang-UUIDs und selbst erzeugte) - der Quellserver hat die "
                    "Anmeldeart im Laufe seines Lebens gewechselt. Betroffene Spieler mit "
                    "zwei Staenden konnten nicht ermittelt werden (die usercache.json "
                    "kennt sie nicht mehr).")
        namen = ", ".join(
            f"{e['name']} (es gilt der Stand vom "
            f"{e['echt']['datum'] if e['juenger'] == 'echt' else e['offline']['datum']})"
            for e in betroffen[:5])
        return (f"{len(betroffen)} Spieler haben zwei Staende, weil der Quellserver die "
                f"Anmeldeart gewechselt hat: {namen}"
                f"{' und weitere' if len(betroffen) > 5 else ''}. Der Manager nimmt jeweils "
                f"den ZULETZT GESPIELTEN Stand; der andere wird nicht geloescht, sondern "
                f"als '<uuid>.dat.ersetzt' daneben aufgehoben - falls die Wahl falsch war, "
                f"laesst sie sich von Hand umkehren.")

    erwartet = erwartete_uuid_art(ziel)
    if erwartet == "unklar" or erwartet == gemessen:
        return None

    if gemessen == "v3":
        herkunft = ("Die Spielerdaten der Welt tragen selbst erzeugte UUIDs - der "
                    "Quellserver lief ohne Mojang-Anmeldung")
        zielart = "echte Mojang-UUIDs"
    else:
        herkunft = ("Die Spielerdaten der Welt tragen echte Mojang-UUIDs - der Quellserver "
                    "hat bei Mojang angemeldet (oder hing hinter einem Proxy)")
        zielart = "selbst erzeugte UUIDs"
    return (f"{herkunft}, '{ziel.name}' verwendet aber {zielart}. Inventare, Positionen, "
            f"Statistiken und Fortschritte kommen mit, werden dort aber niemandem "
            f"zugeordnet: jeder startet mit leerem Inventar am Spawn. Die Weltbauten sind "
            f"davon nicht betroffen.")


# ------------------------------------------------------------------- Warnungen

def warnungen(quelle: Server, ziel: Server, *, welt: bool) -> list[str]:
    """Risiken, die den Transfer nicht verhindern, aber vorher bekannt sein muessen."""
    qt = (quelle.server_type or "").strip().lower()
    zt = (ziel.server_type or "").strip().lower()
    liste: list[str] = []

    # Paper sagt selbst: "Paper does not support migration to either CraftBukkit or Spigot!
    # ... note that data loss is possible." Die Richtung ist also NICHT das Gegenstueck zu
    # Spigot -> Paper, auch wenn beide Typen zur Bukkit-Familie gehoeren.
    if welt and qt in {"paper", "purpur", "folia"} and zt in SPIGOT_LAYOUT_TYPEN:
        liste.append(
            f"Der Weg von {quelle.server_type} zurueck zu {ziel.server_type} ist von Paper "
            f"ausdruecklich NICHT unterstuetzt - Datenverlust ist laut Hersteller moeglich. "
            f"Bitte vorher ein eigenes Backup anlegen, auf das du dich verlassen kannst.")

    if welt and hat_zwei_strukturen(quelle):
        liste.append(_zwei_strukturen_meldung(quelle, basis(quelle), level_name(quelle)))

    if welt and qt in MOD_TYPEN and (zt in BUKKIT_TYPEN or zt == "vanilla"):
        liste.append(
            f"Die Welt kommt von einem Server mit Mods, '{ziel.name}' laeuft ohne. Bloecke "
            f"und Gegenstaende aus Mods verschwinden beim Laden still (Mod-Bloecke werden zu "
            f"Luft, Mod-Items aus Kisten und Inventaren fallen weg), und Mod-Dimensionen "
            f"sind nicht erreichbar. Die Chunk-Dateien belegen trotzdem weiter Platz.")

    if welt and qt in MOD_TYPEN and zt in MOD_TYPEN and qt != zt:
        liste.append(
            f"Die Welt wechselt von {quelle.server_type} auf {ziel.server_type}. Der "
            f"Vanilla-Anteil laeuft weiter, aber alles aus Mods, die es auf dem anderen "
            f"Loader nicht gibt, verschwindet beim Laden - ohne Meldung.")

    if welt and qt in {"forge", "neoforge"} and zt in {"forge", "neoforge"}:
        liste.append(
            "Forge und NeoForge speichern pro Welt eine Zuordnung von Mod-Inhalten zu "
            "internen Nummern. Fehlen auf dem Zielserver Mods, die in der Welt vorkommen, "
            "meldet der Loader beim Start fehlende Registry-Eintraege und entfernt sie beim "
            "naechsten Speichern endgueltig aus der Welt. Vor dem ersten Start pruefen, ob "
            "die Mod-Liste uebereinstimmt.")

    if welt:
        from app.services.server_service import read_server_property

        for schluessel, dimension in (("allow-nether", "Nether"),
                                      ("allow-end", "End")):
            if (read_server_property(ziel, schluessel) or "").strip().lower() == "false":
                liste.append(
                    f"'{ziel.name}' hat {schluessel}=false - das {dimension} ist dort "
                    f"abgeschaltet. Die Dateien kommen an, aber das Portal fuehrt nirgendwo "
                    f"hin, bis der Wert umgestellt ist.")

    if welt:
        identitaet = spieleridentitaet_warnung(quelle, ziel)
        if identitaet:
            liste.append(identitaet)

    # Beim Zusammenlegen in einen Weltordner verlieren Nether und End ihre eigene level.dat.
    if welt and stil(quelle) == STIL_GETRENNT and ziel_stil(ziel) == STIL_GEMEINSAM:
        base, name = basis(quelle), level_name(quelle)
        if any((base / f"{name}_{rest}" / "level.dat").is_file()
               for rest in ("nether", "the_end")):
            liste.append(
                "ALLES GEBAUTE BLEIBT: Nether und End werden nicht neu erzeugt, sondern "
                "mitsamt ihren Chunk-Dateien uebernommen. Was ihr gebaut oder erkundet "
                "habt, ist danach unveraendert da. "
                "Was sich aendert: in der getrennten Struktur hat jede Dimension eigene "
                "Werte fuer Seed, Weltgrenze, Spielregeln, Zeit und Wetter; nach dem "
                "Zusammenlegen gelten die der Hauptwelt. Spuerbar ist das nur bei "
                "Gelaende, das NOCH NIE betreten wurde - dort kann an der Grenze des "
                "Erkundeten eine sichtbare Naht entstehen, weil neue Chunks mit dem Seed "
                "der Hauptwelt erzeugt werden.")

    return liste


# ------------------------------------------------- Spieleridentitaet nahtlos umstellen

# Die beim Welt-Transfer ermittelte UUID-Zuordnung, bis die Dateien im Serverordner
# nachgezogen sind. Nach Ziel-Server-ID, weil immer nur ein Transfer je Ziel laeuft
# (try_lock_servers_for_file_copy sorgt dafuer).
_LETZTE_ZUORDNUNG: dict[int, dict[str, str]] = {}


def _mojang_uuid(name: str) -> str | None:
    """Echte Minecraft-UUID zu einem Namen - ueber denselben Weg wie der Avatar-Dienst."""
    import uuid as _uuid

    from app.services.avatar_service import _uuid_zu_name

    try:
        roh = _uuid_zu_name(name, 6.0)
    except Exception:  # noqa: BLE001 - ohne Netz bleibt der Spieler unzugeordnet
        return None
    kompakt = str(roh or "").replace("-", "").strip()
    if len(kompakt) != 32:
        return None
    try:
        return str(_uuid.UUID(kompakt))
    except ValueError:
        return None


def _stelle_server_daten_um(quelle: Server, ziel: Server,
                            zuordnung: dict[str, str]) -> list[str]:
    """Spieler-UUIDs AUSSERHALB der Welt umstellen: Zugangslisten und Plugin-Daten.

    Laeuft erst, wenn die Welt schon an ihrem Platz ist - diese Dateien liegen im
    Serverordner, nicht in der Welt, und haengen nicht am Zwischenordner.
    """
    from app.services import player_data_service as pds

    if not zuordnung:
        return []
    meldungen = pds.stelle_zugangslisten_um(basis(ziel), zuordnung)
    meldungen += pds.stelle_plugin_daten_um(basis(ziel), zuordnung)
    return meldungen


def _passe_identitaet_an(quelle: Server, ziel: Server, welt: Path,
                         weitere: list[Path] | None = None) -> list[str]:
    """Spieler-UUIDs der Welt auf das Anmeldeverfahren des Ziels umstellen.

    Laeuft im Zwischenordner, bevor die Welt an ihren Platz kommt. Umgestellt werden nicht
    nur die Dateinamen der Spielerdaten, sondern auch die Vorkommen IN den Weltdaten:
    Haustier-Besitzer, Dorfbewohner-Ruf, geworfene Gegenstaende. Ohne das gehoert nach dem
    Umzug kein Tier mehr jemandem und jeder Handelsrabatt ist zurueckgesetzt.

    ``weitere`` sind die Nether-/End-Huellen. Die muessen MIT durchlaufen werden: in der
    getrennten Bukkit-Struktur liegen sie NEBEN dem Hauptweltordner, nicht darin - sonst
    behaelt ein Pferd im Nether seinen alten Besitzer, waehrend es in der Overworld
    umgestellt wurde. In der echten Welt dieses Nutzers stecken dort 65 bzw. 8 Vorkommen.

    Die Zuordnung wird nur EINMAL gebaut (sie kostet eine Mojang-Abfrage pro Spieler) und
    dann auf alle Ordner angewendet.
    """
    from app.services import world_identity_service as wis

    ziel_art = erwartete_uuid_art(ziel)
    if ziel_art == "unklar":
        return ["Spieleridentitaet nicht angepasst: beim Zielserver laesst sich nicht "
                "feststellen, ob er echte Minecraft-UUIDs verwendet (server.properties "
                "fehlt oder ist unvollstaendig)."]

    # Die Spielerdateien und damit die Kandidaten stehen im Hauptweltordner.
    zuordnung, offen = wis.baue_zuordnung(welt, basis(quelle), ziel_art,
                                          namen_aufloeser=_mojang_uuid)
    # Fuer die Dateien im Serverordner merken (Zugangslisten, Plugin-Daten).
    _LETZTE_ZUORDNUNG[ziel.id] = dict(zuordnung)
    meldungen: list[str] = []
    if zuordnung:
        gesamt: dict = {"dateien": 0, "chunks": 0, "dat": 0, "aufgeloest": []}
        for ordner in [welt] + [p for p in (weitere or []) if p.is_dir()]:
            teil = wis.schreibe_welt_um(ordner, zuordnung)
            for schluessel in ("dateien", "chunks", "dat"):
                gesamt[schluessel] += teil[schluessel]
            gesamt["aufgeloest"] += teil.get("aufgeloest") or []
        if gesamt.get("aufgeloest"):
            meldungen.append(
                f"{len(gesamt['aufgeloest'])} Spieler hatten zwei Staende - es gilt "
                f"jeweils der zuletzt gespielte, der andere liegt als '.ersetzt' daneben.")
        meldungen.append(
            f"Spieleridentitaet auf {len(zuordnung)} Spieler umgestellt "
            f"({'echte Minecraft-UUIDs' if ziel_art == 'v4' else 'serverseitig erzeugte UUIDs'}): "
            f"{gesamt['dateien']} Spielerdatei(en) umbenannt, {gesamt['chunks']} Chunk(s) "
            f"und {gesamt['dat']} Weltdatei(en) angepasst - ueber alle Dimensionen hinweg, "
            f"damit Haustiere, Dorfbewohner-Ruf und Inventare zugeordnet bleiben.")
    if offen:
        meldungen.append(
            f"Fuer {len(offen)} Spieler liess sich die neue UUID nicht bestimmen: "
            f"{', '.join(offen[:4])}{' ...' if len(offen) > 4 else ''}. Ihre Inventare und "
            f"Haustiere bleiben unzugeordnet.")
    return meldungen


# ----------------------------------------------------------- Weitere Welten je Server

def zusatz_welten(server: Server) -> list[str]:
    """Namen weiterer Welten im Serverordner, ohne ihre Nether-/End-Huellen.

    Multiverse und aehnliche Plugins legen beliebig viele Welten an - Creative, Minispiele,
    Baumwelten. Erkannt werden sie an der level.dat: ein Ordner mit level.dat ist eine
    Welt, plugins/, mods/, logs/ und cache/ sind keine.
    """
    base, haupt = basis(server), level_name(server)
    try:
        alle = {e.name for e in base.iterdir()
                if e.is_dir() and not e.name.startswith(".")
                and (e / "level.dat").is_file()}
    except OSError:
        return []
    kanonisch = {haupt, f"{haupt}_nether", f"{haupt}_the_end"}
    namen: list[str] = []
    for name in sorted(alle):
        if name in kanonisch:
            continue
        # Die Huelle einer anderen Welt ist keine eigene Welt.
        if name.endswith("_nether") and name[:-len("_nether")] in alle:
            continue
        if name.endswith("_the_end") and name[:-len("_the_end")] in alle:
            continue
        namen.append(name)
    return namen


def _uebernimm_zusatzwelten(quelle: Server, ziel: Server, *, spielerdaten: bool,
                            stempel: str, melde) -> list[str]:
    """Weitere Welten des Quellservers mitnehmen - jede mit demselben Struktur-Umbau.

    Die Namen bleiben, wie sie sind: eine Welt "creative" heisst auf dem Zielserver auch
    "creative", sonst finden die Plugins sie nicht mehr.
    """
    from app.services.server_service import _dir_total_size, _robust_copy_tree

    namen = zusatz_welten(quelle)
    if not namen:
        return []

    quell_base, ziel_base = basis(quelle), basis(ziel)
    quell_struktur, ziel_struktur = stil(quelle), ziel_stil(ziel)
    meldungen: list[str] = []
    uebernommen: list[str] = []

    for name in namen:
        gruppe = [quell_base / name]
        if quell_struktur == STIL_GETRENNT:
            gruppe += [quell_base / f"{name}_nether", quell_base / f"{name}_the_end"]
        gruppe = [p for p in gruppe if p.is_dir()]
        if not gruppe:
            continue

        temp = ziel_base / f".zusatzwelt_{stempel}_{name}"
        if temp.exists():
            shutil.rmtree(temp, ignore_errors=True)
        temp.mkdir(parents=True, exist_ok=True)
        try:
            for ordner in gruppe:
                _robust_copy_tree(ordner, temp / ordner.name,
                                  skip=_welt_filter(spielerdaten))
            meldungen += _baue_stil_um(temp, name, quell_struktur, ziel_struktur)
            if spielerdaten:
                meldungen += _passe_identitaet_an(
                    quelle, ziel, temp / name,
                    weitere=[temp / f"{name}_nether", temp / f"{name}_the_end"])
            beiseite = _tausche_welt(ziel_base, name, temp, f"{stempel}_{name}")
        except Exception as exc:  # noqa: BLE001 - eine Zusatzwelt darf den Transfer nicht kippen
            shutil.rmtree(temp, ignore_errors=True)
            meldungen.append(f"Weitere Welt '{name}' NICHT uebernommen: {exc}")
            continue
        shutil.rmtree(temp, ignore_errors=True)
        for weg in beiseite:
            shutil.rmtree(weg, ignore_errors=True)
        uebernommen.append(name)

    if uebernommen:
        meldungen.insert(0, f"{len(uebernommen)} weitere Welt(en) uebernommen: "
                            f"{', '.join(uebernommen)}.")
    return meldungen


# ------------------------------------------- Plugin-Daten, die Welten beim Namen nennen

def _passe_plugin_weltnamen_an(ziel_base: Path, quell_welt: str,
                               ziel_welt: str) -> list[str]:
    """Plugin-Daten mit umbenennen, die eine Welt ueber ihren NAMEN adressieren.

    WorldGuard-Regionen, Claims, Warps, Shops und viele andere liegen unter
    ``plugins/<Plugin>/worlds/<weltname>/`` oder direkt ``plugins/<Plugin>/<weltname>/``.
    Wird die Welt beim Transfer auf den level-name des Ziels umbenannt, zeigen diese Daten
    ins Leere: die Regionen sind praktisch weg, obwohl die Dateien noch da sind.

    Zusaetzlich die YAML-Schluessel ``world-settings.<name>`` (spigot.yml) und
    ``worlds.<name>`` (bukkit.yml).
    """
    import yaml

    if quell_welt == ziel_welt:
        return []

    paare = [(quell_welt, ziel_welt),
             (f"{quell_welt}_nether", f"{ziel_welt}_nether"),
             (f"{quell_welt}_the_end", f"{ziel_welt}_the_end")]
    plugins = ziel_base / "plugins"
    angepasst: list[str] = []

    if plugins.is_dir():
        for plugin in sorted(p for p in plugins.iterdir() if p.is_dir()):
            for eltern in (plugin / "worlds", plugin):
                if not eltern.is_dir():
                    continue
                for alt_name, neu_name in paare:
                    alt_pfad = eltern / alt_name
                    if not alt_pfad.is_dir():
                        continue
                    neu_pfad = eltern / neu_name
                    if neu_pfad.exists():
                        continue
                    try:
                        alt_pfad.rename(neu_pfad)
                        angepasst.append(str(neu_pfad.relative_to(ziel_base)).replace("\\", "/"))
                    except OSError:
                        continue

    for datei, wurzel in (("spigot.yml", "world-settings"), ("bukkit.yml", "worlds")):
        voll = ziel_base / datei
        if not voll.is_file():
            continue
        try:
            daten = yaml.safe_load(voll.read_text(encoding="utf-8", errors="ignore")) or {}
            knoten = daten.get(wurzel)
            if not isinstance(knoten, dict):
                continue
            geaendert = False
            for alt_name, neu_name in paare:
                if alt_name in knoten and neu_name not in knoten:
                    knoten[neu_name] = knoten.pop(alt_name)
                    geaendert = True
            if geaendert:
                voll.write_text(yaml.safe_dump(daten, sort_keys=False, allow_unicode=True),
                                encoding="utf-8")
                angepasst.append(f"{datei} ({wurzel})")
        except Exception:  # noqa: BLE001
            continue

    if not angepasst:
        return []
    return [f"Weltname in Plugin-Daten mitgezogen ('{quell_welt}' -> '{ziel_welt}'): "
            f"{', '.join(angepasst[:6])}{' ...' if len(angepasst) > 6 else ''}."]


# ------------------------------------------------------------------- Vorpruefung

def hat_zwei_strukturen(server: Server) -> bool:
    """Liegen die Dimensionen gleichzeitig in beiden Strukturen?

    Passiert nach einem Wechsel des Servertyps (Vanilla -> Spigot) oder einem Umzug: die
    alte Struktur bleibt als Rest liegen. Das ist KEIN Grund, den Transfer zu verweigern -
    welche Struktur gilt, sagt der Servertyp eindeutig. Der Rest wird beim Kopieren
    ausgelassen und die Sache gemeldet.
    """
    base, name = basis(server), level_name(server)
    getrennt_da = any((base / f"{name}_{rest}").is_dir()
                      for rest in ("nether", "the_end"))
    gemeinsam_da = any((base / name / relativ).is_dir()
                       for pfade in DIM_PFADE.values() for relativ in pfade)
    return getrennt_da and gemeinsam_da


def _zwei_strukturen_meldung(quelle: Server, base: Path, name: str) -> str:
    """Erklaeren, welche Struktur genommen wird und welche liegen bleibt.

    Der Servertyp entscheidet: Spigot und CraftBukkit lesen die eigenen Weltordner,
    Vanilla, Forge, Fabric und Paper ab 26.1 die Unterordner. Groesse und Datum beider
    Strukturen stehen dabei, damit die Einschaetzung nachpruefbar ist und nicht geglaubt
    werden muss.
    """
    from datetime import datetime

    from app.services.server_service import _dir_total_size

    def _angabe(pfade: list[Path]) -> str:
        vorhanden = [p for p in pfade if p.is_dir()]
        if not vorhanden:
            return "nicht vorhanden"
        bytes_gesamt = sum(_dir_total_size(p) for p in vorhanden)
        try:
            juengste = max(p.stat().st_mtime for p in vorhanden)
            datum = datetime.fromtimestamp(juengste).strftime("%d.%m.%Y")
        except OSError:
            datum = "unbekannt"
        if bytes_gesamt >= 1024 ** 3:
            groesse = f"{bytes_gesamt / (1024 ** 3):.1f} GB"
        elif bytes_gesamt >= 1024 ** 2:
            groesse = f"{bytes_gesamt // (1024 ** 2)} MB"
        else:
            # "0 MB" waere irrefuehrend - gerade bei der Altlast ist "fast leer" die
            # entscheidende Information.
            groesse = f"{max(1, bytes_gesamt // 1024)} KB"
        return f"{groesse}, zuletzt geaendert {datum}"

    eigene = [base / f"{name}_nether", base / f"{name}_the_end"]
    innere = [base / name / relativ
              for pfade in DIM_PFADE.values() for relativ in pfade]

    erwartet_getrennt = stil_aus_typ(quelle) == STIL_GETRENNT
    if erwartet_getrennt:
        gilt = f"die eigenen Ordner '{name}_nether'/'{name}_the_end'"
        altlast = f"die Unterordner in '{name}'"
    else:
        gilt = f"die Unterordner in '{name}'"
        altlast = f"die eigenen Ordner '{name}_nether'/'{name}_the_end'"

    return (
        f"'{quelle.name}' hat die Dimensionen gleichzeitig in zwei Strukturen. "
        f"Eigene Ordner: {_angabe(eigene)}. Unterordner in '{name}': {_angabe(innere)}. "
        f"Weil der Server {quelle.server_type} {quelle.mc_version} ist, gelten dort {gilt} - "
        f"nur die werden uebernommen. {altlast[:1].upper()}{altlast[1:]} sind ein Rest "
        f"aus einer "
        f"frueheren Installation und bleiben zurueck. Falls das nicht stimmt, den Transfer "
        f"abbrechen und die Ordner erst im Dateimanager bereinigen.")


def pruefe_transfer(quelle: Server, ziel: Server, *, welt: bool, configs: bool,
                    erweiterungen: bool, zusatzwelten: bool = False,
                    zugangslisten: bool = False) -> None:
    """Alles, was VOR dem ersten Kopieren scheitern muss. Wirft ValueError mit klarer Meldung."""
    from app.services.process_service import is_running

    if quelle.id == ziel.id:
        raise ValueError("Quelle und Ziel sind derselbe Server.")
    if not (welt or configs or erweiterungen or zusatzwelten or zugangslisten):
        raise ValueError("Es wurde nichts zum Uebernehmen ausgewaehlt.")

    for srv, rolle in ((quelle, "Quellserver"), (ziel, "Zielserver")):
        if (srv.status or "").strip().lower() == "provisioning":
            raise ValueError(f"Der {rolle} wird gerade erstellt oder kopiert - bitte warten.")
        if is_running(srv.id):
            raise ValueError(f"Bitte den {rolle} zuerst stoppen - bei laufendem Server sind die "
                             f"Weltdateien gesperrt und die Kopie waere unvollstaendig.")
        if not Path(srv.base_path).expanduser().is_dir():
            raise ValueError(f"Ordner des {rolle}s nicht gefunden: {srv.base_path}")

    quell_pfad, ziel_pfad = basis(quelle), basis(ziel)
    if (quell_pfad == ziel_pfad or quell_pfad.is_relative_to(ziel_pfad)
            or ziel_pfad.is_relative_to(quell_pfad)):
        raise ValueError("Die beiden Serverordner liegen ineinander - Uebernahme abgebrochen.")

    if welt:
        if not welt_ordner(quelle):
            raise ValueError(f"'{quelle.name}' hat noch keine Welt "
                             f"(Ordner '{level_name(quelle)}' fehlt).")
        quell_welt = basis(quelle) / level_name(quelle)
        if not (quell_welt / "level.dat").is_file():
            # Ein Ordner ohne level.dat ist keine Welt, sondern ein Rest (abgebrochener
            # Download, manuell angelegt). Ohne diese Pruefung verliert das Ziel seine
            # Welt und bekommt einen leeren Ordner, aus dem der Server neu generiert.
            raise ValueError(
                f"Im Ordner '{level_name(quelle)}' von '{quelle.name}' fehlt die level.dat - "
                f"das ist keine vollstaendige Welt. Die Uebernahme wuerde die Welt des "
                f"Zielservers loeschen und nichts Brauchbares an ihre Stelle setzen.")

        abwaerts = pruefe_richtung(quelle, ziel)
        if abwaerts:
            raise ValueError(abwaerts)

    if erweiterungen:
        moegl = moeglichkeiten(quelle, ziel)["erweiterungen"]
        if not moegl["moeglich"]:
            raise ValueError(moegl["grund"])


def uebertrage(db: Session, *, quelle: Server, ziel: Server, welt: bool = True,
               spielerdaten: bool = True, configs: bool = False, erweiterungen: bool = False,
               zusatzwelten: bool = False, zugangslisten: bool = False,
               user_id: int | None = None, run_async: bool = True) -> None:
    """Ausgewaehlte Daten von ``quelle`` nach ``ziel`` uebernehmen.

    Prueft synchron (ValueError bei Problemen) und kopiert dann im Hintergrund - eine Welt
    kann Gigabytes gross sein. Der Fortschritt laeuft ueber dieselbe Anzeige wie Start und
    Duplizieren, die Detailseite des Zielservers zeigt ihn also ohne Zutun.
    """
    from threading import Thread

    from app.services import process_service, server_service

    optionen = {"welt": welt, "spielerdaten": spielerdaten, "configs": configs,
                "erweiterungen": erweiterungen, "zusatzwelten": zusatzwelten,
                "zugangslisten": zugangslisten}
    quell_id, ziel_id = quelle.id, ziel.id

    # ZUERST sperren, dann pruefen: beide Server fuer die Dauer der Kopie start-sperren -
    # die Quelle, damit sie die Welt nicht mitten im Lesen veraendert, das Ziel, damit es
    # nicht mit halb ersetzter Welt startet. Die Sperre ist exklusiv, faengt also auch eine
    # zweite gleichzeitige Uebernahme und ein laufendes Duplizieren ab. Erst danach zu
    # pruefen hiesse, auf einem Zustand zu pruefen, den ein paralleler Vorgang noch aendern
    # kann (Doppelklick auf "Uebernehmen", zweiter Tab, zweiter Admin).
    if not server_service.try_lock_servers_for_file_copy((quell_id, ziel_id)):
        raise ValueError("An den Dateien eines der beiden Server wird gerade gearbeitet - "
                         "eine Kopie oder eine andere Uebernahme laeuft noch. Bitte warten.")
    try:
        pruefe_transfer(quelle, ziel, welt=welt, configs=configs,
                        erweiterungen=erweiterungen, zusatzwelten=zusatzwelten,
                        zugangslisten=zugangslisten)
    except BaseException:
        server_service.unlock_server_for_file_copy(quell_id)
        server_service.unlock_server_for_file_copy(ziel_id)
        raise

    process_service.report_progress(ziel_id, active=True, stage="transfer", percent=0,
                                    message="Uebernahme wird vorbereitet ...")

    if not run_async:
        _fuehre_aus(db, quell_id, ziel_id, optionen, user_id)
        return

    def _im_hintergrund() -> None:
        from app.db.session import SessionLocal

        with SessionLocal() as arbeits_db:
            try:
                _fuehre_aus(arbeits_db, quell_id, ziel_id, optionen, user_id)
            except Exception:  # noqa: BLE001 - Status und Sperren macht _fuehre_aus selbst
                pass

    Thread(target=_im_hintergrund, daemon=True, name=f"mcsm-transfer-{ziel_id}").start()


# ------------------------------------------------------------------- Durchfuehrung

def _sicherung_enthaelt_welt(sicherung, weltname: str) -> bool:
    """Steht die Welt wirklich in der angelegten Sicherung?

    Gurt gegen einen verfehlten Backup-Umfang: der Transfer loescht die bisherige Welt des
    Ziels endgueltig, also muss die Zusage "vorher wird gesichert" ueberpruefbar stimmen und
    nicht nur im Protokoll stehen.
    """
    try:
        pfad = Path(sicherung.storage_path)
        if not pfad.is_file():
            return False
        praefixe = tuple(f"{weltname}{rest}/" for rest in ("", "_nether", "_the_end"))
        with zipfile.ZipFile(pfad) as archiv:
            return any(name.startswith(praefixe) for name in archiv.namelist())
    except Exception:  # noqa: BLE001 - im Zweifel gilt die Sicherung als unbrauchbar
        return False


def _protokoll(ziel_id: int, text: str) -> None:
    """Zeile in den Konsolenpuffer des Zielservers - der Nutzer sieht hinterher, was passiert ist."""
    try:
        from app.services.console_service import console_service

        console_service.append_output(ziel_id, f"[Uebernahme] {text}")
    except Exception:  # noqa: BLE001 - ein fehlendes Protokoll darf den Transfer nicht kippen
        pass


def _stelle_welt_wieder_her(eingeschoben: list[Path],
                            beiseite: list[tuple[Path, Path]]) -> list[str]:
    """Stand von vor dem Tausch wiederherstellen. Rueckgabe: Ordner, die nicht
    zurueckbenannt werden konnten (ihre Daten liegen dann noch unter dem .alte_welt-Namen).

    Faesst NUR an, was dieser Tausch selbst angelegt oder beiseitegelegt hat. Ein Rollback
    ueber alle kanonischen Weltnamen wuerde Ordner loeschen, die nie beiseitegelegt wurden -
    scheitert etwa das Umbenennen von world_nether, sind world_nether und world_the_end noch
    die ORIGINALE, und ein rmtree darauf vernichtet sie unwiederbringlich.
    """
    for pfad in eingeschoben:
        shutil.rmtree(pfad, ignore_errors=True)
    offen: list[str] = []
    for weg, platz in beiseite:
        if platz.exists():
            shutil.rmtree(platz, ignore_errors=True)
        try:
            weg.rename(platz)
        except OSError:
            offen.append(weg.name)
    return offen


def _tausche_welt(ziel_base: Path, ziel_welt: str, temp: Path, stempel: str) -> list[Path]:
    """Bisherige Weltordner beiseitelegen und die vorbereiteten an ihren Platz schieben.

    ALLE drei kanonischen Namen werden geraeumt, auch wenn der Transfer nur einen davon
    mitbringt: bliebe ein alter ``world_nether`` neben einer neuen Overworld stehen, wuerde
    der Server den alten Nether zur neuen Welt dazuladen - und das faellt erst auf, wenn
    jemand durch ein Portal geht.

    Scheitert das Einschieben, wird der Stand von vorher wiederhergestellt. Rueckgabe sind
    die beiseitegelegten Pfade, die nach Erfolg geloescht werden koennen.
    """
    namen = [ziel_welt, f"{ziel_welt}_nether", f"{ziel_welt}_the_end"]
    beiseite: list[tuple[Path, Path]] = []
    eingeschoben: list[Path] = []
    try:
        for name in namen:
            platz = ziel_base / name
            if not platz.exists():
                continue
            weg = ziel_base / f".alte_welt_{stempel}_{name}"
            if weg.exists():
                shutil.rmtree(weg, ignore_errors=True)
            if weg.exists():
                # Ein Rest, den wir nicht wegbekommen (offenes Handle). Abbrechen, bevor
                # rename den Originalordner auf etwas Halbes schiebt.
                raise OSError(f"Zwischenordner '{weg.name}' laesst sich nicht entfernen.")
            platz.rename(weg)   # gleiches Laufwerk -> nur ein Verzeichniseintrag, kein Kopieren
            beiseite.append((weg, platz))
        for ordner in sorted(temp.iterdir()):
            platz = ziel_base / ordner.name
            # Gurt: nach allen Umbenennungen muss der Zielpfad noch direkt im Serverordner
            # liegen. Ein Name, der die Basis verwirft, kaeme hier nicht mehr durch.
            if platz.parent.resolve() != ziel_base.resolve():
                raise ValueError(f"Unerwarteter Zielpfad '{platz}' - Tausch abgebrochen.")
            shutil.move(str(ordner), str(platz))
            eingeschoben.append(platz)
    except BaseException as exc:
        offen = _stelle_welt_wieder_her(eingeschoben, beiseite)
        if offen:
            # Nicht verschweigen: die alte Welt ist da, heisst aber anders. Sonst sucht der
            # Nutzer eine Welt, die er fuer geloescht haelt.
            raise OSError(
                f"{exc} - ausserdem liess sich die bisherige Welt nicht zurueckbenennen. "
                f"Ihre Daten liegen unveraendert im Serverordner unter: "
                f"{', '.join(offen)}") from exc
        raise
    return [weg for weg, _ in beiseite]


def _behalte_spielerdaten(alte_welt: Path, neue_welt: Path) -> list[str]:
    """Spielerdaten aus der bisherigen Welt des Ziels in die neue uebernehmen.

    Der Haken "Spielerdaten mitnehmen" bezieht sich auf die Daten der QUELLE. Ohne diesen
    Schritt waeren beim Abwaehlen auch die Spieler des ZIELSERVERS weg - die Welt wird ja
    komplett ersetzt. Gewollt ist "Gelaende tauschen, Spieler behalten".
    """
    from app.services.server_service import _robust_copy_tree

    behalten: list[str] = []
    for name in SPIELERDATEN_ORDNER:
        quelle = alte_welt / name
        if not quelle.is_dir():
            continue
        _robust_copy_tree(quelle, neue_welt / name)
        behalten.append(name)
    return behalten


def _uebernimm_welt(quelle: Server, ziel: Server, *, spielerdaten: bool, stempel: str,
                    melde) -> list[str]:
    """Welt in einen Zwischenordner kopieren, auf den Stil des Ziels umbauen und erst dann
    an ihren Platz schieben. Scheitert etwas davor, ist die bisherige Welt unberuehrt."""
    from app.services.server_service import _dir_total_size, _robust_copy_tree

    quell_ordner = welt_ordner(quelle)
    quell_welt, ziel_welt = level_name(quelle), level_name(ziel)
    quell_struktur, ziel_struktur = stil(quelle), ziel_stil(ziel)
    ziel_base = basis(ziel)
    meldungen: list[str] = []

    gesamt = sum(_dir_total_size(p) for p in quell_ordner)
    try:
        frei = shutil.disk_usage(str(ziel_base)).free
    except OSError:
        frei = 0
    if gesamt and frei and frei < gesamt + 256 * 1024 * 1024:
        raise ValueError(
            f"Nicht genug freier Speicher beim Zielserver (benoetigt ~{gesamt // (1024 * 1024)} "
            f"MB, frei ~{frei // (1024 * 1024)} MB).")

    temp = ziel_base / f".uebernahme_{stempel}"
    if temp.exists():
        shutil.rmtree(temp, ignore_errors=True)
    temp.mkdir(parents=True, exist_ok=True)

    try:
        mb = 1024 * 1024
        kopiert, letzte = [0], [-1]

        def _datei(pfad) -> None:
            if not gesamt:
                return
            try:
                kopiert[0] += Path(pfad).stat().st_size
            except OSError:
                return
            # 5-83 %: der Rest ist Umbau, Tausch, Configs.
            prozent = 5 + min(78, int(kopiert[0] * 78 / gesamt))
            if prozent != letzte[0]:
                letzte[0] = prozent
                melde(prozent, f"Welt wird kopiert ... {kopiert[0] // mb} / {gesamt // mb} MB")

        melde(5, "Welt wird kopiert ...")
        # Liegen beide Strukturen gleichzeitig vor, gilt die des Servertyps. Bei einer
        # getrennten Quelle sind die Unterordner im Hauptweltordner dann der Rest - er
        # darf nicht mitkommen, sonst ueberschreibt er beim Umbau die echten Daten.
        rest_auslassen = (quell_struktur == STIL_GETRENNT
                          and hat_zwei_strukturen(quelle))
        uebersprungen: list[str] = []
        for ordner in quell_ordner:
            ist_hauptwelt = ordner.name == quell_welt
            uebersprungen += _robust_copy_tree(
                ordner, temp / _zielname(ordner.name, quell_welt, ziel_welt),
                on_file=_datei,
                skip=_welt_filter(spielerdaten,
                                  ohne_dimensionen=rest_auslassen and ist_hauptwelt))
        if rest_auslassen:
            meldungen.append(_zwei_strukturen_meldung(quelle, basis(quelle), quell_welt))

        # Vor dem Umbau: die level.dat der Huellen verschwindet dabei. Was sich retten
        # laesst (Drachenkampf), wird uebernommen; der Rest wird mit den echten Werten
        # benannt. Beim Nutzer stand in world/level.dat DragonKilled=0, im End aber 1 -
        # ohne diesen Schritt waere der Drache nach dem Umzug zurueck.
        if quell_struktur == STIL_GETRENNT and ziel_struktur == STIL_GEMEINSAM:
            from app.services import world_merge_service as wms

            meldungen += wms.fuehre_level_dat_zusammen(
                temp / ziel_welt,
                [temp / f"{ziel_welt}_nether", temp / f"{ziel_welt}_the_end"])

        # Eine uebersprungene Weltdatei ist kein Schoenheitsfehler: eine fehlende
        # region/*.mca sind 512x512 Bloecke - ein ganzer Landstrich mit Basis und Dorf
        # waere nach dem Tausch neu generiert. Lieber abbrechen, solange die alte Welt
        # des Ziels noch unberuehrt ist.
        wichtig = [p for p in uebersprungen
                   if p.endswith(".mca") or p.endswith(".dat")
                   or Path(p).name in {"level.dat", "uid.dat"}]
        if wichtig:
            raise ValueError(
                f"{len(wichtig)} Weltdatei(en) liessen sich nicht kopieren, darunter "
                f"{', '.join(wichtig[:3])}. Das waeren fehlende Gelaendeteile oder "
                f"Spielerdaten - die Uebernahme wurde abgebrochen, die Welt des "
                f"Zielservers ist unveraendert. Meist haelt ein Virenscanner oder ein "
                f"offenes Fenster die Dateien fest; nach einem Moment erneut versuchen.")

        melde(84, "Welt wird auf die Struktur des Zielservers gebracht ...")
        meldungen += _baue_stil_um(temp, ziel_welt, quell_struktur, ziel_struktur)

        # Paper <-> Vanilla-Familie: beide nutzen ab 26.1 denselben Weltstil, legen die
        # weltglobalen Daten aber an verschiedenen Stellen ab. Der Stil-Umbau greift hier
        # also gar nicht - diese Dateien brauchen eine eigene Behandlung.
        qt = (quelle.server_type or "").strip().lower()
        zt = (ziel.server_type or "").strip().lower()
        paper_familie = {"paper", "purpur", "folia"}
        if qt in paper_familie and zt not in paper_familie and zt not in SPIGOT_LAYOUT_TYPEN:
            meldungen += _sortiere_paper_weltglobal(temp / ziel_welt, nach_vanilla=True)
        behalten: list[str] = []
        if not spielerdaten:
            melde(86, "Spielerdaten des Zielservers werden uebernommen ...")
            behalten = _behalte_spielerdaten(ziel_base / ziel_welt, temp / ziel_welt)

        if level_name(quelle) != ziel_welt:
            from app.services import world_merge_service as wms

            meldungen += wms.setze_weltnamen(temp / ziel_welt, ziel_welt)

        if spielerdaten:
            melde(86, "Spieleridentitaet wird angepasst ...")
            # Nach dem Stil-Umbau: temp enthaelt genau die Ordner, die ans Ziel gehen.
            meldungen += _passe_identitaet_an(
                quelle, ziel, temp / ziel_welt,
                weitere=[temp / f"{ziel_welt}_nether", temp / f"{ziel_welt}_the_end"])

        melde(87, "Bisherige Welt wird abgeloest ...")
        beiseite = _tausche_welt(ziel_base, ziel_welt, temp, stempel)
    except BaseException:
        shutil.rmtree(temp, ignore_errors=True)
        raise

    shutil.rmtree(temp, ignore_errors=True)
    for weg in beiseite:
        shutil.rmtree(weg, ignore_errors=True)

    # Erst JETZT, nach dem gelungenen Tausch: der Quelle ihre Welt-Identitaet nehmen, damit
    # nicht zwei Welten dieselbe UUID tragen. Vorher waere es ein Eingriff in die Quelle
    # ohne Gegenwert, falls der Transfer noch scheitert.
    entwertet: list[str] = []
    for ordner in quell_ordner:
        datei = ordner / "uid.dat"
        if not datei.is_file():
            continue
        try:
            datei.unlink()
            entwertet.append(ordner.name)
        except OSError:
            continue

    ordner_text = ", ".join(p.name for p in quell_ordner)
    meldungen.insert(0, f"Welt '{quell_welt}' von '{quelle.name}' uebernommen "
                        f"(als '{ziel_welt}', Quellordner: {ordner_text}).")
    if not spielerdaten:
        text = ("Spielerdaten der Quelle (Inventare, Positionen, Statistiken, Fortschritte) "
                "wurden auf Wunsch nicht mitgenommen.")
        if behalten:
            text += f" Die des Zielservers wurden behalten ({', '.join(behalten)})."
        meldungen.append(text)
    if entwertet:
        meldungen.append(
            f"Die Welt-Identitaet ist mitgewandert, damit Spielerpositionen und "
            f"weltgebundene Plugin-Daten weiter passen. Damit es sie nicht zweimal gibt, "
            f"bekommt '{quelle.name}' beim naechsten Start eine neue "
            f"({', '.join(entwertet)}).")
    if uebersprungen:
        meldungen.append(f"{len(uebersprungen)} Datei(en) waren gesperrt und fehlen in der Kopie.")
    return meldungen


def _yaml_tiefenwert(daten, pfad: tuple[str, ...]):
    """Wert aus verschachtelten dicts holen; ``None``, wenn der Pfad nicht existiert."""
    knoten = daten
    for teil in pfad:
        if not isinstance(knoten, dict) or teil not in knoten:
            return None
        knoten = knoten[teil]
    return knoten


def _yaml_tiefensetzen(daten: dict, pfad: tuple[str, ...], wert) -> None:
    knoten = daten
    for teil in pfad[:-1]:
        if not isinstance(knoten.get(teil), dict):
            knoten[teil] = {}
        knoten = knoten[teil]
    knoten[pfad[-1]] = wert


def _netzwerk_yaml_merken(ziel_base: Path) -> dict:
    """Netzwerk-Werte des Zielservers aus seinen YAMLs sichern (vor dem Ueberschreiben)."""
    import yaml

    gemerkt: dict = {}
    for datei, pfad in NETZWERK_YAML:
        voll = ziel_base / datei
        if not voll.is_file():
            continue
        try:
            daten = yaml.safe_load(voll.read_text(encoding="utf-8", errors="ignore")) or {}
        except Exception:  # noqa: BLE001 - kaputte YAML ist kein Grund zu scheitern
            continue
        wert = _yaml_tiefenwert(daten, pfad)
        if wert is not None:
            gemerkt[(datei, pfad)] = wert
    return gemerkt


def _netzwerk_yaml_zurueckschreiben(ziel_base: Path, gemerkt: dict) -> list[str]:
    """Die gemerkten Netzwerk-Werte wieder eintragen. Rueckgabe: beschriebene Dateien."""
    import yaml

    nach_datei: dict[str, list] = {}
    for (datei, pfad), wert in gemerkt.items():
        nach_datei.setdefault(datei, []).append((pfad, wert))

    geschrieben: list[str] = []
    for datei, eintraege in nach_datei.items():
        voll = ziel_base / datei
        if not voll.is_file():
            continue
        try:
            daten = yaml.safe_load(voll.read_text(encoding="utf-8", errors="ignore")) or {}
            if not isinstance(daten, dict):
                continue
            geaendert = False
            for pfad, wert in eintraege:
                if _yaml_tiefenwert(daten, pfad) != wert:
                    _yaml_tiefensetzen(daten, pfad, wert)
                    geaendert = True
            if geaendert:
                voll.write_text(yaml.safe_dump(daten, sort_keys=False, allow_unicode=True),
                                encoding="utf-8")
                geschrieben.append(datei)
        except Exception:  # noqa: BLE001
            continue
    return geschrieben


def _uebernimm_configs(quelle: Server, ziel: Server) -> list[str]:
    """server.properties (gefiltert), die bekannten YAMLs und den config/-Ordner uebernehmen."""
    from app.services.server_service import _robust_copy_tree

    quell_base, ziel_base = basis(quelle), basis(ziel)
    # Netzwerk-Setup des ZIELS sichern, bevor die YAMLs ueberschrieben werden.
    gemerkt = _netzwerk_yaml_merken(ziel_base)
    meldungen = [_kopiere_properties(quelle, ziel)]

    dateien = 0
    misslungen: list[str] = []
    for name in CONFIG_DATEIEN:
        quell_datei = quell_base / name
        if not quell_datei.is_file():
            continue
        try:
            shutil.copy2(quell_datei, ziel_base / name)
            dateien += 1
        except OSError:
            # Nicht verschlucken: wer "Configs uebernehmen" anhakt und nichts hoert, haelt
            # die Einstellungen fuer uebernommen.
            misslungen.append(name)
    if dateien:
        meldungen.append(f"{dateien} Config-Datei(en) uebernommen.")
    if misslungen:
        meldungen.append(f"NICHT uebernommen (gesperrt oder nicht schreibbar): "
                         f"{', '.join(misslungen)}")
    if not dateien and not misslungen:
        meldungen.append("Keine der bekannten Config-Dateien war beim Quellserver vorhanden.")

    # config/ heisst bei Paper "die neuen YAMLs" und bei Forge/NeoForge "die Mod-Configs".
    # Zwischen den Familien zu kopieren legt paper-global.yml in Forges Mod-Ordner bzw.
    # Mod-TOMLs neben Papers YAMLs - beides sinnlos und verwirrend.
    familie_passt = (ist_bukkit(quelle.server_type) and ist_bukkit(ziel.server_type)) or (
        (quelle.server_type or "").strip().lower() == (ziel.server_type or "").strip().lower())
    for name in CONFIG_ORDNER:
        quell_ordner = quell_base / name
        if not quell_ordner.is_dir():
            continue
        if not familie_passt:
            meldungen.append(
                f"Ordner '{name}' NICHT uebernommen: bei {quelle.server_type} enthaelt er "
                f"etwas anderes als bei {ziel.server_type}.")
            continue
        uebersprungen = _robust_copy_tree(quell_ordner, ziel_base / name)
        text = (f"Ordner '{name}' uebernommen - gleichnamige Dateien wurden ersetzt, "
                f"zusaetzliche Dateien des Zielservers bleiben stehen.")
        if uebersprungen:
            text += f" {len(uebersprungen)} Datei(en) gesperrt."
        meldungen.append(text)

    # usercache.json: die einzige Zuordnung Name <-> UUID, die ein Server fuehrt. Ohne sie
    # kann ein spaeterer Transfer VON diesem Server die Spieler nicht mehr umschreiben, und
    # /ban <Name> findet keinen Offline-Spieler. Zusammenfuehren statt ersetzen, damit die
    # Spieler des Ziels nicht verloren gehen.
    meldungen += _fuehre_usercache_zusammen(quell_base, ziel_base)

    geschuetzt = _netzwerk_yaml_zurueckschreiben(ziel_base, gemerkt)
    if geschuetzt:
        meldungen.append(
            f"Netzwerk-Einstellungen des Zielservers in {', '.join(geschuetzt)} "
            f"beibehalten (Proxy-Weiterleitung und Geheimnis gehoeren zu diesem Server).")
    return meldungen


def _fuehre_usercache_zusammen(quell_base: Path, ziel_base: Path) -> list[str]:
    """usercache.json beider Server zusammenlegen (neuere Eintraege gewinnen)."""
    import json

    def _lies(pfad: Path) -> list:
        if not pfad.is_file():
            return []
        try:
            daten = json.loads(pfad.read_text(encoding="utf-8", errors="ignore"))
            return daten if isinstance(daten, list) else []
        except Exception:  # noqa: BLE001
            return []

    quelle_eintraege = _lies(quell_base / "usercache.json")
    if not quelle_eintraege:
        return []
    ziel_datei = ziel_base / "usercache.json"
    nach_uuid = {}
    for eintrag in _lies(ziel_datei) + quelle_eintraege:
        if isinstance(eintrag, dict) and eintrag.get("uuid"):
            nach_uuid[str(eintrag["uuid"]).lower()] = eintrag
    try:
        ziel_datei.write_text(json.dumps(list(nach_uuid.values()), indent=2),
                              encoding="utf-8")
    except OSError:
        return []
    return [f"Namensspeicher (usercache.json) zusammengefuehrt: {len(nach_uuid)} Spieler - "
            f"damit bleiben Name und UUID auch fuer spaetere Umzuege zuordenbar."]


def _uebernimm_erweiterungen(quelle: Server, ziel: Server) -> list[str]:
    """``plugins/`` oder ``mods/`` uebernehmen und die alten JARs dabei entfernen.

    Bleiben alte JARs liegen, laedt der Server zwei Versionen desselben Plugins/Mods - bei
    Plugins gibt das eine Fehlermeldung, bei Mods meist einen Absturz beim Start. Die
    Datenordner daneben (z.B. ``plugins/WorldEdit/``) bleiben stehen, weil dort Einstellungen
    und weltbezogene Daten liegen.
    """
    from app.services.server_service import _robust_copy_tree

    art = moeglichkeiten(quelle, ziel)["erweiterungen"]
    if not art["moeglich"]:
        return [art["grund"]]

    name = art["ordner"]
    quell_ordner = basis(quelle) / name
    if not quell_ordner.is_dir():
        return [f"Der Quellserver hat keinen Ordner '{name}' - uebersprungen."]

    ziel_ordner = basis(ziel) / name
    ziel_ordner.mkdir(parents=True, exist_ok=True)
    entfernt = 0
    geblieben: list[str] = []
    for jar in list(ziel_ordner.glob("*.jar")):
        try:
            jar.unlink()
            entfernt += 1
        except OSError:
            # Eine alte JAR, die bleibt, laedt neben der neuen Version - bei Mods meist ein
            # Absturz beim Start. Das muss der Nutzer erfahren.
            geblieben.append(jar.name)

    # plugins/update/ wendet Bukkit beim naechsten Start an und ERSETZT damit Plugins durch
    # die Jars darin. Mitzukopieren heisst, dem Ziel ungefragt die Plugin-Versionen
    # unterzuschieben, die bei der Quelle noch nicht einmal aktiv waren.
    def _ohne_update(rel: Path) -> bool:
        return rel.parts[:1] == ("update",)

    uebersprungen = _robust_copy_tree(quell_ordner, ziel_ordner, skip=_ohne_update)
    angekommen = len([p for p in (ziel_ordner).glob("*.jar")
                      if p.name not in geblieben])
    text = f"'{name}': {angekommen} JAR-Datei(en) uebernommen, {entfernt} alte entfernt."
    if uebersprungen:
        text += f" {len(uebersprungen)} Datei(en) gesperrt."
    meldungen = [text]
    if geblieben:
        meldungen.append(
            f"ACHTUNG: diese alten Dateien liessen sich nicht entfernen und laufen jetzt "
            f"neben den neuen: {', '.join(geblieben)}. Bitte von Hand loeschen, bevor der "
            f"Server startet.")
    return meldungen


def _fuehre_aus(db: Session, quell_id: int, ziel_id: int, optionen: dict,
                user_id: int | None) -> None:
    """Der eigentliche Transfer. Laeuft im Hintergrund und raeumt Sperren und Status in
    jedem Fall auf."""
    from app.services import audit_service, backup_service, process_service, server_service

    def melde(prozent: int, text: str) -> None:
        process_service.report_progress(ziel_id, active=True, stage="transfer",
                                        percent=prozent, message=text)

    meldungen: list[str] = []
    try:
        quelle, ziel = db.get(Server, quell_id), db.get(Server, ziel_id)
        if quelle is None or ziel is None:
            return

        # Erneut pruefen: zwischen Klick und Start dieses Threads kann jemand einen der
        # beiden Server gestartet haben (die Vorabpruefung deckt nur den Klick-Moment ab).
        pruefe_transfer(quelle, ziel, welt=optionen["welt"], configs=optionen["configs"],
                        erweiterungen=optionen["erweiterungen"],
                        zusatzwelten=bool(optionen.get("zusatzwelten")),
                        zugangslisten=bool(optionen.get("zugangslisten")))

        stempel = datetime.now().strftime("%Y%m%d-%H%M%S")
        melde(2, "Sicherung des Zielservers wird angelegt ...")
        umfang = "full" if (optionen["configs"] or optionen["erweiterungen"]) else "world"
        try:
            sicherung = backup_service.create_backup(
                db, server=ziel, initiated_by_user_id=user_id, backup_scope=umfang,
                backup_type="auto", custom_name=f"{ziel.slug}-vor-uebernahme-{stempel}")
        except Exception as exc:  # noqa: BLE001
            # Ohne Sicherung wird nichts angefasst - sonst gaebe es keinen Weg zurueck.
            raise ValueError(f"Die Sicherung des Zielservers ist fehlgeschlagen, deshalb wurde "
                             f"nichts veraendert: {exc}") from exc
        # Die Zusage "vorher wird gesichert" muss stimmen, nicht nur protokolliert werden -
        # gleich danach wird die bisherige Welt des Ziels endgueltig geloescht. Hat das Ziel
        # noch gar keine Welt, ist eine weltlose Sicherung dagegen richtig.
        if optionen["welt"] and welt_ordner(ziel) and not _sicherung_enthaelt_welt(
                sicherung, level_name(ziel)):
            raise ValueError(
                f"Die Sicherung '{sicherung.backup_name}' enthaelt die Welt "
                f"'{level_name(ziel)}' des Zielservers nicht - deshalb wurde nichts "
                f"veraendert. Ohne brauchbare Sicherung gibt es keinen Weg zurueck.")
        meldungen.append(f"Sicherung '{sicherung.backup_name}' angelegt ({umfang}).")

        # Auch hier protokollieren: die Vorschau kann uebergangen worden sein (direkter
        # POST, oder der Nutzer hat die Warnung ueberlesen).
        try:
            for risiko in warnungen(quelle, ziel, welt=optionen["welt"]):
                meldungen.append(f"ACHTUNG: {risiko}")
        except Exception:  # noqa: BLE001
            pass

        if optionen["welt"]:
            meldungen += _uebernimm_welt(quelle, ziel, spielerdaten=optionen["spielerdaten"],
                                         stempel=stempel, melde=melde)
        if optionen["welt"] and not optionen["configs"]:
            # Nur wenn Configs NICHT mitkommen - sonst wandern diese Werte dort schon mit.
            meldungen += _uebernimm_welt_properties(quelle, ziel)

        if optionen["welt"] and optionen["spielerdaten"]:
            # Jetzt liegt die Welt an ihrem Platz; die Dateien im Serverordner nachziehen.
            # get, nicht pop: die Zuordnung brauchen auch der Plugin- und der
            # Zugangslisten-Schritt weiter unten. Aufgeraeumt wird am Ende.
            meldungen += _stelle_server_daten_um(
                quelle, ziel, _LETZTE_ZUORDNUNG.get(ziel_id, {}))

        if optionen.get("zusatzwelten"):
            melde(88, "Weitere Welten werden uebernommen ...")
            meldungen += _uebernimm_zusatzwelten(
                quelle, ziel, spielerdaten=optionen["spielerdaten"], stempel=stempel,
                melde=melde)

        if optionen["configs"]:
            melde(90, "Configs werden uebernommen ...")
            meldungen += _uebernimm_configs(quelle, ziel)
        if optionen["erweiterungen"]:
            melde(95, "Plugins/Mods werden uebernommen ...")
            meldungen += _uebernimm_erweiterungen(quelle, ziel)
            # Erst JETZT, nachdem die Plugin-Daten da sind: der Weltname steckt in ihren
            # Ordnernamen UND in ihren Dateien.
            from app.services import player_data_service as pds

            meldungen += _passe_plugin_weltnamen_an(
                basis(ziel), level_name(quelle), level_name(ziel))
            meldungen += pds.ersetze_weltnamen_in_plugin_daten(
                basis(ziel), level_name(quelle), level_name(ziel))
            # Die mitgebrachten Plugin-Daten tragen noch die UUIDs der Quelle.
            meldungen += _stelle_server_daten_um(
                quelle, ziel, _LETZTE_ZUORDNUNG.get(ziel_id, {}))

        if optionen.get("zugangslisten"):
            melde(97, "Zugangslisten werden uebernommen ...")
            from app.services import player_data_service as pds2

            meldungen += pds2.uebernimm_zugangslisten(basis(quelle), basis(ziel))
            meldungen += pds2.stelle_zugangslisten_um(
                basis(ziel), _LETZTE_ZUORDNUNG.get(ziel_id, {}))

        for zeile in meldungen:
            _protokoll(ziel_id, zeile)
        # Uebersprungene Dateien gehoeren in die Abschlussmeldung, nicht nur ins Protokoll:
        # "abgeschlossen" bei fehlenden Weltdateien waere eine falsche Zusage.
        luecken = [z for z in meldungen if "gesperrt" in z or "fehlen" in z]
        process_service.report_progress(
            ziel_id, active=False, stage="stopped", percent=100,
            message=("Uebernahme abgeschlossen." if not luecken else
                     f"Uebernahme abgeschlossen, aber unvollstaendig: {luecken[0]}"))
        try:
            audit_service.log_action(
                db, action="server.transfer", user_id=user_id, server_id=ziel_id,
                details=(f"source={quell_id} welt={optionen['welt']} "
                         f"spielerdaten={optionen['spielerdaten']} "
                         f"configs={optionen['configs']} "
                         f"erweiterungen={optionen['erweiterungen']}"))
        except Exception:  # noqa: BLE001
            pass
    except Exception as exc:  # noqa: BLE001 - der Thread darf nie unbemerkt sterben
        db.rollback()
        for zeile in meldungen:
            _protokoll(ziel_id, zeile)
        _protokoll(ziel_id, f"FEHLGESCHLAGEN: {exc}")
        process_service.report_progress(ziel_id, active=False, stage="error", percent=0,
                                        message=f"Uebernahme fehlgeschlagen: {exc}")
        try:
            audit_service.log_action(db, action="server.transfer_failed", user_id=user_id,
                                     server_id=ziel_id, details=f"source={quell_id} {exc}")
        except Exception:  # noqa: BLE001
            pass
    finally:
        _LETZTE_ZUORDNUNG.pop(ziel_id, None)
        server_service.unlock_server_for_file_copy(quell_id)
        server_service.unlock_server_for_file_copy(ziel_id)
