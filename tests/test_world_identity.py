"""Tests fuer das Umschreiben von Spieler-UUIDs in Weltdaten.

Der Kern ist der Anvil-Schreiber: er fasst Chunk-Daten an, und ein Fehler darin zerstoert
die Welt. Deshalb pruefen diese Tests nicht nur, ob die UUID ersetzt wurde, sondern vor
allem, ob danach noch JEDER Chunk lesbar ist und seine NBT-Struktur behalten hat - und ob
ein Durchlauf OHNE Treffer die Datei byte-identisch laesst.
"""
import gzip
import struct
import zlib
from pathlib import Path

import pytest


SEKTOR = 4096


def _chunk_nbt(zusatz: bytes = b"") -> bytes:
    """Minimales Chunk-NBT: Root-Compound mit xPos/zPos und optionalem Byte-Array."""
    innen = (b"\x03" + struct.pack(">H", 4) + b"xPos" + struct.pack(">i", 0)
             + b"\x03" + struct.pack(">H", 4) + b"zPos" + struct.pack(">i", 0))
    if zusatz:
        innen += (b"\x07" + struct.pack(">H", 5) + b"Owner"
                  + struct.pack(">i", len(zusatz)) + zusatz)
    return b"\x0a" + struct.pack(">H", 0) + innen + b"\x00"


def _region_schreiben(pfad: Path, chunks: dict[int, bytes]) -> None:
    """Anvil-Region mit den gegebenen Chunk-Nutzdaten (roh, unkomprimiert) anlegen."""
    kopf = bytearray(SEKTOR)
    rumpf = bytearray()
    sektor = 2
    for index, roh in sorted(chunks.items()):
        gepackt = zlib.compress(roh)
        block = struct.pack(">I", len(gepackt) + 1) + b"\x02" + gepackt
        gefuellt = block + bytes((-len(block)) % SEKTOR)
        anzahl = len(gefuellt) // SEKTOR
        kopf[index * 4] = (sektor >> 16) & 0xFF
        kopf[index * 4 + 1] = (sektor >> 8) & 0xFF
        kopf[index * 4 + 2] = sektor & 0xFF
        kopf[index * 4 + 3] = anzahl
        rumpf += gefuellt
        sektor += anzahl
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_bytes(bytes(kopf) + bytes(SEKTOR) + bytes(rumpf))


def _chunks_lesen(pfad: Path) -> dict:
    """Jeden belegten Chunk dekomprimieren und als NBT parsen."""
    from app.services.world_bake_service import read_nbt

    daten = pfad.read_bytes()
    ergebnis = {}
    for i in range(1024):
        ort = daten[i * 4:i * 4 + 4]
        off = (ort[0] << 16) | (ort[1] << 8) | ort[2]
        if off == 0 or ort[3] == 0:
            continue
        s = off * SEKTOR
        laenge = int.from_bytes(daten[s:s + 4], "big")
        if laenge < 1 or s + 4 + laenge > len(daten):
            ergebnis[i] = ("kaputt", None)
            continue
        nutz = daten[s + 5:s + 4 + laenge]
        try:
            roh = zlib.decompress(nutz) if daten[s + 4] == 2 else gzip.decompress(nutz)
            ergebnis[i] = ("ok", tuple(sorted(read_nbt(roh).keys())))
        except Exception as exc:  # noqa: BLE001
            ergebnis[i] = (f"kaputt:{exc}", None)
    return ergebnis


ALT = "69f230e1-af2c-4d0e-9885-93d66595a855"      # echte Mojang-UUID (Version 4)
NEU = "79668c7e-c702-3ba1-9b9e-20219be4de43"      # offline erzeugt (Version 3)


# --------------------------------------------------------------------- Grundlagen

