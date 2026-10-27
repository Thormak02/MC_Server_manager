"""Tests fuer die Uebernahme von Welten, Configs und Plugins/Mods zwischen Servern.

Der Schwerpunkt liegt auf dem, was beim reinen Ordner-Kopieren schiefgeht und dem Nutzer
NICHT auffaellt:

* Nether und End liegen bei der Bukkit-Familie in eigenen Weltordnern, bei Vanilla/Forge
  im Hauptweltordner. Wird das nicht umgebaut, laedt die Welt trotzdem - nur generiert der
  Server die Dimensionen neu, und die Bauten sind weg.
* Bleibt ein alter ``world_nether`` des Zielservers neben der neuen Welt stehen, laedt der
  Server den ALTEN Nether zur NEUEN Overworld dazu.
* ``uid.dat`` mitzukopieren gibt zwei Welten mit identischer UUID.
* Eine Welt von einer neueren auf eine aeltere Minecraft-Version zu schieben zerstoert sie.
"""
import gzip
import struct
from pathlib import Path

import pytest


# --------------------------------------------------------------------- Hilfsmittel

def _login_admin(client):
    antwort = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303


def _level_dat(pfad: Path, data_version: int = 4082) -> None:
    """Minimale level.dat anlegen: gzip-NBT mit ``Data.DataVersion``."""
    innen = (b"\x03" + struct.pack(">H", 11) + b"DataVersion"
             + struct.pack(">i", data_version) + b"\x00")
    wurzel = (b"\x0a" + struct.pack(">H", 0)
              + b"\x0a" + struct.pack(">H", 4) + b"Data" + innen
              + b"\x00")
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_bytes(gzip.compress(wurzel))


def _dimension(ordner: Path, inhalt: bytes = b"region-daten") -> None:
    (ordner / "region").mkdir(parents=True, exist_ok=True)
    (ordner / "region" / "r.0.0.mca").write_bytes(inhalt)
    (ordner / "entities").mkdir(parents=True, exist_ok=True)
    (ordner / "entities" / "r.0.0.mca").write_bytes(inhalt)


def _welt_getrennt(base: Path, name: str = "world", *, data_version: int = 4082,
                   spielerdaten: bool = True, nether: bool = True, end: bool = True) -> None:
    """Weltsatz im Bukkit-Stil: Overworld + eigene Ordner fuer Nether und End."""
    haupt = base / name
    _dimension(haupt, b"overworld")
    _level_dat(haupt / "level.dat", data_version)
    (haupt / "session.lock").write_bytes(b"lock")
    (haupt / "uid.dat").write_bytes(b"welt-uuid")
    if spielerdaten:
        (haupt / "playerdata").mkdir(exist_ok=True)
        (haupt / "playerdata" / "spieler.dat").write_bytes(b"inventar")
        (haupt / "stats").mkdir(exist_ok=True)
        (haupt / "stats" / "spieler.json").write_text("{}", encoding="utf-8")
        (haupt / "advancements").mkdir(exist_ok=True)
        (haupt / "advancements" / "spieler.json").write_text("{}", encoding="utf-8")
    for rest, dim, marke in (("nether", "DIM-1", b"nether"), ("the_end", "DIM1", b"end")):
        if rest == "nether" and not nether:
            continue
        if rest == "the_end" and not end:
            continue
        aussen = base / f"{name}_{rest}"
        _dimension(aussen / dim, marke)
        _level_dat(aussen / "level.dat", data_version)
        (aussen / "uid.dat").write_bytes(b"dim-uuid")
        (aussen / "session.lock").write_bytes(b"lock")


def _welt_gemeinsam(base: Path, name: str = "world", *, data_version: int = 4082,
                    spielerdaten: bool = True) -> None:
    """Weltsatz im Vanilla-Stil: alles im Hauptweltordner, Dimensionen als Unterordner."""
    haupt = base / name
    _dimension(haupt, b"overworld")
    _level_dat(haupt / "level.dat", data_version)
    (haupt / "session.lock").write_bytes(b"lock")
    if spielerdaten:
        (haupt / "playerdata").mkdir(exist_ok=True)
        (haupt / "playerdata" / "spieler.dat").write_bytes(b"inventar")
    _dimension(haupt / "DIM-1", b"nether")
    _dimension(haupt / "DIM1", b"end")


def _server(db, base: Path, *, name: str, slug: str, typ: str = "paper",
            version: str = "26.2", level: str = "world", port: int = 25599):
    """Testserver anlegen.

    ACHTUNG bei der Version: ab 26.1 nutzt Paper das Vanilla-Weltlayout, davor das von
    Bukkit. Wer ein bestimmtes Layout testen will, setzt die Version deshalb ausdruecklich
    statt sich auf diesen Default zu verlassen.
    """
    from app.models.server import Server

    base.mkdir(parents=True, exist_ok=True)
    (base / "server.properties").write_text(
        f"level-name={level}\nserver-port={port}\nmax-players=20\ndifficulty=normal\n",
        encoding="utf-8",
    )
    srv = Server(name=name, slug=slug, server_type=typ, mc_version=version,
                 base_path=str(base), port=port, status="stopped", start_mode="command",
                 start_command="java -jar server.jar")
    db.add(srv)
    db.commit()
    db.refresh(srv)
    return srv


def _ohne_backup(monkeypatch, tmp_path):
    """create_backup durch eine Attrappe ersetzen, die eine ECHTE (winzige) ZIP schreibt.

    Der Backup-Ablageort ist nicht Teil dieser Tests - ohne Attrappe braeuchte jeder Lauf
    einen konfigurierten Speicherort. Die Attrappe muss aber eine brauchbare Sicherung
    liefern: der Transfer prueft vor dem Loeschen der alten Welt, ob sie wirklich in der
    Sicherung steht.
    """
    import zipfile

    from app.services import backup_service, transfer_service

    def _attrappe(db, *, server, **kwargs):
        pfad = tmp_path / f"sicherung-{server.id}.zip"
        welt = transfer_service.level_name(server)
        with zipfile.ZipFile(pfad, "w") as archiv:
            for ordner in (welt, f"{welt}_nether", f"{welt}_the_end"):
                if (Path(server.base_path) / ordner).is_dir():
                    archiv.writestr(f"{ordner}/level.dat", "x")

        class _Attrappe:
            backup_name = "test-sicherung"
            storage_path = str(pfad)

        return _Attrappe()

    monkeypatch.setattr(backup_service, "create_backup", _attrappe)


def _properties(base: Path) -> dict:
    werte = {}
    for zeile in (base / "server.properties").read_text(encoding="utf-8").splitlines():
        if "=" in zeile and not zeile.strip().startswith("#"):
            schluessel, wert = zeile.split("=", 1)
            werte[schluessel.strip()] = wert.strip()
    return werte


# ------------------------------------------------------------------- Erkennung

