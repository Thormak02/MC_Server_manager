"""Welt-Metadaten beim Zusammenlegen getrennter Dimensionen retten.

In der Bukkit-Struktur ist jede Dimension eine eigene Welt mit eigener ``level.dat``. Werden
die Dimensionen in EINEN Weltordner gelegt (Vanilla-Stil, und Paper ab 26.1), gilt danach
nur noch die ``level.dat`` der Hauptwelt - die der Huellen verschwindet mitsamt allem, was
nur dort stand.

An der echten Welt dieses Nutzers (Spigot 1.21) nachgemessen, was sich unterscheidet:

* ``DragonFight``: in ``world_the_end`` steht ``DragonKilled=1``, ``PreviouslyKilled=1``
  und die Lage des Ausgangsportals - in ``world/level.dat`` dagegen ``0``/``0``. Ohne
  Uebernahme stuende nach dem Umzug ein neuer Enderdrache am Himmel, obwohl er besiegt war.
* ``WorldGenSettings.seed``: Hauptwelt 5868878461555556257, Nether und End
  3992729094211360533. Der Nether hat also einen EIGENEN Seed. Den kann die gemeinsame
  Struktur nicht abbilden - sie hat nur einen.
* ``Difficulty``: Hauptwelt 3 (schwer), Nether und End 1 (leicht).
* ``SpawnX/Y/Z``, ``BorderSize`` und die anderen Weltgrenzen-Felder, Wetter und Zeit.

Was uebernehmbar ist, wird uebernommen; was es nicht ist, wird mit den TATSAECHLICHEN
Werten benannt statt pauschal gewarnt. Geschrieben wird nur ueber ``nbt_codec``, der jede
Datei nach dem Schreiben erneut einliest und bei Abweichung abbricht.
"""

from __future__ import annotations

from pathlib import Path

from app.services import nbt_codec as nc

# Felder, die nur in der End-Welt stehen und die Hauptwelt in der gemeinsamen Struktur
# fuehren muss.
VOM_END = ("DragonFight",)

# Felder, die pro Dimension gelten und in der gemeinsamen Struktur kein Gegenstueck haben.
# Sie koennen nicht uebernommen werden - abweichende Werte werden gemeldet.
NUR_EINMAL = (
    ("WorldGenSettings", "seed"),
    ("Difficulty",),
    ("GameType",),
    ("SpawnX",), ("SpawnY",), ("SpawnZ",),
    ("BorderCenterX",), ("BorderCenterZ",), ("BorderSize",),
    ("BorderDamagePerBlock",), ("BorderSafeZone",),
    ("BorderWarningBlocks",), ("BorderWarningTime",),
    ("GameRules",),
)

_LESBAR = {
    "WorldGenSettings.seed": "Seed (Weltgenerator)",
    "Difficulty": "Schwierigkeit",
    "GameType": "Spielmodus",
    "SpawnX": "Spawn X", "SpawnY": "Spawn Y", "SpawnZ": "Spawn Z",
    "BorderCenterX": "Weltgrenze Mittelpunkt X",
    "BorderCenterZ": "Weltgrenze Mittelpunkt Z",
    "BorderSize": "Weltgrenze Groesse",
    "BorderDamagePerBlock": "Weltgrenze Schaden",
    "BorderSafeZone": "Weltgrenze Schutzzone",
    "BorderWarningBlocks": "Weltgrenze Warnabstand",
    "BorderWarningTime": "Weltgrenze Warnzeit",
    "GameRules": "Spielregeln",
}


def _anzeige(tag: nc.Tag | None) -> str:
    if tag is None:
        return "(nicht gesetzt)"
    if tag.typ == nc.TAG_COMPOUND:
        return f"<{len(tag.wert)} Einstellungen>"
    if tag.typ == nc.TAG_LIST:
        return f"<{len(tag.wert)} Eintraege>"
    if tag.typ in (nc.TAG_FLOAT, nc.TAG_DOUBLE) and isinstance(tag.wert, bytes):
        import struct

        form = ">f" if tag.typ == nc.TAG_FLOAT else ">d"
        try:
            return f"{struct.unpack(form, tag.wert)[0]:g}"
        except Exception:  # noqa: BLE001
            return tag.wert.hex()
    return str(tag.wert)


def _gleich(a: nc.Tag | None, b: nc.Tag | None) -> bool:
    if a is None or b is None:
        return a is None and b is None
    return a == b