def test_offline_uuid_hat_version_3(client):
    """Der Offline-Modus bildet die UUID als MD5 ueber "OfflinePlayer:<name>" - das ergibt
    eine Version-3-UUID."""
    from app.services import world_identity_service as wis

    wert = wis.offline_uuid("Thormak2002")
    assert wert.replace("-", "")[12] == "3"
    assert wis.ist_v4(wert) is False
    # Dieselbe Eingabe muss immer dasselbe ergeben, sonst findet niemand seine Daten.
    assert wis.offline_uuid("Thormak2002") == wert
    assert wis.offline_uuid("Sir_Fangar2004") != wert


def test_uuid_bytes_sind_die_16_bytes_der_uuid(client):
    from app.services import world_identity_service as wis

    assert wis.uuid_bytes(ALT).hex() == "69f230e1af2c4d0e988593d66595a855"
    assert len(wis.uuid_bytes(ALT)) == 16
    assert wis.uuid_bytes("keine-uuid") is None
    assert wis.uuid_bytes("") is None


def test_usercache_wird_gelesen(tmp_path, client):
    from app.services import world_identity_service as wis

    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + ALT + '","expiresOn":"2025-02-25 16:20:18 +0100"}]',
        encoding="utf-8")
    assert wis.lade_usercache(tmp_path) == {ALT: "Thormak2002"}


def test_kaputte_usercache_ist_kein_fehler(tmp_path, client):
    from app.services import world_identity_service as wis

    (tmp_path / "usercache.json").write_text("{kein json", encoding="utf-8")
    assert wis.lade_usercache(tmp_path) == {}
    assert wis.lade_usercache(tmp_path / "gibtsnicht") == {}


# ------------------------------------------------------------------- Zuordnung

def test_zuordnung_nach_offline_braucht_nur_den_namen(tmp_path, client):
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (welt / "playerdata").mkdir(parents=True)
    (welt / "playerdata" / f"{ALT}.dat").write_bytes(b"x")
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + ALT + '"}]', encoding="utf-8")

    zuordnung, offen = wis.baue_zuordnung(welt, tmp_path, "v3")
    assert zuordnung == {ALT: NEU}
    assert offen == []


def test_zuordnung_ohne_namen_bleibt_offen(tmp_path, client):
    """Aus einer v4-UUID allein folgt kein Name - und ohne Namen keine Offline-UUID."""
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (welt / "playerdata").mkdir(parents=True)
    (welt / "playerdata" / f"{ALT}.dat").write_bytes(b"x")

    zuordnung, offen = wis.baue_zuordnung(welt, tmp_path, "v3")
    assert zuordnung == {}
    assert len(offen) == 1 and ALT in offen[0]


def test_passende_uuids_stehen_nicht_in_der_zuordnung(tmp_path, client):
    """Was schon passt, darf nicht angefasst werden."""
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (welt / "playerdata").mkdir(parents=True)
    (welt / "playerdata" / f"{ALT}.dat").write_bytes(b"x")

    zuordnung, offen = wis.baue_zuordnung(welt, tmp_path, "v4")
    assert zuordnung == {} and offen == []


def test_zuordnung_nach_online_nutzt_den_aufloeser(tmp_path, client):
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (welt / "playerdata").mkdir(parents=True)
    (welt / "playerdata" / f"{NEU}.dat").write_bytes(b"x")
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + NEU + '"}]', encoding="utf-8")

    zuordnung, offen = wis.baue_zuordnung(welt, tmp_path, "v4",
                                          namen_aufloeser=lambda n: ALT)
    assert zuordnung == {NEU: ALT}
    assert offen == []


def test_aufloeser_der_scheitert_laesst_den_spieler_offen(tmp_path, client):
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (welt / "playerdata").mkdir(parents=True)
    (welt / "playerdata" / f"{NEU}.dat").write_bytes(b"x")
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + NEU + '"}]', encoding="utf-8")

    def _kaputt(name):
        raise OSError("kein Netz")

    zuordnung, offen = wis.baue_zuordnung(welt, tmp_path, "v4", namen_aufloeser=_kaputt)
    assert zuordnung == {}
    assert len(offen) == 1 and "Thormak2002" in offen[0]