def test_level_name_aus_properties(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a", level="lobby")
        assert transfer_service.level_name(srv) == "lobby"


def test_level_name_default_ohne_datei(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a")
        (tmp_path / "a" / "server.properties").unlink()
        assert transfer_service.level_name(srv) == "world"


def test_level_name_mit_pfadanteil_wird_verworfen(tmp_path, client):
    """Ein level-name mit Pfadanteilen wuerde den Transfer aus dem Serverordner heraus
    schreiben lassen."""
    from app.db.session import SessionLocal
    from app.services import transfer_service

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a", level="../woanders")
        assert transfer_service.level_name(srv) == "world"


def test_stil_wird_an_den_ordnern_erkannt(tmp_path, client):
    from app.services import transfer_service as ts

    _welt_getrennt(tmp_path / "bukkit")
    _welt_gemeinsam(tmp_path / "vanilla")
    assert ts.stil_auf_platte(tmp_path / "bukkit", "world") == ts.STIL_GETRENNT
    assert ts.stil_auf_platte(tmp_path / "vanilla", "world") == ts.STIL_GEMEINSAM


def test_stil_ohne_dimensionen_ist_nicht_entscheidbar(tmp_path, client):
    """Eine Welt, in der niemand im Nether war, sieht in beiden Stilen gleich aus."""
    from app.services import transfer_service as ts

    _dimension(tmp_path / "leer" / "world")
    assert ts.stil_auf_platte(tmp_path / "leer", "world") is None


def test_stil_faellt_auf_den_servertyp_zurueck(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        spigot = _server(db, tmp_path / "s", name="S", slug="s", typ="spigot")
        forge = _server(db, tmp_path / "f", name="F", slug="f", typ="forge", port=25600)
        _dimension(tmp_path / "s" / "world")
        _dimension(tmp_path / "f" / "world")
        assert ts.stil(spigot) == ts.STIL_GETRENNT
        assert ts.stil(forge) == ts.STIL_GEMEINSAM


def test_welt_ordner_zaehlt_nur_vorhandene(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a", typ="spigot")
        _welt_getrennt(tmp_path / "a", end=False)
        namen = sorted(p.name for p in ts.welt_ordner(srv))
        assert namen == ["world", "world_nether"]


def test_data_version_aus_level_dat(tmp_path, client):
    from app.services import transfer_service as ts

    _level_dat(tmp_path / "world" / "level.dat", 4189)
    assert ts.data_version(tmp_path / "world") == 4189


def test_data_version_ohne_datei_ist_none(tmp_path, client):
    from app.services import transfer_service as ts

    (tmp_path / "world").mkdir()
    assert ts.data_version(tmp_path / "world") is None


# ------------------------------------------------------- Versionsrichtung

def test_abwaerts_wird_blockiert(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        neu = _server(db, tmp_path / "neu", name="Neu", slug="neu", version="26.3")
        alt = _server(db, tmp_path / "alt", name="Alt", slug="alt", version="26.2", port=25600)
        meldung = ts.pruefe_richtung(neu, alt)
        assert meldung is not None
        assert "26.3" in meldung and "26.2" in meldung


def test_aufwaerts_ist_erlaubt(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        alt = _server(db, tmp_path / "alt", name="Alt", slug="alt", version="26.2")
        neu = _server(db, tmp_path / "neu", name="Neu", slug="neu", version="26.3", port=25600)
        assert ts.pruefe_richtung(alt, neu) is None
        assert ts.pruefe_richtung(alt, alt) is None


def test_snapshot_faellt_auf_die_data_version_zurueck(tmp_path, client):
    """Snapshot-Kennungen lassen sich nicht ordnen - dann entscheidet level.dat."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", version="26w14a")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", version="26w10a", port=25600)
        _level_dat(tmp_path / "q" / "world" / "level.dat", 4200)
        _level_dat(tmp_path / "z" / "world" / "level.dat", 4100)
        meldung = ts.pruefe_richtung(quelle, ziel)
        assert meldung is not None and "4200" in meldung


# ----------------------------------------------------------- Moeglichkeiten

@pytest.mark.parametrize("quell_typ, ziel_typ, erwartet", [
    ("spigot", "paper", "plugins"),
    ("paper", "purpur", "plugins"),
    ("forge", "forge", "mods"),
    ("fabric", "fabric", "mods"),
])
def test_erweiterungen_moeglich(tmp_path, client, quell_typ, ziel_typ, erwartet):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ=quell_typ)
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ=ziel_typ, port=25600)
        ergebnis = ts.moeglichkeiten(quelle, ziel)["erweiterungen"]
        assert ergebnis["moeglich"] is True
        assert ergebnis["art"] == erwartet


@pytest.mark.parametrize("quell_typ, ziel_typ", [
    ("forge", "neoforge"),
    ("forge", "paper"),
    ("paper", "forge"),
    ("vanilla", "vanilla"),
])
def test_erweiterungen_nicht_moeglich(tmp_path, client, quell_typ, ziel_typ):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ=quell_typ)
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ=ziel_typ, port=25600)
        ergebnis = ts.moeglichkeiten(quelle, ziel)["erweiterungen"]
        assert ergebnis["moeglich"] is False
        assert ergebnis["grund"]


# ------------------------------------------------------------- Stil-Umbau

def test_umbau_getrennt_nach_gemeinsam(tmp_path, client):
    from app.services import transfer_service as ts

    _welt_getrennt(tmp_path)
    meldungen = ts._baue_stil_um(tmp_path, "world", ts.STIL_GETRENNT, ts.STIL_GEMEINSAM)

    assert (tmp_path / "world" / "DIM-1" / "region" / "r.0.0.mca").read_bytes() == b"nether"
    assert (tmp_path / "world" / "DIM1" / "region" / "r.0.0.mca").read_bytes() == b"end"
    # Die leeren Huellen muessen weg - sonst laedt der Server sie als eigene Welten.
    assert not (tmp_path / "world_nether").exists()
    assert not (tmp_path / "world_the_end").exists()
    assert sum(1 for m in meldungen if "einsortiert" in m) == 2
    # Der Drachenkampf-Stand steht in der level.dat der End-Welt und wandert nicht mit -
    # das muss benannt werden, nicht stillschweigend passieren.
    assert any("Enderdrachen" in m for m in meldungen)


def test_umbau_gemeinsam_nach_getrennt(tmp_path, client):
    from app.services import transfer_service as ts

    _welt_gemeinsam(tmp_path)
    ts._baue_stil_um(tmp_path, "world", ts.STIL_GEMEINSAM, ts.STIL_GETRENNT)

    assert (tmp_path / "world_nether" / "DIM-1" / "region" / "r.0.0.mca").read_bytes() == b"nether"
    assert (tmp_path / "world_the_end" / "DIM1" / "region" / "r.0.0.mca").read_bytes() == b"end"
    assert not (tmp_path / "world" / "DIM-1").exists()
    # Bukkit legt eine Welt ohne level.dat als leere Neuwelt an.
    assert (tmp_path / "world_nether" / "level.dat").is_file()
    assert (tmp_path / "world_the_end" / "level.dat").is_file()


def test_umbau_bei_gleichem_stil_ist_wirkungslos(tmp_path, client):
    from app.services import transfer_service as ts

    _welt_getrennt(tmp_path)
    assert ts._baue_stil_um(tmp_path, "world", ts.STIL_GETRENNT, ts.STIL_GETRENNT) == []
    assert (tmp_path / "world_nether" / "DIM-1").is_dir()


def test_umbau_ohne_dimensionen_laeuft_durch(tmp_path, client):
    from app.services import transfer_service as ts

    _dimension(tmp_path / "world")
    assert ts._baue_stil_um(tmp_path, "world", ts.STIL_GEMEINSAM, ts.STIL_GETRENNT) == []


# ------------------------------------------------------------------ Filter

def test_filter_laesst_laufzeitdateien_weg(client):
    from app.services import transfer_service as ts

    skip = ts._welt_filter(True)
    assert skip(Path("session.lock")) is True
    assert skip(Path("region/r.0.0.mca")) is False
    # uid.dat wandert BEWUSST mit: daran haengt, in welcher Welt ein Spieler zuletzt war.
    # Dass die Quelle ihre verliert, regelt _uebernimm_welt nach dem Tausch.
    assert skip(Path("uid.dat")) is False


def test_filter_spielerdaten_nur_in_der_wurzel(client):
    """Ein 'stats' tiefer im Baum gehoert zu einem Datapack und bleibt drin."""
    from app.services import transfer_service as ts

    skip = ts._welt_filter(False)
    assert skip(Path("playerdata")) is True
    assert skip(Path("stats")) is True
    assert skip(Path("datapacks/meins/data/stats")) is False
    assert ts._welt_filter(True)(Path("playerdata")) is False


def test_robuste_kopie_beachtet_den_filter(tmp_path, client):
    from app.services import server_service

    quelle = tmp_path / "q"
    (quelle / "behalten").mkdir(parents=True)
    (quelle / "behalten" / "a.txt").write_text("a", encoding="utf-8")
    (quelle / "weglassen").mkdir()
    (quelle / "weglassen" / "b.txt").write_text("b", encoding="utf-8")
    (quelle / "session.lock").write_text("x", encoding="utf-8")

    uebersprungen = server_service._robust_copy_tree(
        quelle, tmp_path / "z", skip=lambda rel: rel.name in {"weglassen", "session.lock"})

    assert (tmp_path / "z" / "behalten" / "a.txt").is_file()
    assert not (tmp_path / "z" / "weglassen").exists()
    assert not (tmp_path / "z" / "session.lock").exists()
    # Bewusst Uebergangenes ist kein Fehler.
    assert uebersprungen == []


# -------------------------------------------------------------- Properties

def test_properties_uebernimmt_spiel_nicht_netzwerk(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", level="quellwelt", port=11111)
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", level="zielwelt", port=22222)
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=quellwelt\nserver-port=11111\nmax-players=99\nonline-mode=false\n"
            "difficulty=hard\npvp=false\nview-distance=12\nmotd=Quelle\n",
            encoding="utf-8")

        ts._kopiere_properties(quelle, ziel)
        werte = _properties(tmp_path / "z")

    assert werte["difficulty"] == "hard"
    assert werte["pvp"] == "false"
    assert werte["view-distance"] == "12"
    # An den Zielserver gebunden - darf nicht mitwandern.
    assert werte["server-port"] == "22222"
    assert werte["level-name"] == "zielwelt"
    assert werte["max-players"] == "20"
    assert "online-mode" not in werte
    assert werte.get("motd") != "Quelle"


# ------------------------------------------------------------- Vorpruefung

def test_pruefung_lehnt_denselben_server_ab(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a")
        with pytest.raises(ValueError, match="derselbe Server"):
            ts.pruefe_transfer(srv, srv, welt=True, configs=False, erweiterungen=False)


def test_pruefung_lehnt_leere_auswahl_ab(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", port=25600)
        with pytest.raises(ValueError, match="nichts zum Uebernehmen"):
            ts.pruefe_transfer(quelle, ziel, welt=False, configs=False, erweiterungen=False)


def test_pruefung_lehnt_laufenden_server_ab(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import process_service, transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", port=25600)
        _welt_getrennt(tmp_path / "q")
        monkeypatch.setattr(process_service, "is_running", lambda sid: sid == ziel.id)
        with pytest.raises(ValueError, match="Zielserver zuerst stoppen"):
            ts.pruefe_transfer(quelle, ziel, welt=True, configs=False, erweiterungen=False)


def test_pruefung_lehnt_welt_ohne_quelle_ab(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", port=25600)
        with pytest.raises(ValueError, match="keine Welt"):
            ts.pruefe_transfer(quelle, ziel, welt=True, configs=False, erweiterungen=False)


def test_pruefung_lehnt_downgrade_ab(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", version="26.3")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", version="26.2", port=25600)
        _welt_getrennt(tmp_path / "q")
        with pytest.raises(ValueError, match="nur hoch-, nie herunterrechnen"):
            ts.pruefe_transfer(quelle, ziel, welt=True, configs=False, erweiterungen=False)


def test_pruefung_lehnt_verschachtelte_ordner_ab(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        aussen = tmp_path / "aussen"
        quelle = _server(db, aussen, name="Q", slug="q")
        ziel = _server(db, aussen / "innen", name="Z", slug="z", port=25600)
        _welt_getrennt(aussen)
        with pytest.raises(ValueError, match="ineinander"):
            ts.pruefe_transfer(quelle, ziel, welt=True, configs=False, erweiterungen=False)


def test_pruefung_lehnt_unpassende_erweiterungen_ab(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="forge")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="neoforge", port=25600)
        with pytest.raises(ValueError, match="laufen nicht auf"):
            ts.pruefe_transfer(quelle, ziel, welt=False, configs=False, erweiterungen=True)


# -------------------------------------------------------- Transfer komplett

def test_spigot_nach_paper_kopiert_alles(tmp_path, client, monkeypatch):
    """Gleicher Stil, neuere Zielversion - reine Dateiarbeit. Beide Versionen VOR 26.1,
    denn ab 26.1 nutzt Paper nicht mehr das Bukkit-Layout."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "spigot", name="Spigot", slug="spigot",
                         typ="spigot", version="1.21")
        ziel = _server(db, tmp_path / "paper", name="Paper", slug="paper",
                       typ="paper", version="1.21.11", port=25600)
        _welt_getrennt(tmp_path / "spigot")
        _welt_getrennt(tmp_path / "paper", nether=False, end=False)

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, spielerdaten=True,
                      run_async=False)

    ziel_welt = tmp_path / "paper" / "world"
    assert (ziel_welt / "region" / "r.0.0.mca").read_bytes() == b"overworld"
    assert (tmp_path / "paper" / "world_nether" / "DIM-1" / "region" / "r.0.0.mca"
            ).read_bytes() == b"nether"
    assert (tmp_path / "paper" / "world_the_end" / "DIM1" / "region" / "r.0.0.mca"
            ).read_bytes() == b"end"
    assert (ziel_welt / "playerdata" / "spieler.dat").is_file()
    # Die Laufzeit-Sperre bleibt zurueck ...
    assert not (ziel_welt / "session.lock").exists()
    # ... die Welt-Identitaet wandert dagegen mit (sonst erscheint jeder, der im Nether
    # ausgeloggt war, in der Overworld).
    assert (ziel_welt / "uid.dat").read_bytes() == b"welt-uuid"
    assert (tmp_path / "paper" / "world_nether" / "uid.dat").read_bytes() == b"dim-uuid"
    # Und die Quelle hat ihre abgegeben, damit es sie nicht zweimal gibt.
    assert not (tmp_path / "spigot" / "world" / "uid.dat").exists()


def test_bukkit_nach_vanilla_sortiert_dimensionen_ein(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "paper", name="Paper", slug="paper", typ="paper")
        ziel = _server(db, tmp_path / "forge", name="Forge", slug="forge", typ="forge",
                       port=25600)
        _welt_getrennt(tmp_path / "paper")
        _welt_gemeinsam(tmp_path / "forge")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    haupt = tmp_path / "forge" / "world"
    assert (haupt / "DIM-1" / "region" / "r.0.0.mca").read_bytes() == b"nether"
    assert (haupt / "DIM1" / "region" / "r.0.0.mca").read_bytes() == b"end"
    assert not (tmp_path / "forge" / "world_nether").exists()


def test_vanilla_nach_bukkit_lagert_dimensionen_aus(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "forge", name="Forge", slug="forge", typ="forge",
                         version="1.21.1")
        ziel = _server(db, tmp_path / "paper", name="Paper", slug="paper", typ="paper",
                       version="1.21.11", port=25600)
        _welt_gemeinsam(tmp_path / "forge")
        _welt_getrennt(tmp_path / "paper")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "paper" / "world_nether" / "DIM-1" / "region" / "r.0.0.mca"
            ).read_bytes() == b"nether"
    assert (tmp_path / "paper" / "world_nether" / "level.dat").is_file()
    assert not (tmp_path / "paper" / "world" / "DIM-1").exists()


def test_alte_dimensionsordner_des_ziels_verschwinden(tmp_path, client, monkeypatch):
    """Bliebe der alte world_nether stehen, laedt der Server den ALTEN Nether zur NEUEN
    Overworld dazu - und das faellt erst am Portal auf."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        # Die Quelle hat nur eine Overworld, das Ziel noch Nether und End von vorher.
        _welt_getrennt(tmp_path / "q", nether=False, end=False)
        _welt_getrennt(tmp_path / "z")
        (tmp_path / "z" / "world_nether" / "DIM-1" / "region" / "r.0.0.mca"
         ).write_bytes(b"alter-nether")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert not (tmp_path / "z" / "world_nether").exists()
    assert not (tmp_path / "z" / "world_the_end").exists()
    assert (tmp_path / "z" / "world" / "region" / "r.0.0.mca").read_bytes() == b"overworld"


def test_ohne_spielerdaten_bleiben_die_ordner_weg(tmp_path, client, monkeypatch):
    """Die Spielerdaten der QUELLE bleiben weg. Hat auch das Ziel keine (frischer Server),
    bleibt die Welt spielerfrei - das ist der Fall "Welt als Vorlage uebernehmen"."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "z", nether=False, end=False, spielerdaten=False)

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, spielerdaten=False,
                      run_async=False)

    haupt = tmp_path / "z" / "world"
    assert (haupt / "region" / "r.0.0.mca").is_file()
    assert not (haupt / "playerdata").exists()
    assert not (haupt / "stats").exists()
    assert not (haupt / "advancements").exists()


def test_welt_wird_auf_den_zielnamen_umbenannt(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper",
                         version="1.21.11", level="lobby")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper",
                       version="1.21.11", level="welt", port=25600)
        _welt_getrennt(tmp_path / "q", name="lobby")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "z" / "welt" / "region" / "r.0.0.mca").read_bytes() == b"overworld"
    assert (tmp_path / "z" / "welt_nether" / "DIM-1").is_dir()
    assert not (tmp_path / "z" / "lobby").exists()
    # Der level-name des Zielservers bleibt unberuehrt.
    assert _properties(tmp_path / "z")["level-name"] == "welt"