def fuehre_level_dat_zusammen(welt: Path, huellen: list[Path]) -> list[str]:
    """Felder der Huellen-level.dat in die Hauptwelt retten und Unterschiede benennen.

    Muss laufen, SOLANGE die Huellen noch da sind - also vor dem Loeschen beim Stil-Umbau.
    Rueckgabe: Meldungen fuer Protokoll und Oberflaeche.
    """
    haupt_datei = welt / "level.dat"
    if not haupt_datei.is_file():
        return []
    try:
        haupt_name, haupt_wurzel, packart = nc.lies_datei(haupt_datei)
    except Exception as exc:  # noqa: BLE001 - unlesbare level.dat nicht anfassen
        return [f"level.dat der Hauptwelt nicht lesbar ({exc}) - Dimensionsdaten konnten "
                f"nicht zusammengefuehrt werden."]
    haupt_daten = nc.daten_wurzel(haupt_wurzel)

    meldungen: list[str] = []
    uebernommen: list[str] = []
    geaendert = False

    for huelle in huellen:
        datei = huelle / "level.dat"
        if not datei.is_file():
            continue
        try:
            _, wurzel, _ = nc.lies_datei(datei)
        except Exception:  # noqa: BLE001
            meldungen.append(f"level.dat von '{huelle.name}' nicht lesbar - ihre "
                             f"Einstellungen konnten nicht uebernommen werden.")
            continue
        daten = nc.daten_wurzel(wurzel)
        ist_end = huelle.name.endswith("_the_end")

        # 1) Uebernehmbares uebernehmen: der Drachenkampf steht nur im End.
        if ist_end:
            for feld in VOM_END:
                quelle_tag = daten.hole(feld)
                if quelle_tag is None:
                    continue
                ziel_tag = haupt_daten.hole(feld)
                if _gleich(quelle_tag, ziel_tag):
                    continue
                haupt_daten.setze(feld, quelle_tag)
                geaendert = True
                if feld == "DragonFight":
                    besiegt = daten.hole("DragonFight", "DragonKilled")
                    zuvor = daten.hole("DragonFight", "PreviouslyKilled")
                    stand = ("besiegt" if (besiegt and besiegt.wert) or (zuvor and zuvor.wert)
                             else "noch nicht besiegt")
                    uebernommen.append(f"Drachenkampf ({stand})")
                else:
                    uebernommen.append(feld)

        # 2) Nicht uebernehmbares benennen - mit den echten Werten.
        abweichend: list[str] = []
        for pfad in NUR_EINMAL:
            a = daten.hole(*pfad)
            b = haupt_daten.hole(*pfad)
            if a is None or _gleich(a, b):
                continue
            schluessel = ".".join(pfad)
            abweichend.append(f"{_LESBAR.get(schluessel, schluessel)}: "
                              f"{_anzeige(a)} statt {_anzeige(b)}")
        if abweichend:
            meldungen.append(
                f"'{huelle.name}' hatte eigene Werte, die in einem gemeinsamen Weltordner "
                f"kein Gegenstueck haben - ab jetzt gelten die der Hauptwelt: "
                f"{'; '.join(abweichend[:6])}"
                f"{' ...' if len(abweichend) > 6 else ''}.")

    if geaendert:
        try:
            nc.schreibe_datei(haupt_datei, haupt_name, haupt_wurzel, packart)
            meldungen.insert(0, f"Aus den Dimensions-Welten uebernommen: "
                                f"{', '.join(uebernommen)}.")
        except Exception as exc:  # noqa: BLE001
            meldungen.append(f"Dimensionsdaten konnten NICHT in die Hauptwelt geschrieben "
                             f"werden ({exc}) - die level.dat ist unveraendert. Der "
                             f"Enderdrache kann dadurch erneut erscheinen.")
    return meldungen


def setze_weltnamen(welt: Path, neuer_name: str) -> list[str]:
    """``Data.LevelName`` auf den neuen Ordnernamen setzen.

    Der Server selbst richtet sich nach dem Ordnernamen, aber Weltverwaltungs-Werkzeuge,
    Karten-Renderer und der Einzelspieler zeigen den Namen aus der level.dat an. Bleibt er
    alt, heisst die Welt dort weiter wie beim Quellserver.
    """
    datei = welt / "level.dat"
    if not datei.is_file():
        return []
    try:
        name, wurzel, packart = nc.lies_datei(datei)
    except Exception:  # noqa: BLE001
        return []
    daten = nc.daten_wurzel(wurzel)
    vorher = daten.hole("LevelName")
    if vorher is None or vorher.typ != nc.TAG_STRING or vorher.wert == neuer_name:
        return []
    alt = vorher.wert
    daten.setze("LevelName", nc.Tag(nc.TAG_STRING, neuer_name))
    try:
        nc.schreibe_datei(datei, name, wurzel, packart)
    except Exception:  # noqa: BLE001
        return []
    return [f"Weltname in der level.dat von '{alt}' auf '{neuer_name}' gesetzt."]


def lies_welt_uuid(welt: Path) -> bytes | None:
    """Inhalt von ``uid.dat`` (16 Bytes), oder ``None``.

    Bukkit fuehrt darin die Identitaet der Welt. Jede ``playerdata`` speichert genau diese
    UUID als die Welt, in der der Spieler zuletzt war.
    """
    datei = welt / "uid.dat"
    if not datei.is_file():
        return None
    try:
        roh = datei.read_bytes()
    except OSError:
        return None
    return roh if len(roh) == 16 else None