def test_zuordnung_erfasst_auch_spieler_ohne_datei(tmp_path, client):
    """Ein Haustier-Besitzer muss keine playerdata haben - die usercache kennt ihn aber."""
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    welt.mkdir()
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + ALT + '"}]', encoding="utf-8")

    zuordnung, offen = wis.baue_zuordnung(welt, tmp_path, "v3")
    assert zuordnung == {ALT: NEU}


# ------------------------------------------------------- Anvil-Schreiber (der Kern)

def test_region_ohne_treffer_bleibt_byte_identisch(tmp_path, client):
    """Der wichtigste Test: wo nichts zu ersetzen ist, darf die Datei nicht angefasst
    werden - weder neu komprimiert noch neu angeordnet."""
    from app.services import world_identity_service as wis

    pfad = tmp_path / "r.0.0.mca"
    _region_schreiben(pfad, {0: _chunk_nbt(b"keine uuid hier"), 5: _chunk_nbt()})
    vorher = pfad.read_bytes()

    geaendert = wis.region_umschreiben(pfad, wis._ersetzungen({ALT: NEU}))

    assert geaendert == 0
    assert pfad.read_bytes() == vorher


def test_region_mit_treffer_bleibt_vollstaendig_lesbar(tmp_path, client):
    from app.services import world_identity_service as wis

    pfad = tmp_path / "r.0.0.mca"
    _region_schreiben(pfad, {
        0: _chunk_nbt(wis.uuid_bytes(ALT)),
        3: _chunk_nbt(b"unbeteiligt"),
        7: _chunk_nbt(wis.uuid_bytes(ALT) + b"x" + wis.uuid_bytes(ALT)),
    })
    vorher = _chunks_lesen(pfad)

    geaendert = wis.region_umschreiben(pfad, wis._ersetzungen({ALT: NEU}))
    nachher = _chunks_lesen(pfad)

    assert geaendert == 2
    # Alle Chunks noch da, alle lesbar, gleiche NBT-Struktur.
    assert set(vorher) == set(nachher)
    assert all(z[0] == "ok" for z in nachher.values())
    assert all(vorher[i][1] == nachher[i][1] for i in vorher)
    # Alte UUID weg, neue drin.
    roh = pfad.read_bytes()
    assert wis.uuid_bytes(ALT) not in zlib.decompress(_erster_chunk(roh))


def _erster_chunk(daten: bytes) -> bytes:
    ort = daten[0:4]
    off = (ort[0] << 16) | (ort[1] << 8) | ort[2]
    s = off * SEKTOR
    laenge = int.from_bytes(daten[s:s + 4], "big")
    return daten[s + 5:s + 4 + laenge]


def test_region_laesst_kaputte_chunks_in_ruhe(tmp_path, client):
    """Ein unlesbarer Chunk darf den Rest nicht verhindern und nicht veraendert werden."""
    from app.services import world_identity_service as wis

    pfad = tmp_path / "r.0.0.mca"
    _region_schreiben(pfad, {0: _chunk_nbt(wis.uuid_bytes(ALT))})
    # Chunk 1 mit Muell belegen, der sich nicht entpacken laesst.
    daten = bytearray(pfad.read_bytes())
    naechster = len(daten) // SEKTOR
    daten += bytes(SEKTOR)
    block = struct.pack(">I", 5) + b"\x02" + b"Muell"
    daten[naechster * SEKTOR:naechster * SEKTOR + len(block)] = block
    daten[4] = (naechster >> 16) & 0xFF
    daten[5] = (naechster >> 8) & 0xFF
    daten[6] = naechster & 0xFF
    daten[7] = 1
    pfad.write_bytes(bytes(daten))

    geaendert = wis.region_umschreiben(pfad, wis._ersetzungen({ALT: NEU}))

    assert geaendert == 1
    nachher = _chunks_lesen(pfad)
    # Beide Slots noch belegt - der kaputte ist unveraendert kaputt, nicht verschwunden.
    assert set(nachher) == {0, 1}
    assert nachher[0][0] == "ok"
    assert nachher[1][0].startswith("kaputt")