def test_sperren_werden_wieder_freigegeben(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import server_service, transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        _welt_getrennt(tmp_path / "q")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

        assert not server_service.is_active_duplication_source(quelle.id)
        assert not server_service.is_active_duplication_source(ziel.id)


def test_configs_und_plugins_zusammen(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        (tmp_path / "q" / "spigot.yml").write_text("settings: neu", encoding="utf-8")
        (tmp_path / "q" / "config").mkdir()
        (tmp_path / "q" / "config" / "etwas.yml").write_text("a: 1", encoding="utf-8")
        (tmp_path / "q" / "plugins").mkdir()
        (tmp_path / "q" / "plugins" / "Neu.jar").write_bytes(b"neu")
        (tmp_path / "z" / "plugins").mkdir()
        (tmp_path / "z" / "plugins" / "Alt.jar").write_bytes(b"alt")
        (tmp_path / "z" / "plugins" / "Alt").mkdir()
        (tmp_path / "z" / "plugins" / "Alt" / "config.yml").write_text("b: 2", encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=False, configs=True,
                      erweiterungen=True, run_async=False)

    assert (tmp_path / "z" / "spigot.yml").read_text(encoding="utf-8") == "settings: neu"
    assert (tmp_path / "z" / "config" / "etwas.yml").is_file()
    assert (tmp_path / "z" / "plugins" / "Neu.jar").is_file()
    # Alte JARs muessen weg (sonst zwei Versionen desselben Plugins) ...
    assert not (tmp_path / "z" / "plugins" / "Alt.jar").exists()
    # ... ihre Datenordner bleiben, dort liegen Einstellungen und Weltdaten.
    assert (tmp_path / "z" / "plugins" / "Alt" / "config.yml").is_file()


# ----------------------------------------------------------- Routen und UI

def test_detailseite_zeigt_den_uebernahme_bereich(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _server(db, tmp_path / "q", name="Quelle", slug="quelle")
        ziel = _server(db, tmp_path / "z", name="Ziel", slug="ziel", port=25600)
        ziel_id = ziel.id

    _login_admin(client)
    seite = client.get(f"/servers/{ziel_id}")
    assert seite.status_code == 200
    assert "Daten von einem anderen Server uebernehmen" in seite.text
    assert 'id="transfer-source"' in seite.text
    assert "Quelle" in seite.text


def test_vorschau_endpunkt_nennt_blocker(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Quelle", slug="quelle", version="26.3")
        ziel = _server(db, tmp_path / "z", name="Ziel", slug="ziel", version="26.2",
                       port=25600)
        _welt_getrennt(tmp_path / "q")
        quell_id, ziel_id = quelle.id, ziel.id

    _login_admin(client)
    antwort = client.get(f"/api/servers/{ziel_id}/transfer-preview?source_id={quell_id}")
    assert antwort.status_code == 200
    daten = antwort.json()
    assert daten["quelle"]["welt"] == "world"
    assert daten["quelle"]["bytes"] > 0
    assert any("herunterrechnen" in eintrag for eintrag in daten["blocker"])


def test_vorschau_meldet_strukturwechsel(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Quelle", slug="quelle", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Ziel", slug="ziel", typ="forge", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_gemeinsam(tmp_path / "z")
        quell_id, ziel_id = quelle.id, ziel.id

    _login_admin(client)
    daten = client.get(
        f"/api/servers/{ziel_id}/transfer-preview?source_id={quell_id}").json()
    assert daten["quelle"]["stil"] != daten["ziel"]["stil"]
    assert daten["struktur_umbau"] is True
    assert daten["blocker"] == []
    assert any("Nether und End" in eintrag for eintrag in daten["hinweise"])
    assert daten["moeglichkeiten"]["erweiterungen"]["moeglich"] is False


def test_transfer_route_meldet_fehler_zurueck(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Quelle", slug="quelle")
        ziel = _server(db, tmp_path / "z", name="Ziel", slug="ziel", port=25600)
        quell_id, ziel_id = quelle.id, ziel.id

    _login_admin(client)
    antwort = client.post(f"/servers/{ziel_id}/transfer",
                          data={"source_id": str(quell_id), "welt": "true"},
                          follow_redirects=False)
    assert antwort.status_code == 303
    assert antwort.headers["location"] == f"/servers/{ziel_id}"
    # Die Quelle hat keine Welt - der Fehler landet als Flash auf der Detailseite.
    seite = client.get(f"/servers/{ziel_id}")
    assert "keine Welt" in seite.text


def test_transfer_route_braucht_eine_quelle(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        ziel = _server(db, tmp_path / "z", name="Ziel", slug="ziel")
        ziel_id = ziel.id

    _login_admin(client)
    antwort = client.post(f"/servers/{ziel_id}/transfer",
                          data={"source_id": "", "welt": "true"}, follow_redirects=False)
    assert antwort.status_code == 303
    seite = client.get(f"/servers/{ziel_id}")
    assert "Quellserver auswaehlen" in seite.text


def test_vorschau_braucht_anmeldung(tmp_path, client):
    antwort = client.get("/api/servers/1/transfer-preview?source_id=2")
    assert antwort.status_code == 401


def test_vorschau_ohne_dimensionen_meldet_keinen_umbau(tmp_path, client):
    """Eine Welt ohne Nether und End besteht in beiden Stilen aus genau einem Ordner -
    da gibt es nichts zu verschieben, auch wenn die Servertypen verschieden sind."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Quelle", slug="quelle", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Ziel", slug="ziel", typ="forge", port=25600)
        _dimension(tmp_path / "q" / "world")
        _level_dat(tmp_path / "q" / "world" / "level.dat")
        quell_id, ziel_id = quelle.id, ziel.id

    _login_admin(client)
    daten = client.get(
        f"/api/servers/{ziel_id}/transfer-preview?source_id={quell_id}").json()
    assert daten["struktur_umbau"] is False
    assert not any("Nether und End" in eintrag for eintrag in daten["hinweise"])


# =========================================================================================
# Tests zu den Befunden des Code-Reviews. Jeder dieser Faelle hat vorher echte Weltdaten
# vernichtet oder aus dem Serverordner heraus geschrieben.
# =========================================================================================

# --- Weltname: Positivliste statt Verbotsliste (Laufwerksangaben!) -----------------------

@pytest.mark.parametrize("wert", [
    "C:welt",        # verwirft unter Windows die Basis: Path("D:/srv") / "C:welt" == "C:welt"
    "c:",
    "../weg",
    "a/b",
    "a\\b",
    "welt.",         # Windows schneidet den Punkt still ab
    "CON",           # Geraetename
    "nul.txt",
    "",
    "   ",
    "x" * 65,
])
def test_unsichere_weltnamen_werden_abgewiesen(client, wert):
    from app.services import transfer_service as ts

    assert ts.ist_sicherer_weltname(wert) is False


@pytest.mark.parametrize("wert", ["world", "lobby", "Meine Welt", "welt_2", "a.b-c"])
def test_sichere_weltnamen_gehen_durch(client, wert):
    from app.services import transfer_service as ts

    assert ts.ist_sicherer_weltname(wert) is True


def test_laufwerksrelativer_level_name_faellt_auf_world_zurueck(tmp_path, client):
    """"C:welt" hat keinen Schraegstrich, wuerde aber aus dem Serverordner herausfuehren."""
    from pathlib import Path as _Path

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a", level="C:welt")
        assert ts.level_name(srv) == "world"
    # Beleg, warum die Pruefung noetig ist:
    assert _Path("D:/srv/lobby") / "C:welt" == _Path("C:welt")


# --- Backup-Umfang "world" kannte nur den Namen "world" ----------------------------------

def test_welt_backup_findet_abweichenden_weltnamen(tmp_path, client):
    """Fest verdrahtet war ["world", ...]; ein Server mit level-name=lobby bekam dadurch
    ein Welt-Backup ohne eine einzige Weltdatei."""
    from app.services.backup_service import _iter_backup_files

    basis = tmp_path / "srv"
    (basis / "lobby" / "region").mkdir(parents=True)
    (basis / "lobby" / "region" / "r.0.0.mca").write_bytes(b"ow")
    (basis / "lobby_nether" / "DIM-1").mkdir(parents=True)
    (basis / "lobby_nether" / "DIM-1" / "r.0.0.mca").write_bytes(b"nether")
    (basis / "server.properties").write_text("level-name=lobby\n", encoding="utf-8")
    (basis / "eula.txt").write_text("true", encoding="utf-8")

    gefunden = {p.relative_to(basis).as_posix() for p in _iter_backup_files(basis, "world")}
    assert "lobby/region/r.0.0.mca" in gefunden
    assert "lobby_nether/DIM-1/r.0.0.mca" in gefunden


def test_welt_backup_nimmt_auch_eine_alte_world_mit(tmp_path, client):
    """Wer level-name umstellt, hat oft noch die alte world/ daneben liegen."""
    from app.services.backup_service import _iter_backup_files

    basis = tmp_path / "srv"
    (basis / "lobby").mkdir(parents=True)
    (basis / "lobby" / "level.dat").write_bytes(b"neu")
    (basis / "world").mkdir()
    (basis / "world" / "level.dat").write_bytes(b"alt")
    (basis / "server.properties").write_text("level-name=lobby\n", encoding="utf-8")

    gefunden = {p.relative_to(basis).as_posix() for p in _iter_backup_files(basis, "world")}
    assert gefunden >= {"lobby/level.dat", "world/level.dat"}


def test_transfer_bricht_ab_wenn_die_sicherung_die_welt_nicht_enthaelt(
        tmp_path, client, monkeypatch):
    """Eine Sicherung, die die Welt verfehlt, ist kein Weg zurueck - dann darf die alte
    Welt nicht geloescht werden."""
    import zipfile

    from app.db.session import SessionLocal
    from app.services import backup_service, transfer_service as ts

    def _leere_sicherung(db, *, server, **kwargs):
        pfad = tmp_path / "leer.zip"
        with zipfile.ZipFile(pfad, "w") as archiv:
            archiv.writestr("server.properties", "level-name=world")

        class _Attrappe:
            backup_name = "leere-sicherung"
            storage_path = str(pfad)

        return _Attrappe()

    monkeypatch.setattr(backup_service, "create_backup", _leere_sicherung)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "z")
        (tmp_path / "z" / "world" / "region" / "r.0.0.mca").write_bytes(b"ZIEL-ORIGINAL")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "z" / "world" / "region" / "r.0.0.mca").read_bytes() == b"ZIEL-ORIGINAL"


def test_leere_sicherung_ist_ok_wenn_das_ziel_keine_welt_hat(tmp_path, client, monkeypatch):
    """Ein frischer Zielserver hat nichts zu sichern - das darf nicht blockieren."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        _welt_getrennt(tmp_path / "q")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "z" / "world" / "region" / "r.0.0.mca").read_bytes() == b"overworld"


# --- Rollback von _tausche_welt durfte Originale nicht anfassen --------------------------

def test_rollback_laesst_unberuehrte_originale_stehen(tmp_path, client, monkeypatch):
    """Scheitert das Beiseiteraeumen beim ZWEITEN Ordner, sind der dritte und der zweite
    noch die Originale - ein Rollback ueber alle kanonischen Namen hat sie geloescht."""
    from pathlib import Path as _Path

    from app.services import transfer_service as ts

    ziel_base = tmp_path / "z"
    _welt_getrennt(ziel_base)
    (ziel_base / "world" / "region" / "r.0.0.mca").write_bytes(b"ALT-OW")
    (ziel_base / "world_nether" / "DIM-1" / "region" / "r.0.0.mca").write_bytes(b"ALT-NETHER")
    (ziel_base / "world_the_end" / "DIM1" / "region" / "r.0.0.mca").write_bytes(b"ALT-END")

    temp = ziel_base / ".uebernahme_test"
    _dimension(temp / "world", b"NEU-OW")

    echtes_rename = _Path.rename

    def _rename(self, ziel):
        # Offenes Handle auf world_nether (Suchindexer, Virenscanner, Explorer) -> WinError 32
        if self.name == "world_nether":
            raise OSError(32, "Datei von einem anderen Prozess verwendet")
        return echtes_rename(self, ziel)

    monkeypatch.setattr(_Path, "rename", _rename)
    with pytest.raises(OSError):
        ts._tausche_welt(ziel_base, "world", temp, "test")
    monkeypatch.undo()

    # Alle drei Originale muessen noch da sein.
    assert (ziel_base / "world" / "region" / "r.0.0.mca").read_bytes() == b"ALT-OW"
    assert (ziel_base / "world_nether" / "DIM-1" / "region" / "r.0.0.mca").read_bytes() == b"ALT-NETHER"
    assert (ziel_base / "world_the_end" / "DIM1" / "region" / "r.0.0.mca").read_bytes() == b"ALT-END"


def test_rollback_entfernt_schon_eingeschobene_ordner(tmp_path, client, monkeypatch):
    """Nach einem Rollback darf keine halb eingeschobene neue Welt stehenbleiben."""
    from app.services import transfer_service as ts

    ziel_base = tmp_path / "z"
    _welt_getrennt(ziel_base, nether=False, end=False)
    (ziel_base / "world" / "region" / "r.0.0.mca").write_bytes(b"ALT-OW")

    temp = ziel_base / ".uebernahme_test"
    _dimension(temp / "world", b"NEU-OW")
    _dimension(temp / "world_nether" / "DIM-1", b"NEU-NETHER")

    echtes_move = ts.shutil.move

    def _move(von, nach):
        if str(nach).endswith("world_nether"):
            raise OSError("Platte voll")
        return echtes_move(von, nach)

    monkeypatch.setattr(ts.shutil, "move", _move)
    with pytest.raises(OSError):
        ts._tausche_welt(ziel_base, "world", temp, "test")
    monkeypatch.undo()

    assert (ziel_base / "world" / "region" / "r.0.0.mca").read_bytes() == b"ALT-OW"
    assert not (ziel_base / "world_nether").exists()


# --- Stil-Umbau loeschte die Huellen bedingungslos ---------------------------------------

def test_umbau_bricht_ab_statt_unbekannte_dimensionsdaten_zu_loeschen(tmp_path, client):
    """Findet der Umbau in der Huelle kein DIM-1/dimensions, darf er sie NICHT loeschen -
    sonst sind Nether und End stillschweigend weg."""
    from app.services import transfer_service as ts

    _dimension(tmp_path / "world", b"overworld")
    huelle = tmp_path / "world_nether"
    (huelle / "irgendwas_neues" / "region").mkdir(parents=True)
    (huelle / "irgendwas_neues" / "region" / "r.0.0.mca").write_bytes(b"NETHER-DATEN")
    _level_dat(huelle / "level.dat")

    with pytest.raises(ValueError, match="nicht zuordnen"):
        ts._baue_stil_um(tmp_path, "world", ts.STIL_GETRENNT, ts.STIL_GEMEINSAM)

    # Die Daten muessen noch da sein.
    assert (huelle / "irgendwas_neues" / "region" / "r.0.0.mca").read_bytes() == b"NETHER-DATEN"


def test_umbau_verschiebt_die_26x_struktur(tmp_path, client):
    """Ab 26.x liegen die Dimensionen unter dimensions/minecraft/<dim>/ statt DIM-1/DIM1."""
    from app.services import transfer_service as ts

    _dimension(tmp_path / "world" / "dimensions" / "minecraft" / "overworld", b"ow")
    _level_dat(tmp_path / "world" / "level.dat")
    huelle = tmp_path / "world_nether"
    _dimension(huelle / "dimensions" / "minecraft" / "the_nether", b"NETHER26")
    _level_dat(huelle / "level.dat")

    meldungen = ts._baue_stil_um(tmp_path, "world", ts.STIL_GETRENNT, ts.STIL_GEMEINSAM)

    ziel = tmp_path / "world" / "dimensions" / "minecraft" / "the_nether" / "region" / "r.0.0.mca"
    assert ziel.read_bytes() == b"NETHER26"
    assert not huelle.exists()
    assert any("the_nether" in m for m in meldungen)


def test_umbau_lagert_die_26x_struktur_aus(tmp_path, client):
    from app.services import transfer_service as ts

    haupt = tmp_path / "world"
    _dimension(haupt / "dimensions" / "minecraft" / "overworld", b"ow")
    _dimension(haupt / "dimensions" / "minecraft" / "the_nether", b"NETHER26")
    _dimension(haupt / "dimensions" / "minecraft" / "the_end", b"END26")
    _level_dat(haupt / "level.dat")

    ts._baue_stil_um(tmp_path, "world", ts.STIL_GEMEINSAM, ts.STIL_GETRENNT)

    assert (tmp_path / "world_nether" / "dimensions" / "minecraft" / "the_nether"
            / "region" / "r.0.0.mca").read_bytes() == b"NETHER26"
    assert (tmp_path / "world_the_end" / "dimensions" / "minecraft" / "the_end"
            / "region" / "r.0.0.mca").read_bytes() == b"END26"
    assert (tmp_path / "world_nether" / "level.dat").is_file()
    # Die Overworld bleibt, wo sie hingehoert.
    assert (haupt / "dimensions" / "minecraft" / "overworld" / "region" / "r.0.0.mca").is_file()


def test_stil_erkennung_kennt_die_26x_struktur(tmp_path, client):
    from app.services import transfer_service as ts

    _dimension(tmp_path / "v" / "world" / "dimensions" / "minecraft" / "the_nether")
    assert ts.stil_auf_platte(tmp_path / "v", "world") == ts.STIL_GEMEINSAM


# --- Start-Sperre: Zaehler statt Menge ---------------------------------------------------

def test_sperre_zaehlt_mehrere_vorgaenge(client):
    """Mit einer Menge hob der zuerst fertige Vorgang die Sperre fuer ALLE auf - der noch
    laufende Kopiervorgang stand ab da ungeschuetzt da."""
    from app.services import server_service

    sid = 987654
    try:
        server_service._mark_duplication_source(sid)
        server_service._mark_duplication_source(sid)
        assert server_service.is_active_duplication_source(sid) is True

        server_service.unlock_server_for_file_copy(sid)
        # Der zweite Vorgang laeuft noch - die Sperre muss stehen bleiben.
        assert server_service.is_active_duplication_source(sid) is True

        server_service.unlock_server_for_file_copy(sid)
        assert server_service.is_active_duplication_source(sid) is False
    finally:
        for _ in range(4):
            server_service.unlock_server_for_file_copy(sid)


def test_exklusive_sperre_weist_den_zweiten_vorgang_ab(client):
    from app.services import server_service

    a, b, c = 991001, 991002, 991003
    try:
        assert server_service.try_lock_servers_for_file_copy((a, b)) is True
        # Ueberschneidung -> abgewiesen ...
        assert server_service.try_lock_servers_for_file_copy((b, c)) is False
        # ... und zwar ohne c zu sperren (alles oder nichts).
        assert server_service.is_active_duplication_source(c) is False
    finally:
        for sid in (a, b, c):
            server_service.unlock_server_for_file_copy(sid)


def test_zweite_uebernahme_auf_dasselbe_ziel_wird_abgewiesen(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import server_service, transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        _welt_getrennt(tmp_path / "q")
        # Eine Uebernahme laeuft schon (Doppelklick, zweiter Tab, zweiter Admin).
        server_service.try_lock_servers_for_file_copy((ziel.id,))
        try:
            with pytest.raises(ValueError, match="wird gerade gearbeitet"):
                ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)
        finally:
            server_service.unlock_server_for_file_copy(ziel.id)


def test_duplizieren_waehrend_einer_uebernahme_wird_abgewiesen(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a", typ="paper")
        server_service.try_lock_servers_for_file_copy((srv.id,))
        try:
            with pytest.raises(ValueError, match="wird gerade gearbeitet"):
                server_service.duplicate_server(db, srv, None, run_async=False)
        finally:
            server_service.unlock_server_for_file_copy(srv.id)


def test_abgewiesene_uebernahme_laesst_keine_sperre_zurueck(tmp_path, client):
    """Scheitert die Pruefung NACH dem Sperren, muessen beide Server wieder frei sein."""
    from app.db.session import SessionLocal
    from app.services import server_service, transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        # Quelle hat keine Welt -> pruefe_transfer wirft, nachdem schon gesperrt wurde.
        with pytest.raises(ValueError, match="keine Welt"):
            ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

        assert server_service.is_active_duplication_source(quelle.id) is False
        assert server_service.is_active_duplication_source(ziel.id) is False


# --- Config-Transfer darf Netzwerk und Zugangskontrolle nicht mitbringen -----------------

def test_config_transfer_behaelt_das_velocity_setup_des_ziels(tmp_path, client, monkeypatch):
    """Ein mitgebrachtes bungeecord=true oder ein fremdes Velocity-Geheimnis bricht die
    Anmeldung am Zielserver komplett."""
    import yaml

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)

        # Quelle: haengt hinter einem Proxy, mit eigenem Geheimnis.
        (tmp_path / "q" / "spigot.yml").write_text(
            yaml.safe_dump({"settings": {"bungeecord": True, "user-cache-size": 5000}}),
            encoding="utf-8")
        (tmp_path / "q" / "config").mkdir()
        (tmp_path / "q" / "config" / "paper-global.yml").write_text(
            yaml.safe_dump({"proxies": {"velocity": {"enabled": True, "secret": "QUELLE"}},
                            "messages": {"kick": {"flying-player": "Quelle"}}}),
            encoding="utf-8")

        # Ziel: eigenstaendig, kein Proxy.
        (tmp_path / "z" / "spigot.yml").write_text(
            yaml.safe_dump({"settings": {"bungeecord": False, "user-cache-size": 1000}}),
            encoding="utf-8")
        (tmp_path / "z" / "config").mkdir()
        (tmp_path / "z" / "config" / "paper-global.yml").write_text(
            yaml.safe_dump({"proxies": {"velocity": {"enabled": False, "secret": "ZIEL"}}}),
            encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=False, configs=True, run_async=False)

    spigot = yaml.safe_load((tmp_path / "z" / "spigot.yml").read_text(encoding="utf-8"))
    paper = yaml.safe_load((tmp_path / "z" / "config" / "paper-global.yml").read_text(
        encoding="utf-8"))

    # Netzwerk bleibt beim Zielserver ...
    assert spigot["settings"]["bungeecord"] is False
    assert paper["proxies"]["velocity"]["enabled"] is False
    assert paper["proxies"]["velocity"]["secret"] == "ZIEL"
    # ... alles andere wandert mit.
    assert spigot["settings"]["user-cache-size"] == 5000
    assert paper["messages"]["kick"]["flying-player"] == "Quelle"


def test_config_transfer_schaltet_die_whitelist_nicht_um(tmp_path, client, monkeypatch):
    """white-list=false vom Quellserver wuerde den Zielserver fuer alle oeffnen."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=world\nserver-port=11111\nwhite-list=false\n"
            "enforce-whitelist=false\nlevel-type=flat\ndifficulty=peaceful\n",
            encoding="utf-8")
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nserver-port=25600\nwhite-list=true\n"
            "enforce-whitelist=true\nlevel-type=normal\ndifficulty=normal\n",
            encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=False, configs=True, run_async=False)

    werte = _properties(tmp_path / "z")
    assert werte["white-list"] == "true"
    assert werte["enforce-whitelist"] == "true"
    # Weltgebunden, bleibt ebenfalls stehen.
    assert werte["level-type"] == "normal"
    # Gameplay wandert mit.
    assert werte["difficulty"] == "peaceful"


# --- Spielerdaten des Ziels bleiben, wenn die der Quelle abgewaehlt sind ----------------

def test_abgewaehlte_spielerdaten_erhalten_die_des_ziels(tmp_path, client, monkeypatch):
    """Der Haken bezieht sich auf die QUELLE - die Spieler des Zielservers sollen bleiben."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "z", nether=False, end=False)
        (tmp_path / "q" / "world" / "playerdata" / "spieler.dat").write_bytes(b"QUELLE")
        (tmp_path / "z" / "world" / "playerdata" / "spieler.dat").write_bytes(b"ZIEL")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, spielerdaten=False,
                      run_async=False)

    # Gelaende von der Quelle, Spieler vom Ziel.
    assert (tmp_path / "z" / "world" / "region" / "r.0.0.mca").read_bytes() == b"overworld"
    assert (tmp_path / "z" / "world" / "playerdata" / "spieler.dat").read_bytes() == b"ZIEL"


# --- Vorschau und echte Pruefung muessen deckungsgleich sein ----------------------------

def test_vorschau_nennt_auch_die_verschachtelung(tmp_path, client):
    """Fehlte der Blocker, gab der Dialog den Knopf frei und der POST scheiterte danach."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        aussen = tmp_path / "aussen"
        quelle = _server(db, aussen, name="Quelle", slug="quelle", typ="paper")
        ziel = _server(db, aussen / "innen", name="Ziel", slug="ziel", typ="paper", port=25600)
        _welt_getrennt(aussen)
        quell_id, ziel_id = quelle.id, ziel.id

    _login_admin(client)
    daten = client.get(
        f"/api/servers/{ziel_id}/transfer-preview?source_id={quell_id}").json()
    assert any("ineinander" in eintrag for eintrag in daten["blocker"])


# --- Der Fehlerpfad muss wirklich als Fehler ankommen -----------------------------------

def test_fehlgeschlagene_sicherung_meldet_einen_fehler(tmp_path, client, monkeypatch):
    """Vorher pruefte dieser Test nur, dass get_start_progress() ein dict liefert - das tut
    es immer. Jetzt wird der Fortschritt wirklich geprueft."""
    from app.db.session import SessionLocal
    from app.services import backup_service, process_service, transfer_service as ts

    def _kaputt(*args, **kwargs):
        raise RuntimeError("Backup-Ziel nicht erreichbar")

    monkeypatch.setattr(backup_service, "create_backup", _kaputt)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "z")
        (tmp_path / "z" / "world" / "region" / "r.0.0.mca").write_bytes(b"ZIEL-ORIGINAL")
        ziel_id = ziel.id

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    fortschritt = process_service.get_start_progress(ziel_id)
    assert fortschritt["stage"] == "error"
    assert "Backup-Ziel nicht erreichbar" in str(fortschritt["message"])
    assert fortschritt["active"] is False
    assert (tmp_path / "z" / "world" / "region" / "r.0.0.mca").read_bytes() == b"ZIEL-ORIGINAL"


# --- Ziel-Weltstil kommt vom Servertyp, nicht von Altlasten auf der Platte --------------

def test_ziel_stil_folgt_dem_servertyp_nicht_der_platte(tmp_path, client):
    """Ein Paper-Server mit einem uebrig gebliebenen world/DIM-1 sieht auf der Platte wie
    'gemeinsam' aus - Paper liest den Nether aber aus world_nether/."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        paper = _server(db, tmp_path / "p", name="P", slug="p", typ="paper",
                        version="1.21.11")
        _dimension(tmp_path / "p" / "world")
        _dimension(tmp_path / "p" / "world" / "DIM-1")   # Altlast einer Vanilla-Installation

        assert ts.stil_auf_platte(tmp_path / "p", "world") == ts.STIL_GEMEINSAM
        assert ts.ziel_stil(paper) == ts.STIL_GETRENNT


def test_altlast_am_ziel_fuehrt_nicht_zum_falschen_umbau(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper",
                       version="1.21.11", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "z", nether=False, end=False)
        _dimension(tmp_path / "z" / "world" / "DIM-1", b"altlast")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    # Paper erwartet den Nether in world_nether/ - dort muss er liegen.
    assert (tmp_path / "z" / "world_nether" / "DIM-1" / "region" / "r.0.0.mca"
            ).read_bytes() == b"nether"
    assert not (tmp_path / "z" / "world" / "DIM-1").exists()


def test_config_transfer_meldet_wenn_nichts_vorhanden_war(tmp_path, client, monkeypatch):
    """Wer "Configs uebernehmen" anhakt und nichts hoert, haelt sie fuer uebernommen."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)

        meldungen = ts._uebernimm_configs(quelle, ziel)

    assert any("Keine der bekannten Config-Dateien" in m for m in meldungen)


# =========================================================================================
# Migrationsfaelle. Der Kern: Paper hat in 26.1 die Bukkit-Weltablage aufgegeben und nutzt
# seitdem den Vanilla-Stil. Belegt an zwei Servern dieses Nutzers, beide Minecraft 26.2:
# Paper 26.2 laedt alle Dimensionen aus "world" (world_nether kommt im Log nicht vor),
# Spigot 26.2 hat world/, world_nether/, world_the_end/.
# =========================================================================================

@pytest.mark.parametrize("typ, version, erwartet", [
    # Spigot/CraftBukkit haben die Umstellung NICHT mitgemacht.
    ("spigot", "1.21", "getrennt"),
    ("spigot", "26.2", "getrennt"),
    ("bukkit", "26.3", "getrennt"),
    # Paper und Forks: bis 26.0 Bukkit-Stil ...
    ("paper", "1.21.11", "getrennt"),
    ("paper", "26.0", "getrennt"),
    # ... ab 26.1 Vanilla-Stil.
    ("paper", "26.1", "gemeinsam"),
    ("paper", "26.3", "gemeinsam"),
    ("purpur", "26.2", "gemeinsam"),
    ("folia", "26.2", "gemeinsam"),
    # Vanilla und Mod-Loader immer Vanilla-Stil.
    ("vanilla", "1.21", "gemeinsam"),
    ("forge", "1.21.1", "gemeinsam"),
    ("neoforge", "1.21.1", "gemeinsam"),
    ("fabric", "1.21", "gemeinsam"),
])
def test_weltlayout_haengt_an_typ_und_version(tmp_path, client, typ, version, erwartet):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        srv = _server(db, tmp_path / typ / version.replace(".", "_"),
                      name=f"{typ}-{version}", slug=f"{typ}-{version}", typ=typ,
                      version=version)
        assert ts.stil_aus_typ(srv) == erwartet
        assert ts.ziel_stil(srv) == erwartet


def test_paper_26x_nach_paper_26x_baut_nichts_um(tmp_path, client, monkeypatch):
    """Vorher hielt der Manager die Quelle fuer 'gemeinsam' (Platte) und das Ziel fuer
    'getrennt' (Typ) und lagerte die Dimensionen in Ordner aus, die Paper 26.x nicht liest -
    Nether und End waeren weg gewesen."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper", version="26.2")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="26.3",
                       port=25600)
        # Paper-26.x-Layout: alles in EINER Welt.
        haupt = tmp_path / "q" / "world"
        _dimension(haupt / "dimensions" / "minecraft" / "overworld", b"ow26")
        _dimension(haupt / "dimensions" / "minecraft" / "the_nether", b"nether26")
        _dimension(haupt / "dimensions" / "minecraft" / "the_end", b"end26")
        _level_dat(haupt / "level.dat")
        _dimension(tmp_path / "z" / "world" / "dimensions" / "minecraft" / "overworld")
        _level_dat(tmp_path / "z" / "world" / "level.dat")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    ziel_welt = tmp_path / "z" / "world" / "dimensions" / "minecraft"
    assert (ziel_welt / "the_nether" / "region" / "r.0.0.mca").read_bytes() == b"nether26"
    assert (ziel_welt / "the_end" / "region" / "r.0.0.mca").read_bytes() == b"end26"
    # Kein Bukkit-Ordner darf entstanden sein.
    assert not (tmp_path / "z" / "world_nether").exists()
    assert not (tmp_path / "z" / "world_the_end").exists()


def test_vanilla_26x_nach_paper_26x_baut_nichts_um(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="vanilla", version="26.2")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="26.2",
                       port=25600)
        haupt = tmp_path / "q" / "world"
        _dimension(haupt / "dimensions" / "minecraft" / "the_nether", b"nether26")
        _level_dat(haupt / "level.dat")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "z" / "world" / "dimensions" / "minecraft" / "the_nether"
            / "region" / "r.0.0.mca").read_bytes() == b"nether26"
    assert not (tmp_path / "z" / "world_nether").exists()


def test_spigot_26x_nach_paper_26x_wird_zusammengelegt(tmp_path, client, monkeypatch):
    """Der Fall des Nutzers. Paper erklaert den direkten Weg fuer unmoeglich und empfiehlt
    den Zwischenschritt ueber die Vanilla-Struktur - genau den macht der Manager jetzt."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot", version="26.2")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="26.3",
                       port=25600)
        _welt_getrennt(tmp_path / "q")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    # Zusammengelegt in EINEN Weltordner - Paper 26.x migriert von dort beim ersten Start.
    assert (tmp_path / "z" / "world" / "DIM-1" / "region" / "r.0.0.mca").read_bytes() == b"nether"
    assert (tmp_path / "z" / "world" / "DIM1" / "region" / "r.0.0.mca").read_bytes() == b"end"
    assert not (tmp_path / "z" / "world_nether").exists()


def test_paper_1x_nach_paper_26x_wird_zusammengelegt(tmp_path, client, monkeypatch):
    """Auch innerhalb von Paper gibt es die Formatgrenze - 1.21 hat noch world_nether/."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper", version="1.21.11")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="26.2",
                       port=25600)
        _welt_getrennt(tmp_path / "q")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "z" / "world" / "DIM-1" / "region" / "r.0.0.mca").read_bytes() == b"nether"
    assert not (tmp_path / "z" / "world_nether").exists()


