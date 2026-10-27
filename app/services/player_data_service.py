"""Spieler-UUIDs ausserhalb der Weltdaten umstellen: Zugangslisten und Plugin-Daten.

``world_identity_service`` tauscht die 16-BYTE-Form einer UUID, so wie Minecraft sie in NBT
ablegt. Ausserhalb der Welt steht dieselbe UUID aber als TEXT:

* ``ops.json``, ``whitelist.json``, ``banned-players.json``: ``"uuid": "69f230e1-af2c-..."``
  Ohne Umstellung kommt nach einem Wechsel der Anmeldeart niemand mehr durch die Whitelist
  ("You are not white-listed on this server"), Operatoren sind keine mehr, und Gebannte
  koennen wieder beitreten. Das ist der Effekt, der in Sekunde eins auffaellt.
* Plugin-Daten: ``plugins/Essentials/userdata/<uuid>.yml`` (Heimatpunkte, Guthaben),
  LuckPerms (Raenge), DeadChest (Todeskisten), Simple Voice Chat (Gruppen) - als Dateiname
  oder als Textfeld in YAML/JSON.

Deshalb hier eine Textersetzung, die alle ueblichen Schreibweisen abdeckt: mit und ohne
Bindestriche, in Gross- und Kleinschreibung.

**Was bewusst NICHT angefasst wird:** Datenbanken (``.db``, ``.sqlite``). Dort steht die
UUID je nach Plugin als Text, als BLOB oder in zwei Long-Spalten, und ein falscher Eingriff
in ein fremdes Schema ist schwerer zu bemerken als in einer Textdatei. Solche Dateien werden
nur GEMELDET, damit der Nutzer weiss, wo noch Handarbeit ansteht.
"""

from __future__ import annotations

import json
import re
import uuid as _uuid
from pathlib import Path

# Textdateien, in denen Plugins UUIDs ablegen.
TEXT_ENDUNGEN = frozenset({".yml", ".yaml", ".json", ".txt", ".csv", ".properties",
                           ".conf", ".toml", ".ini", ".log"})

# Datenbanken: nur melden, nicht anfassen (siehe Modul-Docstring).
DATENBANK_ENDUNGEN = frozenset({".db", ".sqlite", ".sqlite3", ".mv.db", ".h2.db"})

# Die vier Listen im Serverordner, die Minecraft selbst fuehrt.
ZUGANGSLISTEN = ("ops.json", "whitelist.json", "banned-players.json", "banned-ips.json")

# Obergrenze, damit ein versehentlich mitgelieferter Riesen-Logordner den Transfer nicht
# minutenlang blockiert.
MAX_DATEIGROESSE = 32 * 1024 * 1024


def _schreibweisen(wert: str) -> list[str]:
    """Alle Textformen, in denen eine UUID in Plugin-Daten vorkommen kann."""
    try:
        kennung = _uuid.UUID(wert)
    except (ValueError, AttributeError, TypeError):
        return []
    mit = str(kennung)
    ohne = kennung.hex
    # Reihenfolge: die laengere Form zuerst, damit sie nicht von der kurzen zerlegt wird.
    return [mit, mit.upper(), ohne, ohne.upper()]


def _paare(zuordnung: dict[str, str]) -> list[tuple[str, str]]:
    """Textpaare alt->neu in allen Schreibweisen, laengste zuerst."""
    paare: list[tuple[str, str]] = []
    for alt, neu in zuordnung.items():
        alte, neue = _schreibweisen(alt), _schreibweisen(neu)
        if len(alte) != len(neue) or not alte:
            continue
        for a, n in zip(alte, neue):
            if a != n:
                paare.append((a, n))
    paare.sort(key=lambda p: -len(p[0]))
    return paare


def _ersetze_text(inhalt: str, paare: list[tuple[str, str]]) -> tuple[str, int]:
    treffer = 0
    for alt, neu in paare:
        anzahl = inhalt.count(alt)
        if anzahl:
            inhalt = inhalt.replace(alt, neu)
            treffer += anzahl
    return inhalt, treffer


def schreibe_textdatei_um(pfad: Path, paare: list[tuple[str, str]]) -> int:
    """UUIDs in einer Textdatei ersetzen. Rueckgabe: Zahl der Ersetzungen.

    Die Datei wird nur geschrieben, wenn sich etwas geaendert hat - und nur, wenn sie als
    UTF-8 lesbar ist. Eine Datei mit fremder Kodierung bleibt unberuehrt, statt sie beim
    Zurueckschreiben zu beschaedigen.
    """
    try:
        if pfad.stat().st_size > MAX_DATEIGROESSE:
            return 0
        inhalt = pfad.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return 0
    neu, treffer = _ersetze_text(inhalt, paare)
    if not treffer:
        return 0
    try:
        pfad.write_text(neu, encoding="utf-8", newline="")
    except OSError:
        return 0
    return treffer