def test_region_zu_klein_wird_nicht_angefasst(tmp_path, client):
    from app.services import world_identity_service as wis

    pfad = tmp_path / "winzig.mca"
    pfad.write_bytes(b"zu kurz")
    assert wis.region_umschreiben(pfad, wis._ersetzungen({ALT: NEU})) == 0
    assert pfad.read_bytes() == b"zu kurz"


def test_fehlende_region_ist_kein_fehler(tmp_path, client):
    from app.services import world_identity_service as wis

    assert wis.region_umschreiben(tmp_path / "gibtsnicht.mca",
                                  wis._ersetzungen({ALT: NEU})) == 0


# ------------------------------------------------------------------ .dat-Dateien

def test_dat_datei_wird_umgeschrieben(tmp_path, client):
    from app.services import world_identity_service as wis

    pfad = tmp_path / "level.dat"
    pfad.write_bytes(gzip.compress(b"vorne" + wis.uuid_bytes(ALT) + b"hinten"))

    assert wis.dat_umschreiben(pfad, wis._ersetzungen({ALT: NEU})) is True
    inhalt = gzip.decompress(pfad.read_bytes())
    assert wis.uuid_bytes(NEU) in inhalt
    assert wis.uuid_bytes(ALT) not in inhalt
    # Der Rest bleibt unangetastet.
    assert inhalt.startswith(b"vorne") and inhalt.endswith(b"hinten")


def test_dat_ohne_treffer_bleibt_unveraendert(tmp_path, client):
    from app.services import world_identity_service as wis

    pfad = tmp_path / "level.dat"
    pfad.write_bytes(gzip.compress(b"nichts dabei"))
    vorher = pfad.read_bytes()
    assert wis.dat_umschreiben(pfad, wis._ersetzungen({ALT: NEU})) is False
    assert pfad.read_bytes() == vorher


def test_unkomprimierte_dat_wird_auch_behandelt(tmp_path, client):
    from app.services import world_identity_service as wis

    pfad = tmp_path / "roh.dat"
    pfad.write_bytes(b"a" + wis.uuid_bytes(ALT))
    assert wis.dat_umschreiben(pfad, wis._ersetzungen({ALT: NEU})) is True
    assert pfad.read_bytes() == b"a" + wis.uuid_bytes(NEU)


# ----------------------------------------------------------- Dateinamen umbenennen

def test_spielerdateien_werden_umbenannt(tmp_path, client):
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    for ordner, endung in (("playerdata", ".dat"), ("stats", ".json"),
                           ("advancements", ".json")):
        (welt / ordner).mkdir(parents=True)
        (welt / ordner / f"{ALT}{endung}").write_text("x", encoding="utf-8")

    assert wis.benenne_spielerdateien_um(welt, {ALT: NEU}) == 3
    assert (welt / "playerdata" / f"{NEU}.dat").is_file()
    assert (welt / "stats" / f"{NEU}.json").is_file()
    assert not (welt / "playerdata" / f"{ALT}.dat").exists()


def test_spielerdateien_im_26x_ordner_werden_umbenannt(tmp_path, client):
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (welt / "players" / "data").mkdir(parents=True)
    (welt / "players" / "data" / f"{ALT}.dat").write_text("x", encoding="utf-8")

    assert wis.benenne_spielerdateien_um(welt, {ALT: NEU}) == 1
    assert (welt / "players" / "data" / f"{NEU}.dat").is_file()


def test_vorhandener_zielstand_wird_nicht_ueberschrieben(tmp_path, client):
    """Liegt unter dem neuen Namen schon etwas, waere das Ueberschreiben Datenverlust."""
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (welt / "playerdata").mkdir(parents=True)
    (welt / "playerdata" / f"{ALT}.dat").write_text("alt", encoding="utf-8")
    (welt / "playerdata" / f"{NEU}.dat").write_text("schon da", encoding="utf-8")

    assert wis.benenne_spielerdateien_um(welt, {ALT: NEU}) == 0
    assert (welt / "playerdata" / f"{NEU}.dat").read_text(encoding="utf-8") == "schon da"