# --- 26.1 hat die Spielerordner nach players/ verschoben --------------------------------

def test_spielerdaten_filter_kennt_die_26x_ordner(client):
    """playerdata -> players/data, stats -> players/stats, advancements ->
    players/advancements. Mit nur den alten Namen hatte der Haken auf 26.x-Welten gar
    keine Wirkung."""
    from app.services import transfer_service as ts

    skip = ts._welt_filter(False)
    assert skip(Path("playerdata")) is True
    assert skip(Path("players")) is True
    assert ts._welt_filter(True)(Path("players")) is False


def test_ohne_spielerdaten_laesst_auch_players_weg(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper", version="26.2")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="26.2",
                       port=25600)
        haupt = tmp_path / "q" / "world"
        _dimension(haupt / "dimensions" / "minecraft" / "overworld", b"ow")
        _level_dat(haupt / "level.dat")
        (haupt / "players" / "data").mkdir(parents=True)
        (haupt / "players" / "data" / "abc.dat").write_bytes(b"inventar")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, spielerdaten=False,
                      run_async=False)

    assert (tmp_path / "z" / "world" / "dimensions" / "minecraft" / "overworld"
            / "region" / "r.0.0.mca").is_file()
    assert not (tmp_path / "z" / "world" / "players").exists()


# --- Versionsvergleich: Vorabversionen und Schreibweisen --------------------------------

