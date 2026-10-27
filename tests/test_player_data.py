"""Tests fuer die Umstellung von Spieler-UUIDs ausserhalb der Weltdaten.

Der Unterschied zu world_identity_service: dort steht die UUID als 16 Bytes in NBT, hier
als TEXT - in ops.json, in Plugin-YAMLs, in Dateinamen. Ein Byte-Tausch erwischt davon
nichts, und ohne diese Umstellung kommt nach einem Wechsel der Anmeldeart niemand mehr
durch die Whitelist.
"""
import json
from pathlib import Path

import pytest

ALT = "69f230e1-af2c-4d0e-9885-93d66595a855"      # echte Mojang-UUID (Version 4)
NEU = "79668c7e-c702-3ba1-9b9e-20219be4de43"      # offline erzeugt (Version 3)


def _paare(zuordnung):
    from app.services import player_data_service as pds

    return pds._paare(zuordnung)


# ------------------------------------------------------------------ Schreibweisen

def test_alle_schreibweisen_werden_erfasst(client):
    """Plugins schreiben die UUID mal mit, mal ohne Bindestriche, mal in Grossbuchstaben."""
    from app.services import player_data_service as pds

    formen = pds._schreibweisen(ALT)
    assert ALT in formen
    assert ALT.upper() in formen
    assert ALT.replace("-", "") in formen
    assert ALT.replace("-", "").upper() in formen


def test_ungueltige_uuid_ergibt_keine_paare(client):
    from app.services import player_data_service as pds

    assert pds._schreibweisen("kein-uuid") == []
    assert pds._paare({"unsinn": "auch-unsinn"}) == []


def test_laengste_form_zuerst(client):
    """Sonst zerlegt die kurze Form (ohne Bindestriche) die lange."""
    paare = _paare({ALT: NEU})
    laengen = [len(a) for a, _ in paare]
    assert laengen == sorted(laengen, reverse=True)


def test_text_in_allen_schreibweisen_ersetzt(tmp_path, client):
    from app.services import player_data_service as pds

    datei = tmp_path / "daten.yml"
    datei.write_text(
        f"spieler: {ALT}\ngross: {ALT.upper()}\nkompakt: {ALT.replace('-', '')}\n",
        encoding="utf-8")

    treffer = pds.schreibe_textdatei_um(datei, _paare({ALT: NEU}))
    inhalt = datei.read_text(encoding="utf-8")

    assert treffer == 3
    assert ALT not in inhalt and ALT.upper() not in inhalt
    assert NEU in inhalt and NEU.upper() in inhalt
    assert NEU.replace("-", "") in inhalt


def test_datei_ohne_treffer_bleibt_unveraendert(tmp_path, client):
    from app.services import player_data_service as pds

    datei = tmp_path / "daten.yml"
    datei.write_text("nichts: hier\n", encoding="utf-8")
    vorher = datei.read_bytes()

    assert pds.schreibe_textdatei_um(datei, _paare({ALT: NEU})) == 0
    assert datei.read_bytes() == vorher


def test_binaerdatei_wird_nicht_beschaedigt(tmp_path, client):
    """Eine Datei mit fremder Kodierung bleibt unberuehrt, statt beim Zurueckschreiben
    kaputtzugehen."""
    from app.services import player_data_service as pds

    datei = tmp_path / "daten.yml"
    datei.write_bytes(b"\xff\xfe\x00binaer" + ALT.encode())
    vorher = datei.read_bytes()

    assert pds.schreibe_textdatei_um(datei, _paare({ALT: NEU})) == 0
    assert datei.read_bytes() == vorher


# ------------------------------------------------------------------ Zugangslisten

def test_ops_json_wird_umgestellt(tmp_path, client):
    from app.services import player_data_service as pds

    (tmp_path / "ops.json").write_text(json.dumps(
        [{"uuid": ALT, "name": "Thormak2002", "level": 4}]), encoding="utf-8")
    (tmp_path / "whitelist.json").write_text(json.dumps(
        [{"uuid": ALT, "name": "Thormak2002"}]), encoding="utf-8")

    meldungen = pds.stelle_zugangslisten_um(tmp_path, {ALT: NEU})

    ops = json.loads((tmp_path / "ops.json").read_text(encoding="utf-8"))
    assert ops[0]["uuid"] == NEU
    assert ops[0]["name"] == "Thormak2002"      # Name bleibt
    assert ops[0]["level"] == 4                  # Stufe bleibt
    assert meldungen and "ops.json" in meldungen[0]


