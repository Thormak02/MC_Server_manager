"""Tests fuer die maximale Spielerzahl in den Servereinstellungen.

Der Wert lebt in server.properties, nicht in der Datenbank - der Minecraft-Server
liest ihn beim Start. Entsprechend pruefen die Tests, dass die DATEI geschrieben
wird und dass ein nicht mitgeschicktes Feld den vorhandenen Wert in Ruhe laesst.
"""
from pathlib import Path

import pytest


def _login_admin(client):
    antwort = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303


def _make_server(db, base: Path, *, properties="max-players=20\n",
                 name="Testserver", slug="testserver"):
    from app.models.server import Server

    base.mkdir(parents=True, exist_ok=True)
    if properties is not None:
        (base / "server.properties").write_text(
            "level-name=world\nserver-port=25599\n" + properties, encoding="utf-8"
        )

    srv = Server(
        name=name, slug=slug, server_type="paper",
        mc_version="1.21.11", base_path=str(base), port=25599, status="stopped",
        start_mode="bat", start_bat_path=str(base / "start.bat"),
    )
    db.add(srv)
    db.commit()
    db.refresh(srv)
    return srv


def _properties(base: Path) -> dict:
    werte = {}
    for zeile in (base / "server.properties").read_text(encoding="utf-8").splitlines():
        if "=" in zeile and not zeile.startswith("#"):
            schluessel, wert = zeile.split("=", 1)
            werte[schluessel.strip()] = wert.strip()
    return werte


def test_read_max_players_from_file(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "a", properties="max-players=69\n")
        assert server_service.read_max_players(srv) == 69


def test_read_max_players_missing_is_none(tmp_path, client):
    """Ohne Datei bzw. ohne Schluessel: None statt Absturz. Genau das ist der
    Normalfall bei einem Server, der noch nie gestartet wurde."""
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        ohne_datei = _make_server(db, tmp_path / "leer", properties=None)
        assert server_service.read_max_players(ohne_datei) is None

    with SessionLocal() as db:
        ohne_schluessel = _make_server(
            db, tmp_path / "b", properties="motd=Hallo\n",
            name="Zweiter", slug="zweiter",
        )
        assert server_service.read_max_players(ohne_schluessel) is None


def test_read_max_players_ignores_garbage(tmp_path, client):
    """Ein kaputter Wert darf die Einstellungsseite nicht zerlegen."""
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "a", properties="max-players=viele\n")
        assert server_service.read_max_players(srv) is None


def test_validate_max_players_bounds():
    from app.services.server_service import MAX_PLAYERS_LIMIT, validate_max_players

    assert validate_max_players(None) is None
    assert validate_max_players(0) == 0           # Server dichtmachen ist erlaubt
    assert validate_max_players(20) == 20
    assert validate_max_players(MAX_PLAYERS_LIMIT) == MAX_PLAYERS_LIMIT

    with pytest.raises(ValueError, match="negativ"):
        validate_max_players(-1)
    with pytest.raises(ValueError, match="hoechstens"):
        validate_max_players(MAX_PLAYERS_LIMIT + 1)


def test_settings_route_writes_max_players(tmp_path, client):
    """Der eigentliche Vertrag: die Zahl landet in server.properties - und die
    uebrigen Eintraege der Datei bleiben dabei unangetastet."""
    from app.db.session import SessionLocal

    basis = tmp_path / "a"
    with SessionLocal() as db:
        sid = _make_server(db, basis).id

    _login_admin(client)
    antwort = client.post(
        f"/servers/{sid}/settings",
        data={"mc_version": "1.21.11", "port": "25599", "max_players": "69"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303

    werte = _properties(basis)
    assert werte["max-players"] == "69"
    assert werte["level-name"] == "world"


def test_settings_route_without_field_keeps_value(tmp_path, client):
    """Kein Feld mitgeschickt heisst "nicht anfassen" - nicht "auf 0 setzen".
    Sonst wuerde jedes Speichern ohne das Feld den Server stillschweigend dichtmachen."""
    from app.db.session import SessionLocal

    basis = tmp_path / "a"
    with SessionLocal() as db:
        sid = _make_server(db, basis, properties="max-players=42\n").id

    _login_admin(client)
    antwort = client.post(
        f"/servers/{sid}/settings",
        data={"mc_version": "1.21.11", "port": "25599"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303
    assert _properties(basis)["max-players"] == "42"


def test_settings_route_rejects_invalid_max_players(tmp_path, client):
    """Eine ungueltige Zahl bricht das Speichern ab, statt die Haelfte zu uebernehmen."""
    from app.db.session import SessionLocal
    from app.models.server import Server

    basis = tmp_path / "a"
    with SessionLocal() as db:
        sid = _make_server(db, basis, properties="max-players=42\n").id

    _login_admin(client)
    antwort = client.post(
        f"/servers/{sid}/settings",
        data={
            "mc_version": "1.21.11",
            "port": "25599",
            "max_players": "-5",
            "memory_max_mb": "8192",
        },
        follow_redirects=False,
    )
    assert antwort.status_code == 303

    assert _properties(basis)["max-players"] == "42"
    with SessionLocal() as db:
        assert db.get(Server, sid).memory_max_mb != 8192


def test_settings_page_prefills_max_players(tmp_path, client):
    """Das Feld muss den Wert AUS DER DATEI zeigen."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        sid = _make_server(db, tmp_path / "a", properties="max-players=69\n").id

    _login_admin(client)
    seite = client.get(f"/servers/{sid}")
    assert seite.status_code == 200
    assert 'name="max_players"' in seite.text
    assert 'value="69"' in seite.text


def test_validate_max_players_accepts_raw_form_values():
    """Der Formularwert kommt als Text an - leer heisst "nicht anfassen",
    Unsinn bekommt einen deutschen Satz statt der Python-Fehlermeldung."""
    from app.services.server_service import validate_max_players

    assert validate_max_players("") is None
    assert validate_max_players("   ") is None
    assert validate_max_players("69") == 69
    assert validate_max_players("0") == 0

    with pytest.raises(ValueError, match="ganze Zahl"):
        validate_max_players("viele")
    with pytest.raises(ValueError, match="ganze Zahl"):
        validate_max_players("12.5")
    with pytest.raises(ValueError, match="hoechstens"):
        validate_max_players("99999")