def test_gleiche_version_andere_schreibweise_ist_erlaubt(tmp_path, client):
    """1.21 und 1.21.0 sind dieselbe Version - vorher wurde das als Downgrade geblockt."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        a = _server(db, tmp_path / "a", name="A", slug="a", version="1.21.0")
        b = _server(db, tmp_path / "b", name="B", slug="b", version="1.21", port=25600)
        assert ts.pruefe_richtung(a, b) is None
        assert ts.pruefe_richtung(b, a) is None


@pytest.mark.parametrize("neuer, aelter", [
    ("1.21.4", "1.21.4-rc1"),
    ("26.3", "26.3-pre2"),
])
def test_release_auf_vorabversion_wird_blockiert(tmp_path, client, neuer, aelter):
    """Ein Release-Candidate ist AELTER als das Release (1.21.4-rc1 = DataVersion 4184
    gegen 4189) - vorher galten beide als gleich und der Transfer lief durch."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", version=neuer)
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", version=aelter, port=25600)
        assert ts.pruefe_richtung(quelle, ziel) is not None
        # Die Gegenrichtung ist ein normales Upgrade.
        assert ts.pruefe_richtung(ziel, quelle) is None


# --- Widerspruechliches Layout und unvollstaendige Welten --------------------------------

def test_zwei_layouts_gleichzeitig_werden_abgelehnt(tmp_path, client):
    """Ein uebrig gebliebenes world/DIM-1 neben world_nether/ laesst sich nicht zuordnen -
    und Paper ab 26.1 bricht beim Start mit 'Failed to migrate world storage' ab."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="26.2",
                       port=25600)
        _welt_getrennt(tmp_path / "q")
        _dimension(tmp_path / "q" / "world" / "DIM-1", b"alter-rest")

        with pytest.raises(ValueError, match="zwei Strukturen"):
            ts.pruefe_transfer(quelle, ziel, welt=True, configs=False, erweiterungen=False)


def test_welt_ohne_level_dat_wird_abgelehnt(tmp_path, client):
    """Ein Ordner ohne level.dat ist keine Welt - das Ziel haette seine verloren und
    nichts Brauchbares bekommen."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        _dimension(tmp_path / "q" / "world")   # Regionen, aber keine level.dat

        with pytest.raises(ValueError, match="level.dat"):
            ts.pruefe_transfer(quelle, ziel, welt=True, configs=False, erweiterungen=False)