def test_zugangslisten_werden_uebernommen(tmp_path, client):
    from app.services import player_data_service as pds

    quelle, ziel = tmp_path / "q", tmp_path / "z"
    quelle.mkdir()
    ziel.mkdir()
    (quelle / "ops.json").write_text("[]", encoding="utf-8")
    (quelle / "banned-players.json").write_text(
        json.dumps([{"uuid": ALT, "name": "Stoerer"}]), encoding="utf-8")

    meldungen = pds.uebernimm_zugangslisten(quelle, ziel)

    assert (ziel / "ops.json").is_file()
    banns = json.loads((ziel / "banned-players.json").read_text(encoding="utf-8"))
    assert banns[0]["name"] == "Stoerer"
    assert meldungen and "uebernommen" in meldungen[0]


def test_fehlende_zugangslisten_werden_gemeldet(tmp_path, client):
    from app.services import player_data_service as pds

    quelle, ziel = tmp_path / "q", tmp_path / "z"
    quelle.mkdir()
    ziel.mkdir()
    meldungen = pds.uebernimm_zugangslisten(quelle, ziel)
    assert meldungen and "keine Zugangslisten" in meldungen[0]


def test_unpassende_uuid_art_in_listen_wird_gemeldet(tmp_path, client):
    from app.services import player_data_service as pds

    (tmp_path / "ops.json").write_text(json.dumps(
        [{"uuid": ALT, "name": "Thormak2002"}]), encoding="utf-8")

    # Zielserver erwartet offline erzeugte UUIDs, in der Liste steht eine echte.
    meldungen = pds.pruefe_zugangslisten_uuid_art(tmp_path, "v3")
    assert meldungen and "Thormak2002" in meldungen[0]

    # Passt es, gibt es nichts zu melden.
    assert pds.pruefe_zugangslisten_uuid_art(tmp_path, "v4") == []


# --------------------------------------------------------------------- Plugin-Daten

def test_plugin_yaml_und_dateinamen_werden_umgestellt(tmp_path, client):
    from app.services import player_data_service as pds

    base = tmp_path / "server"
    (base / "plugins" / "Essentials" / "userdata").mkdir(parents=True)
    (base / "plugins" / "Essentials" / "userdata" / f"{ALT}.yml").write_text(
        f"homes:\n  zuhause:\n    world: welt\nlastAccountName: Thormak2002\n",
        encoding="utf-8")
    (base / "plugins" / "DeadChest").mkdir(parents=True)
    (base / "plugins" / "DeadChest" / "chests.yml").write_text(
        f"chest1:\n  owner: {ALT}\n", encoding="utf-8")

    meldungen = pds.stelle_plugin_daten_um(base, {ALT: NEU})

    assert (base / "plugins" / "Essentials" / "userdata" / f"{NEU}.yml").is_file()
    assert not (base / "plugins" / "Essentials" / "userdata" / f"{ALT}.yml").exists()
    assert NEU in (base / "plugins" / "DeadChest" / "chests.yml").read_text(encoding="utf-8")
    assert meldungen and "umgestellt" in meldungen[0]


def test_datenbanken_werden_nur_gemeldet(tmp_path, client):
    """Ein falscher Eingriff in ein fremdes Datenbankschema faellt spaeter auf als in
    einer Textdatei - deshalb nur melden."""
    from app.services import player_data_service as pds

    base = tmp_path / "server"
    (base / "plugins" / "LuckPerms").mkdir(parents=True)
    db = base / "plugins" / "LuckPerms" / "luckperms-h2.sqlite"
    db.write_bytes(b"SQLite format 3\x00" + ALT.encode())
    vorher = db.read_bytes()

    meldungen = pds.stelle_plugin_daten_um(base, {ALT: NEU})

    assert db.read_bytes() == vorher
    assert any("Datenbanken" in m for m in meldungen)


def test_fremde_uuids_bleiben_unberuehrt(tmp_path, client):
    from app.services import player_data_service as pds

    fremd = "11111111-2222-4333-8444-555555555555"
    base = tmp_path / "server"
    (base / "plugins" / "X").mkdir(parents=True)
    datei = base / "plugins" / "X" / "daten.yml"
    datei.write_text(f"a: {ALT}\nb: {fremd}\n", encoding="utf-8")

    pds.stelle_plugin_daten_um(base, {ALT: NEU})
    inhalt = datei.read_text(encoding="utf-8")

    assert NEU in inhalt
    assert fremd in inhalt      # fremde UUID unveraendert


