"""Spieler-UUIDs in einer Welt umschreiben, damit ein Weltumzug nahtlos bleibt.

Wechselt eine Welt zwischen zwei Servern mit verschiedener Anmeldeart, aendert sich die
UUID jedes Spielers: im Online-Modus (oder hinter einem authentifizierenden Proxy) gilt die
echte Mojang-UUID (Version 4), ohne das erzeugt der Server sie selbst als MD5 ueber
``OfflinePlayer:<name>`` (Version 3). Danach passt nichts mehr zusammen - und zwar an viel
mehr Stellen als den Spielerdateien:

* ``playerdata/<uuid>.dat`` (ab 26.1 ``players/data/``), ``stats/``, ``advancements/``
  -> Inventar, Position, Statistiken, Fortschritte. Reine Dateinamen.
* ``Owner`` auf gezaehmten Tieren (Wolf, Katze, Pferd, Papagei) -> das Tier gehoert
  niemandem mehr und folgt keinem.
* ``Gossips[].Target`` auf Dorfbewohnern -> der gesamte Ruf und damit die Handelsrabatte.
* ``Thrower``/``Owner`` auf geworfenen Gegenstaenden.
* Bossbar-Zuschauer, Scoreboard-Eintraege, Raid-Teilnehmer.

Die letzten Punkte stecken IN den Chunk-Daten der Region-Dateien, nicht in einer
uebersichtlichen Datei.

**Wie hier umgeschrieben wird - und warum so:** Minecraft legt eine UUID in NBT als
``int[4]`` ab, also als genau die 16 Bytes der UUID in ihrer Reihenfolge. Diese Bytefolge
wird im dekomprimierten Chunk gesucht und durch die neue ersetzt - gleiche Laenge, keine
Strukturaenderung. Damit braucht es keinen NBT-Schreiber: das NBT wird nicht verstanden,
nur eine Bytefolge getauscht. Ein NBT-Baum neu zu schreiben waere die weitaus groessere
Fehlerquelle, denn jeder Tag-Typ muesste exakt so wieder herauskommen, wie er hereinkam.

Dass eine 16-Byte-Folge zufaellig woanders auftaucht, ist bei zufaelligen UUIDs praktisch
ausgeschlossen (1 zu 2^128 je Position).

Alles, was nicht gelesen oder nicht zugeordnet werden kann, bleibt **unveraendert**: ein
Chunk ohne Treffer wird byte-identisch uebernommen (die Originaldaten, nicht neu
komprimiert), eine unlesbare Region wird gar nicht angefasst.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import struct
import uuid as _uuid
import zlib
from pathlib import Path

# Sektorgroesse einer Anvil-Region. Jeder Chunk beginnt an einer Sektorgrenze.
SEKTOR = 4096

# Welche Dateien einer Welt Spieler-UUIDs enthalten koennen.
REGION_ORDNER = ("region", "entities", "poi")
DAT_DATEIEN = ("level.dat",)
DAT_ORDNER = ("data",)


def offline_uuid(name: str) -> str:
    """Die UUID, die ein Server ohne Mojang-Anmeldung fuer diesen Namen vergibt.

    Java bildet sie mit ``UUID.nameUUIDFromBytes("OfflinePlayer:<name>")``: MD5 ueber den
    Text, dann Version 3 und die RFC-4122-Variante in die Bytes schreiben.
    """
    roh = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode("utf-8")).digest())
    roh[6] = (roh[6] & 0x0F) | 0x30   # Version 3 (namensbasiert, MD5)
    roh[8] = (roh[8] & 0x3F) | 0x80   # Variante RFC 4122
    return str(_uuid.UUID(bytes=bytes(roh)))


def uuid_bytes(wert: str) -> bytes | None:
    """Die 16 Bytes, mit denen Minecraft diese UUID in NBT ablegt (``int[4]``, big-endian)."""
    roh = (wert or "").replace("-", "").strip()
    if len(roh) != 32:
        return None
    try:
        return bytes.fromhex(roh)
    except ValueError:
        return None


def ist_v4(wert: str) -> bool:
    """Echte Mojang-UUID (Version 4)? Die Version steht an der 13. Hex-Stelle."""
    roh = (wert or "").replace("-", "")
    return len(roh) == 32 and roh[12] == "4"


def lade_usercache(server_base: Path) -> dict[str, str]:
    """``usercache.json`` des Servers lesen: UUID (klein, mit Bindestrichen) -> Name.

    Das ist die einzige Zuordnung Name <-> UUID, die ein Server von sich aus fuehrt. Ohne
    sie liesse sich aus einer v4-UUID allein kein Name und damit keine Offline-UUID ableiten.
    """
    pfad = Path(server_base) / "usercache.json"
    if not pfad.is_file():
        return {}
    try:
        daten = json.loads(pfad.read_text(encoding="utf-8", errors="ignore"))
    except Exception:  # noqa: BLE001 - eine kaputte Datei ist kein Fehlerfall
        return {}
    ergebnis: dict[str, str] = {}
    if isinstance(daten, list):
        for eintrag in daten:
            if not isinstance(eintrag, dict):
                continue
            kennung = str(eintrag.get("uuid") or "").strip().lower()
            name = str(eintrag.get("name") or "").strip()
            if kennung and name:
                ergebnis[kennung] = name
    return ergebnis


def _spieler_ordner(welt: Path) -> list[tuple[Path, str]]:
    """Die Spielerordner einer Welt - alte und 26.1er Schreibweise."""
    return [
        (welt / "playerdata", ".dat"),
        (welt / "players" / "data", ".dat"),
        (welt / "stats", ".json"),
        (welt / "players" / "stats", ".json"),
        (welt / "advancements", ".json"),
        (welt / "players" / "advancements", ".json"),
    ]


def vorhandene_spieler(welt: Path) -> set[str]:
    """Alle UUIDs, zu denen die Welt Spielerdateien hat (klein, mit Bindestrichen)."""
    gefunden: set[str] = set()
    for ordner, endung in _spieler_ordner(welt):
        if not ordner.is_dir():
            continue
        for datei in ordner.glob(f"*{endung}"):
            name = datei.stem
            if name.endswith("_old"):
                continue
            try:
                gefunden.add(str(_uuid.UUID(name)).lower())
            except ValueError:
                continue
    return gefunden


def baue_zuordnung(welt: Path, server_base: Path, ziel_art: str, *,
                   namen_aufloeser=None) -> tuple[dict[str, str], list[str]]:
    """Zuordnung alte UUID -> neue UUID fuer alle Spieler dieser Welt.

    ``ziel_art`` ist ``"v4"`` (Zielserver nutzt echte Mojang-UUIDs) oder ``"v3"``.
    ``namen_aufloeser`` ist optional eine Funktion Name -> echte UUID (Mojang-Anfrage); sie
    wird nur fuer Spieler gebraucht, die aus dem Offline-Betrieb kommen und deren echte
    UUID nirgends lokal steht.

    Rueckgabe: (Zuordnung, nicht zuordenbare Spieler als Text). Bereits passende UUIDs
    stehen NICHT in der Zuordnung - fuer sie ist nichts zu tun.
    """
    cache = lade_usercache(server_base)
    zuordnung: dict[str, str] = {}
    offen: list[str] = []

    # Nicht nur Spieler MIT Datei: ein Haustier-Besitzer oder ein Dorfbewohner-Ruf kann
    # auf jemanden zeigen, dessen playerdata nie angelegt wurde oder abgewaehlt ist. Die
    # usercache kennt jeden, der je verbunden war - und nur fuer die laesst sich ueberhaupt
    # eine neue UUID bestimmen.
    kandidaten = set(vorhandene_spieler(welt)) | set(cache)

    for alt in sorted(kandidaten):
        if (ziel_art == "v4") == ist_v4(alt):
            continue   # passt schon
        name = cache.get(alt)
        if ziel_art == "v3":
            # Rueckwaerts: der Name genuegt, die Offline-UUID ist berechenbar.
            if not name:
                offen.append(f"{alt} (Name unbekannt - steht nicht in der usercache.json)")
                continue
            zuordnung[alt] = offline_uuid(name)
            continue

        # Vorwaerts nach v4: die echte UUID kennt nur Mojang.
        if not name:
            offen.append(f"{alt} (Name unbekannt - steht nicht in der usercache.json)")
            continue
        neu = None
        if namen_aufloeser is not None:
            try:
                neu = namen_aufloeser(name)
            except Exception:  # noqa: BLE001 - Netzfehler darf den Transfer nicht kippen
                neu = None
        if not neu:
            offen.append(f"{name} (echte Minecraft-UUID nicht abrufbar)")
            continue
        zuordnung[alt] = str(neu).lower()

    return zuordnung, offen


def _ersetzungen(zuordnung: dict[str, str]) -> dict[bytes, bytes]:
    """Zuordnung in Bytefolgen umsetzen (nur Paare, bei denen beide Seiten taugen)."""
    paare: dict[bytes, bytes] = {}
    for alt, neu in zuordnung.items():
        a, n = uuid_bytes(alt), uuid_bytes(neu)
        if a and n and a != n:
            paare[a] = n
    return paare


def _bytes_ersetzen(roh: bytes, paare: dict[bytes, bytes]) -> bytes:
    for alt, neu in paare.items():
        if alt in roh:
            roh = roh.replace(alt, neu)
    return roh


def dat_umschreiben(pfad: Path, paare: dict[bytes, bytes]) -> bool:
    """UUIDs in einer gzip-NBT-Datei (level.dat, data/*.dat) ersetzen.

    ``True``, wenn etwas geaendert wurde. Nicht lesbare Dateien bleiben unberuehrt.
    """
    try:
        gepackt = pfad.read_bytes()
    except OSError:
        return False
    try:
        roh = gzip.decompress(gepackt)
        war_gzip = True
    except Exception:  # noqa: BLE001 - manche .dat liegen unkomprimiert vor
        roh, war_gzip = gepackt, False

    neu = _bytes_ersetzen(roh, paare)
    if neu == roh:
        return False
    try:
        pfad.write_bytes(gzip.compress(neu) if war_gzip else neu)
    except OSError:
        return False
    return True


def region_umschreiben(pfad: Path, paare: dict[bytes, bytes]) -> int:
    """UUIDs in einer Anvil-Region ersetzen. Rueckgabe: Zahl der geaenderten Chunks.

    Die Datei wird nur neu geschrieben, wenn mindestens ein Chunk einen Treffer hatte.
    Chunks ohne Treffer werden mit ihren ORIGINAL-Bytes uebernommen, also nicht neu
    komprimiert - damit kann ein Durchlauf ohne Treffer die Datei auch nicht verfaelschen.
    """
    try:
        daten = pfad.read_bytes()
    except OSError:
        return 0
    if len(daten) < SEKTOR * 2:
        return 0

    zeitstempel = daten[SEKTOR:SEKTOR * 2]
    # Je Chunk: (Rohblock wie er in die Datei kommt: 4 Byte Laenge + 1 Byte Verfahren +
    # Nutzdaten) oder None, wenn der Platz leer ist.
    bloecke: list[bytes | None] = []
    geaendert = 0

    for i in range(1024):
        ort = daten[i * 4:i * 4 + 4]
        offset = (ort[0] << 16) | (ort[1] << 8) | ort[2]
        sektoren = ort[3]
        if offset == 0 or sektoren == 0:
            bloecke.append(None)
            continue
        start = offset * SEKTOR
        if start + 5 > len(daten):
            bloecke.append(None)
            continue
        laenge = int.from_bytes(daten[start:start + 4], "big")
        if laenge < 1 or start + 4 + laenge > len(daten):
            bloecke.append(None)
            continue
        verfahren = daten[start + 4]
        nutzdaten = daten[start + 5:start + 4 + laenge]
        original = daten[start:start + 4 + laenge]

        try:
            if verfahren == 1:
                roh = gzip.decompress(nutzdaten)
            elif verfahren == 2:
                roh = zlib.decompress(nutzdaten)
            elif verfahren == 3:
                roh = nutzdaten
            else:
                # Unbekanntes Verfahren (z.B. externer .mcc-Chunk): nicht anfassen.
                bloecke.append(original)
                continue
        except Exception:  # noqa: BLE001 - ein kaputter Chunk bleibt, wie er ist
            bloecke.append(original)
            continue

        neu_roh = _bytes_ersetzen(roh, paare)
        if neu_roh == roh:
            bloecke.append(original)   # byte-identisch uebernehmen
            continue

        if verfahren == 1:
            neu_nutz = gzip.compress(neu_roh)
        elif verfahren == 2:
            neu_nutz = zlib.compress(neu_roh)
        else:
            neu_nutz = neu_roh
        bloecke.append(struct.pack(">I", len(neu_nutz) + 1)
                       + bytes([verfahren]) + neu_nutz)
        geaendert += 1

    if not geaendert:
        return 0

    # Datei neu aufbauen: Kopf (Orte + Zeitstempel), dann die Chunks sektorweise.
    kopf = bytearray(SEKTOR)
    rumpf = bytearray()
    naechster_sektor = 2   # Sektor 0 = Orte, Sektor 1 = Zeitstempel
    for i, block in enumerate(bloecke):
        if block is None:
            continue
        gefuellt = block + bytes((-len(block)) % SEKTOR)
        anzahl = len(gefuellt) // SEKTOR
        if anzahl > 255:
            # Passt nicht mehr in das Ein-Byte-Feld der Sektorzahl. Lieber die ganze Datei
            # unangetastet lassen als einen unlesbaren Kopf schreiben.
            return 0
        kopf[i * 4] = (naechster_sektor >> 16) & 0xFF
        kopf[i * 4 + 1] = (naechster_sektor >> 8) & 0xFF
        kopf[i * 4 + 2] = naechster_sektor & 0xFF
        kopf[i * 4 + 3] = anzahl
        rumpf += gefuellt
        naechster_sektor += anzahl

    try:
        pfad.write_bytes(bytes(kopf) + zeitstempel + bytes(rumpf))
    except OSError:
        return 0
    return geaendert


def benenne_spielerdateien_um(welt: Path, zuordnung: dict[str, str]) -> tuple[int, list]:
    """``playerdata/<alt>.dat`` -> ``<neu>.dat``, ebenso stats und advancements.

    Liegt unter dem NEUEN Namen schon eine Datei, gibt es fuer diesen Spieler zwei Staende
    (der Server hat irgendwann die Anmeldeart gewechselt). Dann gewinnt der ZULETZT
    GEAENDERTE - das ist der Stand, mit dem zuletzt gespielt wurde. Der andere wird nicht
    geloescht, sondern als ``<uuid>.ersetzt.dat`` aufgehoben: falls die Wahl falsch war,
    laesst sie sich von Hand rueckgaengig machen.

    Rueckgabe: (umbenannte Dateien, Liste der aufgeloesten Faelle).
    """
    umbenannt = 0
    aufgeloest: list[dict] = []
    for ordner, endung in _spieler_ordner(welt):
        if not ordner.is_dir():
            continue
        for alt, neu in zuordnung.items():
            for kandidat in (ordner / f"{alt}{endung}", ordner / f"{alt}{endung}_old"):
                if not kandidat.is_file():
                    continue
                ziel = ordner / kandidat.name.replace(alt, neu, 1)
                if ziel.exists():
                    behalten = _loese_doppelten_stand_auf(kandidat, ziel)
                    if behalten is None:
                        continue
                    aufgeloest.append({"ordner": ordner.name, "alt": alt, "neu": neu,
                                       "behalten": behalten})
                    if behalten == "alt":
                        umbenannt += 1
                    continue
                try:
                    kandidat.rename(ziel)
                    umbenannt += 1
                except OSError:
                    continue
    return umbenannt, aufgeloest


def _loese_doppelten_stand_auf(kandidat: Path, ziel: Path) -> str | None:
    """Zwei Staende fuer denselben Spieler: der juengere gewinnt, der andere bleibt liegen.

    Rueckgabe: "alt" (der umbenannte Stand gewinnt), "neu" (der vorhandene gewinnt) oder
    None, wenn nichts entschieden werden konnte.
    """
    try:
        zeit_kandidat = kandidat.stat().st_mtime
        zeit_ziel = ziel.stat().st_mtime
    except OSError:
        return None

    if zeit_kandidat <= zeit_ziel:
        # Der vorhandene Stand ist juenger - den umzubenennenden beiseitelegen.
        beiseite = kandidat.with_suffix(kandidat.suffix + ".ersetzt")
        try:
            if beiseite.exists():
                beiseite.unlink()
            kandidat.rename(beiseite)
        except OSError:
            return None
        return "neu"

    # Der umzubenennende Stand ist juenger - er gewinnt, der bisherige wird aufgehoben.
    beiseite = ziel.with_suffix(ziel.suffix + ".ersetzt")
    try:
        if beiseite.exists():
            beiseite.unlink()
        ziel.rename(beiseite)
        kandidat.rename(ziel)
    except OSError:
        return None
    return "alt"


def schreibe_welt_um(welt: Path, zuordnung: dict[str, str]) -> dict:
    """Alle Spieler-UUIDs einer Welt umstellen: Dateinamen UND Vorkommen in den Weltdaten.

    Rueckgabe: {"dateien": n, "chunks": n, "dat": n} - wie viel jeweils angefasst wurde.
    """
    paare = _ersetzungen(zuordnung)
    ergebnis: dict = {"dateien": 0, "chunks": 0, "dat": 0, "aufgeloest": []}
    if not paare:
        return ergebnis

    ergebnis["dateien"], ergebnis["aufgeloest"] = benenne_spielerdateien_um(
        welt, zuordnung)

    # Spielerdateien selbst enthalten UUIDs (z.B. das Reittier, auf dem man sass).
    for ordner, endung in _spieler_ordner(welt):
        if not ordner.is_dir() or endung != ".dat":
            continue
        for datei in ordner.glob("*.dat"):
            if dat_umschreiben(datei, paare):
                ergebnis["dat"] += 1

    for name in DAT_DATEIEN:
        datei = welt / name
        if datei.is_file() and dat_umschreiben(datei, paare):
            ergebnis["dat"] += 1

    # data-Ordner: auch die der Dimensionen. Dort liegen raids.dat bzw. raids_nether.dat
    # und raids_end.dat mit den Spieler-UUIDs der Dorf-Helden.
    for name in DAT_ORDNER:
        for ordner in (list(welt.glob(name)) + list(welt.glob(f"DIM*/{name}"))
                       + list(welt.glob(f"dimensions/*/*/{name}"))):
            if not ordner.is_dir():
                continue
            for datei in ordner.rglob("*.dat"):
                if dat_umschreiben(datei, paare):
                    ergebnis["dat"] += 1

    # Region-Dateien: hier stecken Haustier-Besitzer, Dorfbewohner-Ruf und die Entities.
    # Auch unter dimensions/<ns>/<dim>/ suchen (Minecraft 26.1+).
    gesehen: set[Path] = set()
    for muster in REGION_ORDNER:
        for ordner in list(welt.glob(muster)) + list(welt.glob(f"dimensions/*/*/{muster}")) \
                + list(welt.glob(f"DIM*/{muster}")):
            if not ordner.is_dir() or ordner in gesehen:
                continue
            gesehen.add(ordner)
            for datei in sorted(ordner.glob("*.mca")):
                ergebnis["chunks"] += region_umschreiben(datei, paare)

    return ergebnis