# --------------------------------------------------------- Alles zusammen, eine Welt

def test_schreibe_welt_um_fasst_alles_an(tmp_path, client):
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (welt / "playerdata").mkdir(parents=True)
    (welt / "playerdata" / f"{ALT}.dat").write_bytes(
        gzip.compress(b"reittier" + wis.uuid_bytes(ALT)))
    (welt / "data").mkdir()
    (welt / "data" / "raids.dat").write_bytes(gzip.compress(wis.uuid_bytes(ALT)))
    _region_schreiben(welt / "entities" / "r.0.0.mca",
                      {0: _chunk_nbt(wis.uuid_bytes(ALT))})
    _region_schreiben(welt / "dimensions" / "minecraft" / "the_nether" / "region"
                      / "r.0.0.mca", {0: _chunk_nbt(wis.uuid_bytes(ALT))})

    ergebnis = wis.schreibe_welt_um(welt, {ALT: NEU})

    assert ergebnis["dateien"] == 1          # playerdata umbenannt
    assert ergebnis["chunks"] == 2           # entities + Nether-Region
    assert ergebnis["dat"] >= 2              # playerdata-Inhalt + raids.dat
    assert (welt / "playerdata" / f"{NEU}.dat").is_file()


def test_leere_zuordnung_aendert_nichts(tmp_path, client):
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    _region_schreiben(welt / "entities" / "r.0.0.mca",
                      {0: _chunk_nbt(wis.uuid_bytes(ALT))})
    vorher = (welt / "entities" / "r.0.0.mca").read_bytes()

    ergebnis = wis.schreibe_welt_um(welt, {})

    assert ergebnis == {"dateien": 0, "chunks": 0, "dat": 0}
    assert (welt / "entities" / "r.0.0.mca").read_bytes() == vorher


# ------------------------------------------------- Gegen die ECHTE Welt des Nutzers

ECHTE_WELT = Path(r"C:\Users\fridi\Desktop") / "1.21 Spigot Server" / "world"


@pytest.mark.skipif(not (ECHTE_WELT / "entities").is_dir(),
                    reason="echte Welt nicht vorhanden")
def test_echte_regionen_bleiben_nach_dem_umschreiben_lesbar(tmp_path, client):
    """Gegen echte Minecraft-Daten: eine Region mit Treffern umschreiben und danach JEDEN
    Chunk wieder als NBT parsen. Ein selbstgebauter Testfall kann Eigenheiten echter
    Dateien (Fragmentierung, Luecken, grosse Chunks) nicht nachstellen."""
    import shutil

    from app.services import world_identity_service as wis

    paare = wis._ersetzungen({ALT: NEU})
    geprueft = 0
    for quelle in sorted(ECHTE_WELT.glob("entities/*.mca"))[:12]:
        kopie = tmp_path / quelle.name
        shutil.copy2(quelle, kopie)
        vorher = _chunks_lesen(kopie)
        wis.region_umschreiben(kopie, paare)
        nachher = _chunks_lesen(kopie)

        assert set(vorher) == set(nachher), f"{quelle.name}: Chunk-Slots verschoben"
        kaputt_vorher = sum(1 for z in vorher.values() if z[0] != "ok")
        kaputt_nachher = sum(1 for z in nachher.values() if z[0] != "ok")
        assert kaputt_nachher == kaputt_vorher, f"{quelle.name}: Chunks beschaedigt"
        for i in vorher:
            if vorher[i][0] == "ok":
                assert vorher[i][1] == nachher[i][1], f"{quelle.name}: NBT-Struktur geaendert"
        geprueft += 1
    assert geprueft > 0


# ------------------------------------------- Spieler mit zwei Staenden

