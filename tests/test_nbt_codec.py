"""Tests fuer den typerhaltenden NBT-Codec und die Welt-Zusammenfuehrung.

Der Codec schreibt in level.dat-Dateien. Eine level.dat, die Minecraft nicht mehr lesen
kann, kostet die ganze Welt - deshalb steht hier der Round-Trip im Mittelpunkt:
``schreibe(lies(x)) == x``, Byte fuer Byte, auch gegen die echten Dateien des Nutzers.
"""
import gzip
import struct
from pathlib import Path

import pytest

ECHTER_SERVER = Path(r"C:\Users\fridi\Desktop") / "1.21 Spigot Server"


def _compound(felder: dict) -> bytes:
    """NBT-Wurzel mit den gegebenen (tag, name, payload)-Feldern bauen."""
    innen = b""
    for name, (typ, nutz) in felder.items():
        innen += bytes([typ]) + struct.pack(">H", len(name)) + name.encode() + nutz
    return b"\x0a" + struct.pack(">H", 0) + innen + b"\x00"


# ------------------------------------------------------------------- Round-Trip

def test_round_trip_aller_tag_arten(client):
    """Jede Tag-Art muss exakt so wieder herauskommen, wie sie hereinging."""
    from app.services import nbt_codec as nc

    roh = _compound({
        "b": (nc.TAG_BYTE, struct.pack(">b", -7)),
        "s": (nc.TAG_SHORT, struct.pack(">h", -300)),
        "i": (nc.TAG_INT, struct.pack(">i", 123456)),
        "l": (nc.TAG_LONG, struct.pack(">q", -9876543210)),
        "f": (nc.TAG_FLOAT, struct.pack(">f", 1.5)),
        "d": (nc.TAG_DOUBLE, struct.pack(">d", -2.25)),
        "ba": (nc.TAG_BYTE_ARRAY, struct.pack(">i", 3) + b"\x01\x02\x03"),
        "str": (nc.TAG_STRING, struct.pack(">H", 5) + b"hallo"),
        "ia": (nc.TAG_INT_ARRAY, struct.pack(">i", 2) + struct.pack(">ii", 7, -7)),
        "la": (nc.TAG_LONG_ARRAY, struct.pack(">i", 1) + struct.pack(">q", 1 << 40)),
        "liste": (nc.TAG_LIST, bytes([nc.TAG_INT]) + struct.pack(">i", 2)
                  + struct.pack(">ii", 1, 2)),
        "leer": (nc.TAG_LIST, bytes([nc.TAG_END]) + struct.pack(">i", 0)),
        "tief": (nc.TAG_COMPOUND, bytes([nc.TAG_BYTE]) + struct.pack(">H", 1) + b"x"
                 + struct.pack(">b", 1) + b"\x00"),
    })

    name, wurzel = nc.lies(roh)
    assert nc.schreibe(name, wurzel) == roh


def test_typen_bleiben_unterscheidbar(client):
    """Der Sinn des eigenen Codecs: ein Byte darf nicht als Int zurueckkommen."""
    from app.services import nbt_codec as nc

    roh = _compound({
        "alsByte": (nc.TAG_BYTE, struct.pack(">b", 1)),
        "alsInt": (nc.TAG_INT, struct.pack(">i", 1)),
        "alsLong": (nc.TAG_LONG, struct.pack(">q", 1)),
    })
    _, wurzel = nc.lies(roh)

    assert wurzel.hole("alsByte").typ == nc.TAG_BYTE
    assert wurzel.hole("alsInt").typ == nc.TAG_INT
    assert wurzel.hole("alsLong").typ == nc.TAG_LONG
    # Gleicher Wert, verschiedene Typen - nicht gleich.
    assert wurzel.hole("alsByte") != wurzel.hole("alsInt")


def test_gleitkomma_bleibt_bitgenau(client):
    """Floats als Rohbytes halten - sonst veraendert ein Round-Trip NaN oder -0.0."""
    from app.services import nbt_codec as nc

    for rohwert in (b"\x7f\xc0\x00\x00", b"\x80\x00\x00\x00", b"\x00\x00\x00\x00"):
        roh = _compound({"f": (nc.TAG_FLOAT, rohwert)})
        name, wurzel = nc.lies(roh)
        assert nc.schreibe(name, wurzel) == roh
        assert wurzel.hole("f").wert == rohwert


def test_feldreihenfolge_bleibt_erhalten(client):
    from app.services import nbt_codec as nc

    roh = _compound({
        "zuletzt": (nc.TAG_BYTE, struct.pack(">b", 3)),
        "amAnfang": (nc.TAG_BYTE, struct.pack(">b", 1)),
        "mittig": (nc.TAG_BYTE, struct.pack(">b", 2)),
    })
    name, wurzel = nc.lies(roh)
    assert list(wurzel.wert) == ["zuletzt", "amAnfang", "mittig"]
    assert nc.schreibe(name, wurzel) == roh


