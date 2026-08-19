"""Tests fuer die Version-Aufloesung (content_type-abhaengig) und die
oeffentliche Basis-URL / Zieldomain-Einstellung."""


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _make_server(base_path, *, server_type="spigot", mc_version="1.21.1"):
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        srv = Server(
            name="ver-srv",
            slug="ver-srv",
            server_type=server_type,
            mc_version=mc_version,
            base_path=str(base_path),
        )
        db.add(srv)
        db.commit()
        db.refresh(srv)
        return srv.id


def test_modrinth_versions_endpoint_respects_content_type(client, monkeypatch, tmp_path):
    """Regression: der Modrinth-Versions-Endpunkt haengte auf content_type='mod'
    fest -> auf einem Spigot-Server lieferte er fuer Plugins/Datapacks/
    Resourcepacks IMMER eine leere Liste ("Keine passende Version gefunden")."""
    from app.services import content_service

    base = tmp_path / "srv"
    base.mkdir()
    (base / "server.properties").write_text("level-name=world\n", encoding="utf-8")
    server_id = _make_server(base, server_type="spigot")

    _login_admin(client)

    calls = []

    def fake_versions(project_id, mc_version, loader, release_channel="all"):
        calls.append({"loader": loader, "mc_version": mc_version})
        return [{"id": "v1", "name": "1.0", "version_number": "1.0"}]

    monkeypatch.setattr(content_service, "list_modrinth_versions", fake_versions)

    # Resource Pack: loader-unabhaengig -> Versionen kommen zurueck.
    resp = client.get(
        "/api/content/modrinth/versions",
        params={
            "project_id": "abc",
            "server_id": server_id,
            "content_type": "resourcepack",
            "loader": "",
        },
    )
    assert resp.status_code == 200
    assert resp.json()["versions"], "resourcepack sollte Versionen liefern"
    assert calls[-1]["loader"] in (None, "")

    # Plugin: Loader wird auf den Server-Loader (spigot) gesetzt.
    resp = client.get(
        "/api/content/modrinth/versions",
        params={"project_id": "abc", "server_id": server_id, "content_type": "plugin"},
    )
    assert resp.status_code == 200
    assert resp.json()["versions"]
    assert calls[-1]["loader"] == "spigot"

    # Mod: auf einem Spigot-Server nicht unterstuetzt -> leer, ohne API-Aufruf.
    before = len(calls)
    resp = client.get(
        "/api/content/modrinth/versions",
        params={"project_id": "abc", "server_id": server_id, "content_type": "mod"},
    )
    assert resp.status_code == 200
    assert resp.json()["versions"] == []
    assert len(calls) == before, "mod auf Spigot darf keinen Versions-Abruf ausloesen"


def test_set_server_resource_pack_curseforge_403_is_friendly(
    client, monkeypatch, tmp_path
):
    """CurseForge blockt den API-Download vieler Resource Packs (HTTP 403).
    Statt des rohen '403' soll eine verstaendliche Meldung erscheinen."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import content_service

    monkeypatch.setattr(content_service, "_curseforge_headers", lambda: {})

    def fake_request_json(url, headers=None, **kw):
        if url.endswith("/download-url"):
            raise ValueError("HTTP 403: Forbidden")
        if "/files/" in url:
            return {"data": {"downloadUrl": "", "hashes": []}}
        return {"data": {"name": "Some Pack"}}

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)

    base = tmp_path / "cfrp"
    base.mkdir()
    (base / "server.properties").write_text("level-name=world\n", encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(
            name="cf-rp",
            slug="cf-rp",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()

        try:
            content_service.set_server_resource_pack(
                db, srv, "curseforge", "123", "456", None
            )
            assert False, "sollte ValueError werfen"
        except ValueError as exc:
            message = str(exc)
            assert "403" in message
            assert "Modrinth" in message


def test_public_base_url_setting_roundtrip(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        # Standard: keine UI/ENV-Quelle im Test.
        assert svc.get_public_base_url(db) == ""
        assert svc.get_public_base_url_source(db) == "default"

        # Setzen normalisiert (Trailing-Slash entfernen).
        assert (
            svc.set_public_base_url(db, "http://thormakmc.ddns.net:8000/")
            == "http://thormakmc.ddns.net:8000"
        )
        assert svc.get_public_base_url(db) == "http://thormakmc.ddns.net:8000"
        assert svc.get_public_base_url_source(db) == "ui"

        # Runtime-Getter (eigene Session) sieht den committeten Wert.
        assert svc.get_public_base_url_runtime() == "http://thormakmc.ddns.net:8000"

        # Ungueltiges Schema wird abgelehnt.
        for bad in ("thormakmc.ddns.net", "ftp://x", ""):
            try:
                svc.set_public_base_url(db, bad)
                assert False, f"sollte ValueError werfen fuer {bad!r}"
            except ValueError:
                pass

        # Zuruecksetzen entfernt den Override.
        assert svc.clear_public_base_url_override(db) == ""
        assert svc.get_public_base_url_source(db) == "default"


def test_settings_page_has_tabs(client):
    _login_admin(client)
    page = client.get("/settings")
    assert page.status_code == 200
    # Tab-Navigation + alle 5 Panels vorhanden.
    assert 'id="settings-nav"' in page.text
    for panel in ("netzwerk", "speicher", "java", "plattformen", "system"):
        assert f'data-settings-tab="{panel}"' in page.text
        assert f'data-settings-panel="{panel}"' in page.text


def test_public_url_settings_page_and_post(client):
    _login_admin(client)

    # Einstellungsseite rendert (inkl. neuer URL-Karte).
    page = client.get("/settings")
    assert page.status_code == 200
    assert "Oeffentliche URL" in page.text

    # Speichern per Formular.
    resp = client.post(
        "/settings/public-url",
        data={"public_base_url": "http://thormakmc.ddns.net:8000"},
        follow_redirects=False,
    )
    assert resp.status_code == 303

    page = client.get("/settings")
    assert "http://thormakmc.ddns.net:8000" in page.text