def benenne_uuid_dateien_um(wurzel: Path, zuordnung: dict[str, str]) -> list[str]:
    """Dateien und Ordner umbenennen, deren NAME eine umzustellende UUID ist.

    Essentials legt ``userdata/<uuid>.yml`` an, andere Plugins ebenso. Rueckgabe: die
    neuen Pfade, relativ zur Wurzel.
    """
    muster = re.compile(
        r"(?i)([0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12})")
    nach_kompakt = {_uuid.UUID(a).hex: neu for a, neu in zuordnung.items()}
    umbenannt: list[str] = []
    # Von unten nach oben, damit das Umbenennen eines Ordners die Pfade darunter nicht
    # unter den Fuessen wegzieht.
    for pfad in sorted(wurzel.rglob("*"), key=lambda p: -len(p.parts)):
        treffer = muster.search(pfad.name)
        if not treffer:
            continue
        try:
            gefunden = _uuid.UUID(treffer.group(1)).hex
        except ValueError:
            continue
        neu_wert = nach_kompakt.get(gefunden)
        if not neu_wert:
            continue
        # Dieselbe Schreibweise beibehalten (mit/ohne Bindestriche, Gross/Klein).
        roh = treffer.group(1)
        ersatz = str(_uuid.UUID(neu_wert))
        if "-" not in roh:
            ersatz = ersatz.replace("-", "")
        if roh.isupper():
            ersatz = ersatz.upper()
        ziel = pfad.with_name(pfad.name.replace(roh, ersatz))
        if ziel.exists():
            continue
        try:
            pfad.rename(ziel)
            umbenannt.append(str(ziel.relative_to(wurzel)).replace("\\", "/"))
        except OSError:
            continue
    return umbenannt


def stelle_zugangslisten_um(server_base: Path, zuordnung: dict[str, str]) -> list[str]:
    """UUIDs in ops.json, whitelist.json und den Bann-Listen umstellen."""
    paare = _paare(zuordnung)
    if not paare:
        return []
    geaendert: list[str] = []
    for name in ZUGANGSLISTEN:
        datei = server_base / name
        if not datei.is_file():
            continue
        treffer = schreibe_textdatei_um(datei, paare)
        if treffer:
            geaendert.append(f"{name} ({treffer})")
    if not geaendert:
        return []
    return [f"Zugangslisten auf die neuen UUIDs umgestellt: {', '.join(geaendert)} - "
            f"Whitelist, Operatoren und Banns greifen damit weiter."]


def uebernimm_zugangslisten(quell_base: Path, ziel_base: Path) -> list[str]:
    """Die vier Zugangslisten vom Quell- auf den Zielserver kopieren."""
    import shutil

    uebernommen: list[str] = []
    for name in ZUGANGSLISTEN:
        quelle = quell_base / name
        if not quelle.is_file():
            continue
        try:
            shutil.copy2(quelle, ziel_base / name)
            uebernommen.append(name)
        except OSError:
            continue
    if not uebernommen:
        return ["Der Quellserver hat keine Zugangslisten - uebersprungen."]
    return [f"Zugangslisten uebernommen: {', '.join(uebernommen)}. Die managerweiten Listen "
            f"setzen sich beim Serverstart weiter durch."]


def stelle_plugin_daten_um(server_base: Path, zuordnung: dict[str, str]) -> list[str]:
    """Spieler-UUIDs in den Plugin-Daten umstellen: Textdateien und Dateinamen.

    Datenbanken werden nur gemeldet - siehe Modul-Docstring.
    """
    plugins = server_base / "plugins"
    if not plugins.is_dir():
        return []
    paare = _paare(zuordnung)
    if not paare:
        return []

    dateien = 0
    treffer_gesamt = 0
    datenbanken: list[str] = []
    for pfad in plugins.rglob("*"):
        if not pfad.is_file():
            continue
        endung = pfad.suffix.lower()
        if endung in DATENBANK_ENDUNGEN:
            datenbanken.append(str(pfad.relative_to(server_base)).replace("\\", "/"))
            continue
        if endung not in TEXT_ENDUNGEN:
            continue
        treffer = schreibe_textdatei_um(pfad, paare)
        if treffer:
            dateien += 1
            treffer_gesamt += treffer

    umbenannt = benenne_uuid_dateien_um(plugins, zuordnung)

    meldungen: list[str] = []
    if dateien or umbenannt:
        meldungen.append(
            f"Plugin-Daten auf die neuen UUIDs umgestellt: {treffer_gesamt} Stelle(n) in "
            f"{dateien} Datei(en), {len(umbenannt)} Datei(en) umbenannt - damit bleiben "
            f"Raenge, Heimatpunkte, Guthaben und Todeskisten zugeordnet.")
    if datenbanken:
        meldungen.append(
            f"NICHT umgestellt, weil es Datenbanken sind: {', '.join(sorted(datenbanken)[:5])}"
            f"{' ...' if len(datenbanken) > 5 else ''}. Spielerdaten darin (z.B. LuckPerms, "
            f"CoreProtect) tragen weiter die alten UUIDs und muessen von Hand oder mit dem "
            f"Werkzeug des Plugins umgestellt werden.")
    return meldungen


