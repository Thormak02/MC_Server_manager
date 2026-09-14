def _login(client):
    client.post("/login", data={"username": "admin", "password": "admin123!"})


def test_settings_page_shows_universal_lobby(client):
    _login(client)
    resp = client.get("/settings")
    assert resp.status_code == 200
    assert "Universal-Lobby" in resp.text
    assert 'action="/settings/universal-lobby"' in resp.text


def test_universal_lobby_settings_saved(client):
    _login(client)
    # Speichern mit gesetzten Ports, Toggles AUS (keine Bind-/Download-Nebenwirkungen).
    resp = client.post(
        "/settings/universal-lobby",
        data={
            "hub_lobby_port": "25599",
            "hub_lobby_vanilla_port": "25600",
            "viaproxy_port": "25601",
            "hub_lobby_replay": "atm10_capture.replay",
            "hub_lobby_vanilla_replay": "",
        },
    )
    assert resp.status_code == 200  # 303 -> folgt Redirect -> /settings

    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        assert A.get_hub_lobby_enabled(db) is False
        assert A.get_viaproxy_enabled(db) is False
        assert A.get_hub_lobby_port(db) == 25599
        assert A.get_hub_lobby_vanilla_port(db) == 25600
        assert A.get_viaproxy_port(db) == 25601


def test_universal_lobby_toggle_on(client):
    _login(client)
    # KEIN echter Jar-Download im Test (kein Netz); Ports lokal + frei waehlen.
    import app.services.viaproxy_service as V

    V._DOWNLOAD_TRIED = True
    try:
        resp = client.post(
            "/settings/universal-lobby",
            data={
                "hub_lobby_enabled": "true",
                "viaproxy_enabled": "true",
                "hub_lobby_port": "35599",
                "hub_lobby_vanilla_port": "35600",
                "viaproxy_port": "35601",
                "hub_lobby_replay": "atm10_capture.replay",
            },
        )
        assert resp.status_code == 200

        import app.db.session as dbs
        from app.services import app_setting_service as A

        with dbs.SessionLocal() as db:
            assert A.get_hub_lobby_enabled(db) is True
            assert A.get_viaproxy_enabled(db) is True
    finally:
        from app.services import hub_lobby_service, viaproxy_service

        hub_lobby_service.stop_hub_lobby()
        viaproxy_service.stop_viaproxy()


def test_sync_lobby_plugin_live_reloads_running_servers(client, monkeypatch, tmp_path):
    """Nach dem Schreiben der config.yml wird laufenden Bukkit-Servern `mcsmlobby reload`
    geschickt, damit ein frisch aktivierter Server sofort (ohne Lobby-Neustart) im Menue auftaucht."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import lobby_service

    calls = []
    monkeypatch.setattr(lobby_service, "_write_plugin_for_server",
                        lambda db, srv, is_lobby, lobby_target: (True, srv.name))
    monkeypatch.setattr(lobby_service, "_lobby_transfer_target", lambda db: None)
    monkeypatch.setattr("app.services.process_service.is_running", lambda sid: True)

    def fake_cmd(db, server, command, user_id):
        calls.append((server.id, command))
        return (True, "ok")

    monkeypatch.setattr("app.services.process_service.send_console_command", fake_cmd)

    with SessionLocal() as db:
        lobby = Server(name="Lobby", slug="lobby", server_type="paper", mc_version="1.21.1",
                       base_path=str(tmp_path / "lobby"), port=25569, status="running",
                       gateway_enabled=True, gateway_hostname="lobby", gateway_is_default=True)
        spg = Server(name="Spg", slug="spg", server_type="spigot", mc_version="26.2",
                     base_path=str(tmp_path / "spg"), port=25570, status="running",
                     gateway_enabled=True, gateway_hostname="26.2-spigot")
        db.add_all([lobby, spg])
        db.commit()
        lobby_id, spg_id = lobby.id, spg.id

        ok, _msg = lobby_service.sync_lobby_plugin(db)
        assert ok
        assert (lobby_id, "mcsmlobby reload") in calls
        assert (spg_id, "mcsmlobby reload") in calls