def test_kaputtes_nbt_wirft(client):
    from app.services import nbt_codec as nc

    with pytest.raises(nc.NbtFehler):
        nc.lies(b"\x01kein compound")
    with pytest.raises(nc.NbtFehler):
        nc.lies(b"\x0a\x00\x00\x03\x00\x04abc")   # endet mitten im Wert


# ---------------------------------------------------- Lesen und Schreiben als Datei

def test_datei_round_trip_gzip(tmp_path, client):
    from app.services import nbt_codec as nc

    roh = _compound({"x": (nc.TAG_INT, struct.pack(">i", 5))})
    pfad = tmp_path / "level.dat"
    pfad.write_bytes(gzip.compress(roh))

    name, wurzel, packart = nc.lies_datei(pfad)
    assert packart == "gzip"
    wurzel.setze("x", nc.Tag(nc.TAG_INT, 9))
    nc.schreibe_datei(pfad, name, wurzel, packart)

    _, neu, _ = nc.lies_datei(pfad)
    assert neu.hole("x").wert == 9


def test_schreiben_prueft_sich_selbst(tmp_path, client):
    """Geschrieben wird nur, was sich danach wieder als derselbe Baum einlesen laesst."""
    from app.services import nbt_codec as nc

    pfad = tmp_path / "level.dat"
    pfad.write_bytes(gzip.compress(_compound({"x": (nc.TAG_INT, struct.pack(">i", 1))})))
    name, wurzel, packart = nc.lies_datei(pfad)
    vorher = pfad.read_bytes()

    # Ein Long-Wert, der nicht in ein Int passt -> das Schreiben muss scheitern ...
    wurzel.setze("kaputt", nc.Tag(nc.TAG_INT, 2 ** 40))
    with pytest.raises(Exception):
        nc.schreibe_datei(pfad, name, wurzel, packart)
    # ... und die Datei unberuehrt lassen.
    assert pfad.read_bytes() == vorher


# ------------------------------------------------- Gegen die ECHTEN Dateien des Nutzers

@pytest.mark.skipif(not (ECHTER_SERVER / "world" / "level.dat").is_file(),
                    reason="echte Welt nicht vorhanden")
@pytest.mark.parametrize("rel", ["world/level.dat", "world_nether/level.dat",
                                 "world_the_end/level.dat"])
def test_echte_level_dat_round_trip(client, rel):
    """Eine echte level.dat hat Felder, die ein Testfall nicht nachstellt: 51 Spielregeln,
    verschachtelte WorldGenSettings, Listen von Compounds, UUID-Int-Arrays."""
    from app.services import nbt_codec as nc

    roh = gzip.decompress((ECHTER_SERVER / rel).read_bytes())
    name, wurzel = nc.lies(roh)
    assert nc.schreibe(name, wurzel) == roh, f"{rel}: Round-Trip nicht byte-identisch"


@pytest.mark.skipif(not (ECHTER_SERVER / "world" / "level.dat").is_file(),
                    reason="echte Welt nicht vorhanden")
def test_echte_level_dat_hat_die_erwarteten_felder(client):
    from app.services import nbt_codec as nc

    _, wurzel = nc.lies(gzip.decompress(
        (ECHTER_SERVER / "world" / "level.dat").read_bytes()))
    daten = nc.daten_wurzel(wurzel)
    for feld in ("LevelName", "GameRules", "DragonFight", "DataVersion", "BorderSize"):
        assert daten.hole(feld) is not None, f"{feld} fehlt"
    assert daten.hole("GameRules").typ == nc.TAG_COMPOUND
    assert daten.hole("DataVersion").typ == nc.TAG_INT


# --------------------------------------------------------- Welt-Zusammenfuehrung

def _level_dat(pfad: Path, felder: dict) -> None:
    """level.dat mit Data-Compound schreiben."""
    from app.services import nbt_codec as nc

    daten = nc.Tag(nc.TAG_COMPOUND, dict(felder))
    wurzel = nc.Tag(nc.TAG_COMPOUND, {"Data": daten})
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_bytes(gzip.compress(nc.schreibe("", wurzel)))