# --- Spieleridentitaet: UUID-Art ---------------------------------------------------------

def test_uuid_art_wird_an_den_dateinamen_gemessen(tmp_path, client):
    from app.services import transfer_service as ts

    welt = tmp_path / "world"
    (welt / "playerdata").mkdir(parents=True)
    # Echte Mojang-UUID (Version 4) - die 13. Stelle ist die 4.
    (welt / "playerdata" / "69f230e1-af2c-4d0e-9885-93d66595a855.dat").write_bytes(b"x")
    assert ts.gemessene_uuid_art(welt) == "v4"

    # Offline erzeugt (Version 3).
    (welt / "playerdata" / "1a2b3c4d-5e6f-3a8b-9c0d-1e2f3a4b5c6d.dat").write_bytes(b"y")
    assert ts.gemessene_uuid_art(welt) == "gemischt"


def test_uuid_art_findet_auch_die_26x_ordner(tmp_path, client):
    from app.services import transfer_service as ts

    welt = tmp_path / "world"
    (welt / "players" / "data").mkdir(parents=True)
    (welt / "players" / "data" / "69f230e1-af2c-4d0e-9885-93d66595a855.dat").write_bytes(b"x")
    assert ts.gemessene_uuid_art(welt) == "v4"


def test_leere_welt_hat_keine_uuid_art(tmp_path, client):
    from app.services import transfer_service as ts

    (tmp_path / "world").mkdir()
    assert ts.gemessene_uuid_art(tmp_path / "world") is None


@pytest.mark.parametrize("online_mode, erwartet", [
    ("true", "v4"),
    ("false", "v3"),
])
def test_erwartete_uuid_art_aus_online_mode(tmp_path, client, online_mode, erwartet):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a")
        (tmp_path / "a" / "server.properties").write_text(
            f"level-name=world\nonline-mode={online_mode}\n", encoding="utf-8")
        assert ts.erwartete_uuid_art(srv) == erwartet


def test_velocity_backend_hat_echte_uuids_trotz_online_mode_false(tmp_path, client):
    """Der konkrete Fall des Nutzers: ein Paper-Backend hinter Velocity hat
    online-mode=false, bekommt aber die echten Mojang-UUIDs vom Proxy. Wer nur online-mode
    liest, wuerde hier falsch warnen."""
    import yaml

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a", typ="paper", version="26.2")
        (tmp_path / "a" / "server.properties").write_text(
            "level-name=world\nonline-mode=false\n", encoding="utf-8")
        (tmp_path / "a" / "config").mkdir()
        (tmp_path / "a" / "config" / "paper-global.yml").write_text(
            yaml.safe_dump({"proxies": {"velocity": {"enabled": True, "secret": "x"}}}),
            encoding="utf-8")

        assert ts.forwarding_aktiv(srv) is True
        assert ts.erwartete_uuid_art(srv) == "v4"


def test_identitaetswarnung_bei_wechsel_der_anmeldeart(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        (tmp_path / "q" / "world" / "playerdata").mkdir(parents=True)
        # Offline erzeugte UUID (Version 3) in der Quellwelt ...
        (tmp_path / "q" / "world" / "playerdata"
         / "1a2b3c4d-5e6f-3a8b-9c0d-1e2f3a4b5c6d.dat").write_bytes(b"x")
        # ... Ziel meldet bei Mojang an.
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nonline-mode=true\n", encoding="utf-8")

        meldung = ts.spieleridentitaet_warnung(quelle, ziel)

    assert meldung is not None
    assert "leerem Inventar" in meldung


def test_keine_identitaetswarnung_wenn_die_arten_passen(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        (tmp_path / "q" / "world" / "playerdata").mkdir(parents=True)
        (tmp_path / "q" / "world" / "playerdata"
         / "69f230e1-af2c-4d0e-9885-93d66595a855.dat").write_bytes(b"x")
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nonline-mode=true\n", encoding="utf-8")

        assert ts.spieleridentitaet_warnung(quelle, ziel) is None


# --- Richtungs- und Typwarnungen ---------------------------------------------------------

def test_paper_nach_spigot_warnt(tmp_path, client):
    """Paper: 'Paper does not support migration to either CraftBukkit or Spigot!
    ... note that data loss is possible.'"""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper", version="26.2")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot", version="26.2",
                       port=25600)
        meldungen = ts.warnungen(quelle, ziel, welt=True)

    assert any("NICHT unterstuetzt" in m for m in meldungen)


def test_modded_nach_unmodded_warnt(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="neoforge",
                         version="1.21.1")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="1.21.1",
                       port=25600)
        meldungen = ts.warnungen(quelle, ziel, welt=True)

    assert any("Mods" in m and "verschwinden" in m for m in meldungen)


def test_spigot_nach_paper_warnt_nicht_unnoetig(tmp_path, client):
    """Die Richtung, die der Nutzer vorhat, ist unterstuetzt - hier darf keine
    Hersteller-Warnung erscheinen."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot", version="26.2")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="26.3",
                       port=25600)
        meldungen = ts.warnungen(quelle, ziel, welt=True)

    assert not any("NICHT unterstuetzt" in m for m in meldungen)


# --- Plugins/Mods an die Minecraft-Version gebunden -------------------------------------

def test_mods_nur_bei_gleicher_minecraft_version(tmp_path, client):
    """Mods fuer die falsche Version sind ein Absturz beim Start - und der Transfer loescht
    vorher die funktionierenden JARs des Ziels."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="fabric",
                         version="1.20.1")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="fabric", version="1.21",
                       port=25600)
        ergebnis = ts.moeglichkeiten(quelle, ziel)["erweiterungen"]

    assert ergebnis["moeglich"] is False
    assert "Minecraft-Version gebunden" in ergebnis["grund"]


def test_plugins_bei_versionssprung_mit_hinweis(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21.11")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", version="26.2",
                       port=25600)
        ergebnis = ts.moeglichkeiten(quelle, ziel)["erweiterungen"]

    assert ergebnis["moeglich"] is True
    assert "verschiedenen" in ergebnis["grund"]


# --- Weitere Folgefehler aus dem Review --------------------------------------------------

def test_allow_nether_wandert_nicht_mit(tmp_path, client, monkeypatch):
    """allow-nether=false der Quelle macht genau den Nether unerreichbar, den der Transfer
    vorher an den richtigen Platz gelegt hat."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=world\nserver-port=1\nallow-nether=false\nhardcore=true\n"
            "difficulty=hard\n", encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=False, configs=True, run_async=False)

    werte = _properties(tmp_path / "z")
    assert "allow-nether" not in werte
    assert "hardcore" not in werte
    assert werte["difficulty"] == "hard"


def test_plugins_update_ordner_wandert_nicht_mit(tmp_path, client, monkeypatch):
    """Bukkit wendet plugins/update/ beim naechsten Start an - das Ziel bekaeme ungefragt
    Plugin-Versionen, die bei der Quelle noch nicht einmal aktiv waren."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper", port=25600)
        (tmp_path / "q" / "plugins" / "update").mkdir(parents=True)
        (tmp_path / "q" / "plugins" / "Gut.jar").write_bytes(b"aktiv")
        (tmp_path / "q" / "plugins" / "update" / "Ungetestet.jar").write_bytes(b"wartet")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=False, erweiterungen=True,
                      run_async=False)

    assert (tmp_path / "z" / "plugins" / "Gut.jar").is_file()
    assert not (tmp_path / "z" / "plugins" / "update").exists()