# ------------------------------------------------- Spieler mit zwei Staenden

def _datei_angaben(pfad: Path) -> dict | None:
    try:
        stat = pfad.stat()
    except OSError:
        return None
    return {"pfad": pfad, "bytes": stat.st_size, "zeit": stat.st_mtime}


def _spielerdatei(welt: Path, kennung: str) -> Path | None:
    """playerdata-Datei eines Spielers - alte und 26.1er Ablage."""
    for ordner in (welt / "playerdata", welt / "players" / "data"):
        datei = ordner / f"{kennung}.dat"
        if datei.is_file():
            return datei
    return None


def doppelte_staende(welt: Path, server_base: Path) -> list[dict]:
    """Spieler, die unter BEIDEN UUID-Arten Spielerdaten haben.

    Entsteht, wenn ein Server die Anmeldeart gewechselt hat: derselbe Mensch hat dann einen
    Stand unter seiner echten Mojang-UUID und einen unter der selbst erzeugten. Beim Umzug
    muss entschieden werden, welcher gilt - sonst gewinnt zufaellig der, der gerade am
    Zielnamen liegt.

    Rueckgabe je Spieler: beide Kennungen mit Groesse und letzter Aenderung, dazu welcher
    der juengere ist. Die Dateizeit ist das beste verfuegbare Mass dafuer, wann zuletzt
    mit diesem Stand gespielt wurde.
    """
    from datetime import datetime

    cache = lade_usercache(server_base)
    treffer: list[dict] = []
    for kennung, name in sorted(cache.items(), key=lambda p: p[1].lower()):
        if not ist_v4(kennung):
            continue   # von der echten UUID aus suchen, sonst findet man jeden zweimal
        andere = offline_uuid(name)
        echt = _spielerdatei(welt, kennung)
        offline = _spielerdatei(welt, andere)
        if not (echt and offline):
            continue
        a, b = _datei_angaben(echt), _datei_angaben(offline)
        if not (a and b):
            continue
        juenger = "echt" if a["zeit"] >= b["zeit"] else "offline"
        treffer.append({
            "name": name,
            "echt": {"uuid": kennung, "bytes": a["bytes"],
                     "datum": datetime.fromtimestamp(a["zeit"]).strftime("%d.%m.%Y %H:%M")},
            "offline": {"uuid": andere, "bytes": b["bytes"],
                        "datum": datetime.fromtimestamp(b["zeit"]).strftime("%d.%m.%Y %H:%M")},
            "juenger": juenger,
        })
    return treffer