def test_drachenkampf_wird_aus_dem_end_uebernommen(tmp_path, client):
    """Der sichtbarste Verlust beim Zusammenlegen: in world/level.dat steht
    DragonKilled=0, im End 1 - ohne Uebernahme ist der Drache zurueck."""
    from app.services import nbt_codec as nc, world_merge_service as wms

    _level_dat(tmp_path / "world" / "level.dat", {
        "DragonFight": nc.Tag(nc.TAG_COMPOUND, {
            "DragonKilled": nc.Tag(nc.TAG_BYTE, 0),
            "PreviouslyKilled": nc.Tag(nc.TAG_BYTE, 0)}),
        "LevelName": nc.Tag(nc.TAG_STRING, "world"),
    })
    _level_dat(tmp_path / "world_the_end" / "level.dat", {
        "DragonFight": nc.Tag(nc.TAG_COMPOUND, {
            "DragonKilled": nc.Tag(nc.TAG_BYTE, 1),
            "PreviouslyKilled": nc.Tag(nc.TAG_BYTE, 1),
            "ExitPortalLocation": nc.Tag(nc.TAG_INT_ARRAY, [0, 60, 0])}),
        "LevelName": nc.Tag(nc.TAG_STRING, "world_the_end"),
    })

    meldungen = wms.fuehre_level_dat_zusammen(tmp_path / "world",
                                              [tmp_path / "world_the_end"])

    _, wurzel, _ = nc.lies_datei(tmp_path / "world" / "level.dat")
    kampf = nc.daten_wurzel(wurzel).hole("DragonFight")
    assert kampf.hole("DragonKilled").wert == 1
    assert kampf.hole("PreviouslyKilled").wert == 1
    assert kampf.hole("ExitPortalLocation").wert == [0, 60, 0]
    assert any("besiegt" in m for m in meldungen)


def test_abweichende_werte_werden_konkret_benannt(tmp_path, client):
    """Statt pauschal zu warnen: die tatsaechlichen Werte nennen."""
    from app.services import nbt_codec as nc, world_merge_service as wms

    _level_dat(tmp_path / "world" / "level.dat", {
        "Difficulty": nc.Tag(nc.TAG_BYTE, 3),
        "WorldGenSettings": nc.Tag(nc.TAG_COMPOUND,
                                   {"seed": nc.Tag(nc.TAG_LONG, 1111)}),
    })
    _level_dat(tmp_path / "world_nether" / "level.dat", {
        "Difficulty": nc.Tag(nc.TAG_BYTE, 1),
        "WorldGenSettings": nc.Tag(nc.TAG_COMPOUND,
                                   {"seed": nc.Tag(nc.TAG_LONG, 2222)}),
    })

    meldungen = wms.fuehre_level_dat_zusammen(tmp_path / "world",
                                              [tmp_path / "world_nether"])

    text = " ".join(meldungen)
    assert "2222" in text and "1111" in text
    assert "Schwierigkeit" in text


def test_gleiche_werte_werden_nicht_gemeldet(tmp_path, client):
    from app.services import nbt_codec as nc, world_merge_service as wms

    for ordner in ("world", "world_nether"):
        _level_dat(tmp_path / ordner / "level.dat",
                   {"Difficulty": nc.Tag(nc.TAG_BYTE, 2)})

    assert wms.fuehre_level_dat_zusammen(tmp_path / "world",
                                         [tmp_path / "world_nether"]) == []


def test_fehlende_level_dat_ist_kein_fehler(tmp_path, client):
    from app.services import world_merge_service as wms

    (tmp_path / "world").mkdir()
    assert wms.fuehre_level_dat_zusammen(tmp_path / "world", [tmp_path / "fehlt"]) == []


def test_weltname_wird_gesetzt(tmp_path, client):
    from app.services import nbt_codec as nc, world_merge_service as wms

    _level_dat(tmp_path / "welt" / "level.dat",
               {"LevelName": nc.Tag(nc.TAG_STRING, "lobby")})

    meldungen = wms.setze_weltnamen(tmp_path / "welt", "welt")

    _, wurzel, _ = nc.lies_datei(tmp_path / "welt" / "level.dat")
    assert nc.daten_wurzel(wurzel).hole("LevelName").wert == "welt"
    assert meldungen and "lobby" in meldungen[0]


def test_gleicher_weltname_schreibt_nicht(tmp_path, client):
    from app.services import nbt_codec as nc, world_merge_service as wms

    _level_dat(tmp_path / "welt" / "level.dat",
               {"LevelName": nc.Tag(nc.TAG_STRING, "welt")})
    vorher = (tmp_path / "welt" / "level.dat").read_bytes()

    assert wms.setze_weltnamen(tmp_path / "welt", "welt") == []
    assert (tmp_path / "welt" / "level.dat").read_bytes() == vorher


def test_welt_uuid_wird_gelesen(tmp_path, client):
    from app.services import world_merge_service as wms

    welt = tmp_path / "world"
    welt.mkdir()
    assert wms.lies_welt_uuid(welt) is None
    (welt / "uid.dat").write_bytes(bytes(range(16)))
    assert wms.lies_welt_uuid(welt) == bytes(range(16))
    # Falsche Laenge gilt als nicht vorhanden.
    (welt / "uid.dat").write_bytes(b"zu kurz")
    assert wms.lies_welt_uuid(welt) is None