def _spielerdatei(welt, kennung, inhalt, alter_tage=0):
    import os
    import time

    ordner = welt / "playerdata"
    ordner.mkdir(parents=True, exist_ok=True)
    datei = ordner / f"{kennung}.dat"
    datei.write_bytes(gzip.compress(inhalt))
    if alter_tage:
        stempel = time.time() - alter_tage * 86400
        os.utime(datei, (stempel, stempel))
    return datei


def test_doppelte_staende_werden_erkannt(tmp_path, client):
    """Entsteht, wenn ein Server die Anmeldeart gewechselt hat - derselbe Mensch hat dann
    zwei Spielerdaten."""
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    offline = wis.offline_uuid("Thormak2002")
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + ALT + '"}]', encoding="utf-8")
    _spielerdatei(welt, ALT, b"neu")
    _spielerdatei(welt, offline, b"alt", alter_tage=30)

    treffer = wis.doppelte_staende(welt, tmp_path)

    assert len(treffer) == 1
    assert treffer[0]["name"] == "Thormak2002"
    assert treffer[0]["juenger"] == "echt"
    assert treffer[0]["offline"]["uuid"] == offline


def test_ein_einzelner_stand_ist_kein_doppelter(tmp_path, client):
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + ALT + '"}]', encoding="utf-8")
    _spielerdatei(welt, ALT, b"nur einer")

    assert wis.doppelte_staende(welt, tmp_path) == []


def test_juengerer_stand_gewinnt(tmp_path, client):
    """Der zuletzt gespielte Stand ist der, den der Spieler erwartet."""
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    offline = wis.offline_uuid("Thormak2002")
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + ALT + '"}]', encoding="utf-8")
    _spielerdatei(welt, ALT, b"NEUER-STAND")
    _spielerdatei(welt, offline, b"ALTER-STAND", alter_tage=30)

    # Umzug auf einen Offline-Server: echt -> offline.
    ergebnis = wis.schreibe_welt_um(welt, {ALT: offline})

    gewonnen = gzip.decompress((welt / "playerdata" / f"{offline}.dat").read_bytes())
    assert gewonnen == b"NEUER-STAND"
    assert len(ergebnis["aufgeloest"]) == 1
    assert ergebnis["aufgeloest"][0]["behalten"] == "alt"


def test_aelterer_stand_wird_nicht_geloescht(tmp_path, client):
    """Falls die Wahl falsch war, muss sie umkehrbar bleiben."""
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    offline = wis.offline_uuid("Thormak2002")
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + ALT + '"}]', encoding="utf-8")
    _spielerdatei(welt, ALT, b"NEUER-STAND")
    _spielerdatei(welt, offline, b"ALTER-STAND", alter_tage=30)

    wis.schreibe_welt_um(welt, {ALT: offline})

    aufgehoben = list((welt / "playerdata").glob("*.ersetzt"))
    assert len(aufgehoben) == 1
    assert gzip.decompress(aufgehoben[0].read_bytes()) == b"ALTER-STAND"


def test_vorhandener_juengerer_stand_bleibt(tmp_path, client):
    """Die Gegenrichtung: ist der Stand am Zielnamen juenger, gewinnt er."""
    from app.services import world_identity_service as wis

    welt = tmp_path / "world"
    offline = wis.offline_uuid("Thormak2002")
    (tmp_path / "usercache.json").write_text(
        '[{"name":"Thormak2002","uuid":"' + ALT + '"}]', encoding="utf-8")
    _spielerdatei(welt, ALT, b"ALTER-STAND", alter_tage=30)
    _spielerdatei(welt, offline, b"NEUER-STAND")

    ergebnis = wis.schreibe_welt_um(welt, {ALT: offline})

    assert gzip.decompress(
        (welt / "playerdata" / f"{offline}.dat").read_bytes()) == b"NEUER-STAND"
    assert ergebnis["aufgeloest"][0]["behalten"] == "neu"
    # Auch hier bleibt der unterlegene Stand erhalten.
    assert list((welt / "playerdata").glob("*.ersetzt"))