def test_config_ordner_nur_bei_passender_familie(tmp_path, client, monkeypatch):
    """config/ heisst bei Paper 'die YAMLs' und bei Forge 'die Mod-Configs'."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="paper", version="26.2")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="forge", version="1.21.1",
                       port=25600)
        (tmp_path / "q" / "config").mkdir()
        (tmp_path / "q" / "config" / "paper-global.yml").write_text("a: 1", encoding="utf-8")

        meldungen = ts._uebernimm_configs(quelle, ziel)

    assert not (tmp_path / "z" / "config" / "paper-global.yml").exists()
    assert any("NICHT uebernommen" in m for m in meldungen)


def test_vorschau_liefert_warnungen_an_die_oberflaeche(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Quelle", slug="quelle", typ="paper",
                         version="26.2")
        ziel = _server(db, tmp_path / "z", name="Ziel", slug="ziel", typ="spigot",
                       version="26.2", port=25600)
        haupt = tmp_path / "q" / "world"
        _dimension(haupt / "dimensions" / "minecraft" / "the_nether", b"n")
        _level_dat(haupt / "level.dat")
        quell_id, ziel_id = quelle.id, ziel.id

    _login_admin(client)
    daten = client.get(
        f"/api/servers/{ziel_id}/transfer-preview?source_id={quell_id}").json()
    assert daten["blocker"] == []
    assert any("NICHT unterstuetzt" in w for w in daten["warnungen"])


# =========================================================================================
# Nahtloser Umzug: Spieleridentitaet, weitere Welten, Plugin-Daten mit Weltnamen.
# =========================================================================================

ALT_V4 = "69f230e1-af2c-4d0e-9885-93d66595a855"
NEU_V3 = "79668c7e-c702-3ba1-9b9e-20219be4de43"


def _usercache(base: Path, name: str, kennung: str) -> None:
    (base / "usercache.json").write_text(
        '[{"name":"' + name + '","uuid":"' + kennung + '"}]', encoding="utf-8")


def test_transfer_stellt_die_spieleridentitaet_um(tmp_path, client, monkeypatch):
    """Haustier-Besitzer und Dorfbewohner-Ruf stehen in den Chunks, nicht in Spielerdateien.
    Ohne Umschreiben gehoert nach dem Umzug kein Tier mehr jemandem."""
    import gzip
    import struct
    import zlib

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts, world_identity_service as wis

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper",
                       version="1.21.11", port=25600)
        # Quelle: online-mode, Ziel: offline ohne Proxy -> Identitaet wechselt.
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=world\nonline-mode=true\n", encoding="utf-8")
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nserver-port=25600\nonline-mode=false\n", encoding="utf-8")
        _usercache(tmp_path / "q", "Thormak2002", ALT_V4)

        welt = tmp_path / "q" / "world"
        _welt_getrennt(tmp_path / "q")
        # Spielerdatei und ein "Haustier" in den Entity-Chunks.
        (welt / "playerdata" / f"{ALT_V4}.dat").write_bytes(
            gzip.compress(b"inventar" + wis.uuid_bytes(ALT_V4)))
        innen = (b"\x07" + struct.pack(">H", 5) + b"Owner"
                 + struct.pack(">i", 16) + wis.uuid_bytes(ALT_V4))
        nbt = b"\x0a" + struct.pack(">H", 0) + innen + b"\x00"
        gepackt = zlib.compress(nbt)
        block = struct.pack(">I", len(gepackt) + 1) + b"\x02" + gepackt
        kopf = bytearray(4096)
        kopf[0:3] = (2).to_bytes(3, "big")
        kopf[3] = 1
        (welt / "entities").mkdir(exist_ok=True)
        (welt / "entities" / "r.0.0.mca").write_bytes(
            bytes(kopf) + bytes(4096) + block + bytes((-len(block)) % 4096))

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, spielerdaten=True,
                      run_async=False)

    ziel_welt = tmp_path / "z" / "world"
    # Spielerdatei traegt jetzt die Offline-UUID ...
    assert (ziel_welt / "playerdata" / f"{NEU_V3}.dat").is_file()
    assert not (ziel_welt / "playerdata" / f"{ALT_V4}.dat").exists()
    # ... und der Haustier-Besitzer in den Chunks wurde mitgezogen.
    region = (ziel_welt / "entities" / "r.0.0.mca").read_bytes()
    laenge = int.from_bytes(region[8192:8196], "big")
    roh = zlib.decompress(region[8197:8196 + laenge])
    assert wis.uuid_bytes(NEU_V3) in roh
    assert wis.uuid_bytes(ALT_V4) not in roh


def test_identitaet_bleibt_wenn_die_arten_passen(tmp_path, client, monkeypatch):
    """Passt die Anmeldeart, darf nichts angefasst werden."""
    import gzip

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts, world_identity_service as wis

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper",
                       version="1.21.11", port=25600)
        for ordner, mode in ((tmp_path / "q", "true"), (tmp_path / "z", "true")):
            (ordner / "server.properties").write_text(
                f"level-name=world\nonline-mode={mode}\n", encoding="utf-8")
        _usercache(tmp_path / "q", "Thormak2002", ALT_V4)
        _welt_getrennt(tmp_path / "q")
        (tmp_path / "q" / "world" / "playerdata" / f"{ALT_V4}.dat").write_bytes(
            gzip.compress(b"inventar"))

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "z" / "world" / "playerdata" / f"{ALT_V4}.dat").is_file()
    assert not (tmp_path / "z" / "world" / "playerdata" / f"{NEU_V3}.dat").exists()


def test_velocity_backend_loest_keine_umstellung_aus(tmp_path, client, monkeypatch):
    """Dein Fall: Paper-Backend mit online-mode=false hinter Velocity bekommt die echten
    UUIDs vom Proxy - hier darf NICHT auf Offline-UUIDs umgestellt werden."""
    import gzip

    import yaml

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="paper",
                       version="1.21.11", port=25600)
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=world\nonline-mode=true\n", encoding="utf-8")
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nserver-port=25600\nonline-mode=false\n", encoding="utf-8")
        (tmp_path / "z" / "config").mkdir()
        (tmp_path / "z" / "config" / "paper-global.yml").write_text(
            yaml.safe_dump({"proxies": {"velocity": {"enabled": True, "secret": "s"}}}),
            encoding="utf-8")
        _usercache(tmp_path / "q", "Thormak2002", ALT_V4)
        _welt_getrennt(tmp_path / "q")
        (tmp_path / "q" / "world" / "playerdata" / f"{ALT_V4}.dat").write_bytes(
            gzip.compress(b"inventar"))

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    # Die echte UUID bleibt - der Proxy liefert sie weiter.
    assert (tmp_path / "z" / "world" / "playerdata" / f"{ALT_V4}.dat").is_file()


# --- Weitere Welten (Multiverse & Co.) ---------------------------------------------------

def test_zusatzwelten_werden_erkannt(tmp_path, client):
    """Erkennung an der level.dat - plugins/, mods/ und logs/ sind keine Welten."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        srv = _server(db, tmp_path / "a", name="A", slug="a", typ="spigot", version="1.21")
        _welt_getrennt(tmp_path / "a")
        # Zwei weitere Welten, eine davon mit eigener Nether-Huelle.
        _welt_getrennt(tmp_path / "a", name="creative", end=False)
        _dimension(tmp_path / "a" / "minigames")
        _level_dat(tmp_path / "a" / "minigames" / "level.dat")
        # Kein Weltordner: keine level.dat
        (tmp_path / "a" / "plugins").mkdir(exist_ok=True)
        (tmp_path / "a" / "logs").mkdir(exist_ok=True)

        namen = ts.zusatz_welten(srv)

    # creative_nether ist die Huelle von creative, keine eigene Welt.
    assert namen == ["creative", "minigames"]


def test_zusatzwelten_werden_mituebernommen(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "q", name="creative", end=False)
        (tmp_path / "q" / "creative" / "region" / "r.0.0.mca").write_bytes(b"CREATIVE")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, zusatzwelten=True,
                      run_async=False)

    # Der Name bleibt, sonst finden die Plugins die Welt nicht mehr.
    assert (tmp_path / "z" / "creative" / "region" / "r.0.0.mca").read_bytes() == b"CREATIVE"
    assert (tmp_path / "z" / "creative_nether" / "DIM-1").is_dir()


def test_zusatzwelten_werden_umgebaut(tmp_path, client, monkeypatch):
    """Auch die weiteren Welten brauchen den Struktur-Umbau, nicht nur die Hauptwelt."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="forge",
                       version="1.21.1", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "q", name="creative", end=False)

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, zusatzwelten=True,
                      run_async=False)

    # Forge erwartet die Dimension IM Weltordner.
    assert (tmp_path / "z" / "creative" / "DIM-1" / "region" / "r.0.0.mca").is_file()
    assert not (tmp_path / "z" / "creative_nether").exists()


def test_ohne_haken_bleiben_zusatzwelten_zurueck(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "q", name="creative", end=False)

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert not (tmp_path / "z" / "creative").exists()


def test_vorschau_nennt_die_zusatzwelten(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Quelle", slug="quelle", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Ziel", slug="ziel", typ="spigot",
                       version="1.21", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "q", name="creative", end=False)
        quell_id, ziel_id = quelle.id, ziel.id

    _login_admin(client)
    daten = client.get(
        f"/api/servers/{ziel_id}/transfer-preview?source_id={quell_id}").json()
    assert daten["zusatzwelten"] == ["creative"]


# --- Plugin-Daten, die Welten beim Namen nennen ------------------------------------------

def test_plugin_weltordner_werden_mit_umbenannt(tmp_path, client):
    """WorldGuard-Regionen liegen unter plugins/<Plugin>/worlds/<weltname>/ - nach einer
    Umbenennung der Welt zeigen sie ins Leere."""
    from app.services import transfer_service as ts

    base = tmp_path / "z"
    for rel in ("plugins/WorldGuard/worlds/lobby", "plugins/WorldGuard/worlds/lobby_nether",
                "plugins/Essentials/lobby", "plugins/Fremd/andereWelt"):
        (base / rel).mkdir(parents=True)
        (base / rel / "daten.yml").write_text("x", encoding="utf-8")

    meldungen = ts._passe_plugin_weltnamen_an(base, "lobby", "welt")

    assert (base / "plugins/WorldGuard/worlds/welt/daten.yml").is_file()
    assert (base / "plugins/WorldGuard/worlds/welt_nether/daten.yml").is_file()
    assert (base / "plugins/Essentials/welt/daten.yml").is_file()
    assert not (base / "plugins/WorldGuard/worlds/lobby").exists()
    # Fremde Ordner bleiben unberuehrt.
    assert (base / "plugins/Fremd/andereWelt/daten.yml").is_file()
    assert meldungen and "lobby" in meldungen[0]


def test_yaml_weltschluessel_werden_mit_umbenannt(tmp_path, client):
    import yaml

    from app.services import transfer_service as ts

    base = tmp_path / "z"
    base.mkdir()
    (base / "spigot.yml").write_text(
        yaml.safe_dump({"world-settings": {"lobby": {"merge-radius": 2}, "default": {}}}),
        encoding="utf-8")
    (base / "bukkit.yml").write_text(
        yaml.safe_dump({"worlds": {"lobby": {"generator": "x"}}}), encoding="utf-8")

    ts._passe_plugin_weltnamen_an(base, "lobby", "welt")

    spigot = yaml.safe_load((base / "spigot.yml").read_text(encoding="utf-8"))
    bukkit = yaml.safe_load((base / "bukkit.yml").read_text(encoding="utf-8"))
    assert spigot["world-settings"]["welt"] == {"merge-radius": 2}
    assert "lobby" not in spigot["world-settings"]
    assert "default" in spigot["world-settings"]
    assert bukkit["worlds"]["welt"] == {"generator": "x"}


def test_gleicher_weltname_aendert_nichts(tmp_path, client):
    from app.services import transfer_service as ts

    base = tmp_path / "z"
    (base / "plugins/WorldGuard/worlds/world").mkdir(parents=True)
    assert ts._passe_plugin_weltnamen_an(base, "world", "world") == []
    assert (base / "plugins/WorldGuard/worlds/world").is_dir()


def test_plugin_transfer_zieht_den_weltnamen_mit(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21", level="lobby")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", level="welt", port=25600)
        _welt_getrennt(tmp_path / "q", name="lobby")
        (tmp_path / "q" / "plugins" / "WorldGuard" / "worlds" / "lobby").mkdir(parents=True)
        (tmp_path / "q" / "plugins" / "WorldGuard" / "worlds" / "lobby" / "regions.yml"
         ).write_text("regionen", encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, erweiterungen=True,
                      run_async=False)

    # Welt heisst am Ziel "welt" - die Plugin-Daten muessen mitgewandert sein.
    assert (tmp_path / "z" / "welt" / "region" / "r.0.0.mca").is_file()
    assert (tmp_path / "z" / "plugins" / "WorldGuard" / "worlds" / "welt" / "regions.yml"
            ).read_text(encoding="utf-8") == "regionen"


# --- fml-Registry-Warnung ----------------------------------------------------------------

def test_forge_registry_warnung(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="neoforge",
                         version="1.21.1")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="neoforge",
                       version="1.21.1", port=25600)
        meldungen = ts.warnungen(quelle, ziel, welt=True)

    assert any("Registry" in m for m in meldungen)


# --- Welt-Identitaet und level.dat beim Transfer ----------------------------------------

def test_welt_uuid_wandert_mit_und_quelle_wird_entwertet(tmp_path, client, monkeypatch):
    """Ohne uid.dat erscheint jeder, der im Nether ausgeloggt war, in der Overworld.
    Damit es die Identitaet nicht zweimal gibt, verliert die Quelle ihre."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        _welt_getrennt(tmp_path / "q")
        kennung = bytes(range(16))
        (tmp_path / "q" / "world" / "uid.dat").write_bytes(kennung)

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "z" / "world" / "uid.dat").read_bytes() == kennung
    assert not (tmp_path / "q" / "world" / "uid.dat").exists()
    # session.lock bleibt in jedem Fall zurueck.
    assert not (tmp_path / "z" / "world" / "session.lock").exists()