def ersetze_weltnamen_in_plugin_daten(server_base: Path, alt: str, neu: str) -> list[str]:
    """Den Weltnamen im INHALT der Plugin-Daten ersetzen.

    Ordnernamen zieht der Transfer schon mit; die Weltnamen stehen aber auch IN den Dateien:
    ``world: lobby`` in Essentials-Heimatpunkten, Multiverse-Welteintraege, WorldGuard-
    Regionsdateien. Ersetzt wird nur der VOLLSTAENDIGE Name als eigenes Wort, damit aus
    "lobby2" nicht "welt2" wird.
    """
    plugins = server_base / "plugins"
    if not plugins.is_dir() or alt == neu or not alt:
        return []

    paare = [(alt, neu), (f"{alt}_nether", f"{neu}_nether"),
             (f"{alt}_the_end", f"{neu}_the_end")]
    # Laengste zuerst: sonst macht die Regel fuer "lobby" aus "lobby_nether" ein
    # "welt_nether" mit falschem Rest.
    paare.sort(key=lambda p: -len(p[0]))
    muster = [(re.compile(r"(?<![\w-])" + re.escape(a) + r"(?![\w-])"), n) for a, n in paare]

    dateien = 0
    treffer_gesamt = 0
    for pfad in plugins.rglob("*"):
        if not pfad.is_file() or pfad.suffix.lower() not in TEXT_ENDUNGEN:
            continue
        try:
            if pfad.stat().st_size > MAX_DATEIGROESSE:
                continue
            inhalt = pfad.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        neu_inhalt = inhalt
        treffer = 0
        for regel, ersatz in muster:
            neu_inhalt, anzahl = regel.subn(ersatz, neu_inhalt)
            treffer += anzahl
        if not treffer:
            continue
        try:
            pfad.write_text(neu_inhalt, encoding="utf-8", newline="")
            dateien += 1
            treffer_gesamt += treffer
        except OSError:
            continue

    if not dateien:
        return []
    return [f"Weltname im Inhalt der Plugin-Daten ersetzt ('{alt}' -> '{neu}'): "
            f"{treffer_gesamt} Stelle(n) in {dateien} Datei(en) - damit finden Heimatpunkte, "
            f"Warps und Regionen ihre Welt wieder."]


def pruefe_zugangslisten_uuid_art(server_base: Path, erwartet: str) -> list[str]:
    """Melden, wenn die UUIDs in den Zugangslisten nicht zur Anmeldeart passen.

    ``erwartet`` ist ``"v4"`` oder ``"v3"``. Wird nur geprueft, nicht geaendert - geaendert
    wird ueber stelle_zugangslisten_um mit einer echten Zuordnung.
    """
    unpassend: list[str] = []
    for name in ZUGANGSLISTEN:
        datei = server_base / name
        if not datei.is_file() or name == "banned-ips.json":
            continue
        try:
            eintraege = json.loads(datei.read_text(encoding="utf-8", errors="ignore"))
        except Exception:  # noqa: BLE001
            continue
        if not isinstance(eintraege, list):
            continue
        for eintrag in eintraege:
            if not isinstance(eintrag, dict):
                continue
            kennung = str(eintrag.get("uuid") or "").replace("-", "")
            if len(kennung) != 32:
                continue
            ist_v4 = kennung[12] == "4"
            if ist_v4 != (erwartet == "v4"):
                unpassend.append(f"{name}: {eintrag.get('name') or kennung}")
    if not unpassend:
        return []
    return [f"In den Zugangslisten des Zielservers stehen UUIDs der anderen Anmeldeart "
            f"({', '.join(unpassend[:5])}{' ...' if len(unpassend) > 5 else ''}). Diese "
            f"Eintraege greifen dort nicht."]