def test_ohne_plugins_ordner_passiert_nichts(tmp_path, client):
    from app.services import player_data_service as pds

    assert pds.stelle_plugin_daten_um(tmp_path, {ALT: NEU}) == []


# ------------------------------------------------------------- Weltname im Inhalt

def test_weltname_im_inhalt_wird_ersetzt(tmp_path, client):
    from app.services import player_data_service as pds

    base = tmp_path / "server"
    (base / "plugins" / "Essentials").mkdir(parents=True)
    datei = base / "plugins" / "Essentials" / "homes.yml"
    datei.write_text("zuhause:\n  world: lobby\nnether:\n  world: lobby_nether\n",
                     encoding="utf-8")

    meldungen = pds.ersetze_weltnamen_in_plugin_daten(base, "lobby", "welt")
    inhalt = datei.read_text(encoding="utf-8")

    assert "world: welt\n" in inhalt
    assert "world: welt_nether\n" in inhalt
    assert "lobby" not in inhalt
    assert meldungen


def test_aehnliche_weltnamen_bleiben_unberuehrt(tmp_path, client):
    """Aus "lobby2" darf nicht "welt2" werden - ersetzt wird nur der ganze Name."""
    from app.services import player_data_service as pds

    base = tmp_path / "server"
    (base / "plugins" / "X").mkdir(parents=True)
    datei = base / "plugins" / "X" / "daten.yml"
    datei.write_text("a: lobby\nb: lobby2\nc: meinelobby\nd: lobby-alt\n", encoding="utf-8")

    pds.ersetze_weltnamen_in_plugin_daten(base, "lobby", "welt")
    inhalt = datei.read_text(encoding="utf-8")

    assert "a: welt\n" in inhalt
    assert "b: lobby2\n" in inhalt
    assert "c: meinelobby\n" in inhalt
    assert "d: lobby-alt\n" in inhalt


def test_gleicher_weltname_aendert_nichts(tmp_path, client):
    from app.services import player_data_service as pds

    base = tmp_path / "server"
    (base / "plugins" / "X").mkdir(parents=True)
    (base / "plugins" / "X" / "d.yml").write_text("w: welt\n", encoding="utf-8")

    assert pds.ersetze_weltnamen_in_plugin_daten(base, "welt", "welt") == []


# ------------------------------------------------------------- Ordner umbenennen

def test_uuid_ordner_werden_umbenannt(tmp_path, client):
    """Manche Plugins legen einen ORDNER je Spieler an."""
    from app.services import player_data_service as pds

    wurzel = tmp_path / "plugins"
    (wurzel / "Jobs" / ALT).mkdir(parents=True)
    (wurzel / "Jobs" / ALT / "stand.yml").write_text("level: 30", encoding="utf-8")

    umbenannt = pds.benenne_uuid_dateien_um(wurzel, {ALT: NEU})

    assert (wurzel / "Jobs" / NEU / "stand.yml").read_text(encoding="utf-8") == "level: 30"
    assert umbenannt


def test_kompakte_schreibweise_im_dateinamen(tmp_path, client):
    from app.services import player_data_service as pds

    wurzel = tmp_path / "plugins"
    wurzel.mkdir()
    (wurzel / f"{ALT.replace('-', '')}.dat").write_text("x", encoding="utf-8")

    pds.benenne_uuid_dateien_um(wurzel, {ALT: NEU})

    assert (wurzel / f"{NEU.replace('-', '')}.dat").is_file()


def test_vorhandenes_ziel_wird_nicht_ueberschrieben(tmp_path, client):
    from app.services import player_data_service as pds

    wurzel = tmp_path / "plugins"
    wurzel.mkdir()
    (wurzel / f"{ALT}.yml").write_text("alt", encoding="utf-8")
    (wurzel / f"{NEU}.yml").write_text("schon da", encoding="utf-8")

    pds.benenne_uuid_dateien_um(wurzel, {ALT: NEU})

    assert (wurzel / f"{NEU}.yml").read_text(encoding="utf-8") == "schon da"