def test_drachenkampf_wandert_beim_zusammenlegen_mit(tmp_path, client, monkeypatch):
    """Spigot -> Forge: die End-Welt verschwindet als eigene Welt, ihr Drachenstand muss
    vorher in die Hauptwelt. Beim Nutzer stand dort 0, im End 1."""
    import gzip

    from app.db.session import SessionLocal
    from app.services import nbt_codec as nc, transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)

    def _lvl(pfad, besiegt):
        daten = nc.Tag(nc.TAG_COMPOUND, {
            "DragonFight": nc.Tag(nc.TAG_COMPOUND, {
                "DragonKilled": nc.Tag(nc.TAG_BYTE, besiegt),
                "PreviouslyKilled": nc.Tag(nc.TAG_BYTE, besiegt)}),
            "LevelName": nc.Tag(nc.TAG_STRING, "world")})
        pfad.parent.mkdir(parents=True, exist_ok=True)
        pfad.write_bytes(gzip.compress(
            nc.schreibe("", nc.Tag(nc.TAG_COMPOUND, {"Data": daten}))))

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="forge",
                       version="1.21.1", port=25600)
        _welt_getrennt(tmp_path / "q")
        _lvl(tmp_path / "q" / "world" / "level.dat", 0)
        _lvl(tmp_path / "q" / "world_the_end" / "level.dat", 1)

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    _, wurzel, _ = nc.lies_datei(tmp_path / "z" / "world" / "level.dat")
    kampf = nc.daten_wurzel(wurzel).hole("DragonFight")
    assert kampf.hole("DragonKilled").wert == 1, "Drache wuerde erneut erscheinen"


def test_weltname_in_der_level_dat_wird_gesetzt(tmp_path, client, monkeypatch):
    import gzip

    from app.db.session import SessionLocal
    from app.services import nbt_codec as nc, transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21", level="lobby")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", level="welt", port=25600)
        _welt_getrennt(tmp_path / "q", name="lobby")
        daten = nc.Tag(nc.TAG_COMPOUND, {"LevelName": nc.Tag(nc.TAG_STRING, "lobby")})
        (tmp_path / "q" / "lobby" / "level.dat").write_bytes(gzip.compress(
            nc.schreibe("", nc.Tag(nc.TAG_COMPOUND, {"Data": daten}))))

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    _, wurzel, _ = nc.lies_datei(tmp_path / "z" / "welt" / "level.dat")
    assert nc.daten_wurzel(wurzel).hole("LevelName").wert == "welt"


def test_gesperrte_weltdatei_bricht_ab(tmp_path, client, monkeypatch):
    """Eine fehlende region/*.mca sind 512x512 Bloecke - lieber abbrechen, als 'fertig' zu
    melden, waehrend ein ganzer Landstrich fehlt."""
    from app.db.session import SessionLocal
    from app.services import server_service, transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    echtes_kopieren = server_service._robust_copy_tree

    def _mit_luecke(src, dst, **kwargs):
        return echtes_kopieren(src, dst, **kwargs) + ["region/r.0.0.mca"]

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        _welt_getrennt(tmp_path / "q")
        _welt_getrennt(tmp_path / "z", nether=False, end=False)
        (tmp_path / "z" / "world" / "region" / "r.0.0.mca").write_bytes(b"ZIEL-ORIGINAL")
        monkeypatch.setattr(server_service, "_robust_copy_tree", _mit_luecke)

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    assert (tmp_path / "z" / "world" / "region" / "r.0.0.mca"
            ).read_bytes() == b"ZIEL-ORIGINAL"


def test_weltbezogene_properties_wandern_mit_der_welt(tmp_path, client, monkeypatch):
    """Dieselbe Welt soll sich gleich spielen - Schwierigkeit und PvP gehoeren dazu."""
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=world\nonline-mode=true\ndifficulty=hard\npvp=false\n"
            "view-distance=12\n", encoding="utf-8")
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nserver-port=25600\nonline-mode=true\ndifficulty=easy\n"
            "pvp=true\n", encoding="utf-8")
        _welt_getrennt(tmp_path / "q")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    werte = _properties(tmp_path / "z")
    assert werte["difficulty"] == "hard"
    assert werte["pvp"] == "false"
    assert werte["view-distance"] == "12"
    assert werte["server-port"] == "25600"


def test_usercache_wird_zusammengefuehrt(tmp_path, client, monkeypatch):
    import json

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        (tmp_path / "q" / "usercache.json").write_text(json.dumps(
            [{"name": "Quellspieler", "uuid": ALT_V4}]), encoding="utf-8")
        (tmp_path / "z" / "usercache.json").write_text(json.dumps(
            [{"name": "Zielspieler", "uuid": "11111111-2222-4333-8444-555555555555"}]),
            encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=False, configs=True,
                      run_async=False)

    namen = {e["name"] for e in json.loads(
        (tmp_path / "z" / "usercache.json").read_text(encoding="utf-8"))}
    assert namen == {"Quellspieler", "Zielspieler"}


def test_zugangslisten_werden_auf_wunsch_uebernommen(tmp_path, client, monkeypatch):
    import json

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        (tmp_path / "q" / "ops.json").write_text(json.dumps(
            [{"uuid": ALT_V4, "name": "Thormak2002", "level": 4}]), encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=False, zugangslisten=True,
                      run_async=False)

    ops = json.loads((tmp_path / "z" / "ops.json").read_text(encoding="utf-8"))
    assert ops[0]["name"] == "Thormak2002"
    assert ops[0]["level"] == 4


def test_zugangslisten_uuids_werden_mit_umgestellt(tmp_path, client, monkeypatch):
    """Der sichtbarste Effekt: mit falscher UUID-Art kommt niemand durch die Whitelist."""
    import gzip
    import json

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts, world_identity_service as wis

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=world\nonline-mode=true\n", encoding="utf-8")
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nserver-port=25600\nonline-mode=false\n", encoding="utf-8")
        _usercache(tmp_path / "q", "Thormak2002", ALT_V4)
        _welt_getrennt(tmp_path / "q")
        (tmp_path / "q" / "world" / "playerdata" / f"{ALT_V4}.dat").write_bytes(
            gzip.compress(b"inventar"))
        (tmp_path / "q" / "ops.json").write_text(json.dumps(
            [{"uuid": ALT_V4, "name": "Thormak2002", "level": 4}]), encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, zugangslisten=True,
                      run_async=False)

    ops = json.loads((tmp_path / "z" / "ops.json").read_text(encoding="utf-8"))
    assert ops[0]["uuid"] == NEU_V3, "Operator wuerde auf dem Offline-Server nicht greifen"


def test_plugin_uuids_werden_beim_transfer_umgestellt(tmp_path, client, monkeypatch):
    import gzip

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=world\nonline-mode=true\n", encoding="utf-8")
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nserver-port=25600\nonline-mode=false\n", encoding="utf-8")
        _usercache(tmp_path / "q", "Thormak2002", ALT_V4)
        _welt_getrennt(tmp_path / "q")
        (tmp_path / "q" / "world" / "playerdata" / f"{ALT_V4}.dat").write_bytes(
            gzip.compress(b"inventar"))
        (tmp_path / "q" / "plugins" / "DeadChest").mkdir(parents=True)
        (tmp_path / "q" / "plugins" / "DeadChest" / "chests.yml").write_text(
            f"kiste1:\n  owner: {ALT_V4}\n", encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, erweiterungen=True,
                      run_async=False)

    inhalt = (tmp_path / "z" / "plugins" / "DeadChest" / "chests.yml").read_text(
        encoding="utf-8")
    assert NEU_V3 in inhalt, "Todeskiste liesse sich nicht mehr oeffnen"
    assert ALT_V4 not in inhalt


def test_weltname_im_plugin_inhalt_wird_ersetzt(tmp_path, client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    _ohne_backup(monkeypatch, tmp_path)
    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21", level="lobby")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", level="welt", port=25600)
        _welt_getrennt(tmp_path / "q", name="lobby")
        (tmp_path / "q" / "plugins" / "Essentials").mkdir(parents=True)
        (tmp_path / "q" / "plugins" / "Essentials" / "homes.yml").write_text(
            "zuhause:\n  world: lobby\n", encoding="utf-8")

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, erweiterungen=True,
                      run_async=False)

    inhalt = (tmp_path / "z" / "plugins" / "Essentials" / "homes.yml").read_text(
        encoding="utf-8")
    assert "world: welt" in inhalt, "/home wuerde eine unbekannte Welt melden"


def test_allow_nether_false_am_ziel_warnt(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import transfer_service as ts

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nallow-nether=false\n", encoding="utf-8")

        meldungen = ts.warnungen(quelle, ziel, welt=True)

    assert any("allow-nether" in m for m in meldungen)


def test_identitaet_erfasst_nether_und_end(tmp_path, client, monkeypatch):
    """Der Fehler, den die Recherche fand: die Umstellung lief nur ueber den
    Hauptweltordner. In der echten Welt des Nutzers stecken 65 Vorkommen im Nether."""
    import gzip
    import struct
    import zlib

    from app.db.session import SessionLocal
    from app.services import transfer_service as ts, world_identity_service as wis

    _ohne_backup(monkeypatch, tmp_path)

    def _region(pfad, nutz):
        nbt = (b"\x0a" + struct.pack(">H", 0)
               + b"\x07" + struct.pack(">H", 5) + b"Owner"
               + struct.pack(">i", len(nutz)) + nutz + b"\x00")
        gepackt = zlib.compress(nbt)
        block = struct.pack(">I", len(gepackt) + 1) + b"\x02" + gepackt
        kopf = bytearray(4096)
        kopf[0:3] = (2).to_bytes(3, "big")
        kopf[3] = 1
        pfad.parent.mkdir(parents=True, exist_ok=True)
        pfad.write_bytes(bytes(kopf) + bytes(4096) + block
                         + bytes((-len(block)) % 4096))

    with SessionLocal() as db:
        quelle = _server(db, tmp_path / "q", name="Q", slug="q", typ="spigot",
                         version="1.21")
        ziel = _server(db, tmp_path / "z", name="Z", slug="z", typ="spigot",
                       version="1.21", port=25600)
        (tmp_path / "q" / "server.properties").write_text(
            "level-name=world\nonline-mode=true\n", encoding="utf-8")
        (tmp_path / "z" / "server.properties").write_text(
            "level-name=world\nserver-port=25600\nonline-mode=false\n", encoding="utf-8")
        _usercache(tmp_path / "q", "Thormak2002", ALT_V4)
        _welt_getrennt(tmp_path / "q")
        (tmp_path / "q" / "world" / "playerdata" / f"{ALT_V4}.dat").write_bytes(
            gzip.compress(b"inventar"))
        # Haustier im Nether UND im End - beide liegen NEBEN dem Hauptweltordner.
        _region(tmp_path / "q" / "world_nether" / "DIM-1" / "entities" / "r.0.0.mca",
                wis.uuid_bytes(ALT_V4))
        _region(tmp_path / "q" / "world_the_end" / "DIM1" / "entities" / "r.0.0.mca",
                wis.uuid_bytes(ALT_V4))

        ts.uebertrage(db, quelle=quelle, ziel=ziel, welt=True, run_async=False)

    def _owner(pfad):
        daten = pfad.read_bytes()
        laenge = int.from_bytes(daten[8192:8196], "big")
        return zlib.decompress(daten[8197:8196 + laenge])

    for rel in ("world_nether/DIM-1/entities/r.0.0.mca",
                "world_the_end/DIM1/entities/r.0.0.mca"):
        roh = _owner(tmp_path / "z" / rel)
        assert wis.uuid_bytes(NEU_V3) in roh, f"{rel}: Haustier gehoert niemandem mehr"
        assert wis.uuid_bytes(ALT_V4) not in roh
