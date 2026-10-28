"""Tests fuer das Velocity-Netzwerk (echter Proxy + neueste-Version-Lobby, alle Versionen)."""

from __future__ import annotations

import pytest

from pathlib import Path


def _login(client):
    client.post("/login", data={"username": "admin", "password": "admin123!"})


# --- velocity.toml Rendering ---------------------------------------------------
def test_render_velocity_toml_modern_forwarding():
    from app.services import proxy_service

    cfg = {"bind_port": 25565, "domain": "mc.example.de", "motd": "Hallo", "max_players": 80}
    backends = [
        {"name": "lobby", "address": "127.0.0.1:30001", "alias": "lobby", "is_lobby": True},
        {"name": "smp", "address": "127.0.0.1:30002", "alias": "smp", "is_lobby": False},
    ]
    toml = proxy_service.render_velocity_toml(cfg, backends, "lobby")
    assert 'bind = "127.0.0.1:25565"' in toml
    assert 'player-info-forwarding-mode = "modern"' in toml
    assert 'forwarding-secret-file = "forwarding.secret"' in toml
    assert '"lobby" = "127.0.0.1:30001"' in toml   # QUOTED key
    assert '"smp" = "127.0.0.1:30002"' in toml
    assert 'try = ["lobby"]' in toml
    assert '"lobby.mc.example.de" = ["lobby"]' in toml
    assert '"smp.mc.example.de" = ["smp"]' in toml
    assert "read-timeout = 185000" in toml
    # accepts-transfers MUSS im [advanced]-Block stehen (sonst ignoriert Velocity es) -
    # ohne das scheitern per Transfer-Paket transferierte 767-Vanilla-Clients.
    assert "accepts-transfers = true" in toml
    assert "accepts-transfers = true" in toml.split("[advanced]", 1)[1]
    # Der BungeeCord-Kanal traegt das interne Umschalten der Lobby ("Connect").
    # Fehlt er, verschwinden Menue-Klick und /lobby spurlos - ohne Meldung irgendwo.
    # Velocitys Vorgabe waere true, aber der Manager schreibt die Datei vollstaendig
    # selbst und soll sich nicht auf eine Vorgabe verlassen, die er nicht kontrolliert.
    assert "bungee-plugin-message-channel = true" in toml.split("[advanced]", 1)[1]


def test_render_velocity_toml_no_backends():
    from app.services import proxy_service

    cfg = {"bind_port": 25565, "domain": "", "motd": "x", "max_players": 20}
    toml = proxy_service.render_velocity_toml(cfg, [], None)
    assert "try = []" in toml           # kein Backend -> leere try-Liste, valides TOML
    assert 'bind = "127.0.0.1:25565"' in toml


def test_render_velocity_toml_quotes_dotted_backend_name():
    """Ein Alias mit Punkt (z.B. '1.21.1') muss als QUOTED key raus, sonst zerfaellt
    er in verschachtelte TOML-Tabellen und das Backend existiert nie."""
    from app.services import proxy_service

    be = [{"name": "1.21.1", "address": "127.0.0.1:30001", "alias": "1.21.1", "is_lobby": True}]
    toml = proxy_service.render_velocity_toml(
        {"bind_port": 25565, "domain": "mc.x.de", "motd": "x", "max_players": 10}, be, "1.21.1")
    assert '"1.21.1" = "127.0.0.1:30001"' in toml
    assert '"1.21.1.mc.x.de" = ["1.21.1"]' in toml   # forced-host mit dem quoted Namen


def test_render_velocity_toml_escapes_control_chars_in_motd():
    """Mehrzeilige MOTD darf die velocity.toml nicht zerstoeren (Basic-String verbietet
    rohe Steuerzeichen)."""
    from app.services import proxy_service

    toml = proxy_service.render_velocity_toml(
        {"bind_port": 25565, "domain": "", "motd": "Zeile1\nZeile2\tTab", "max_players": 10}, [], None)
    motd_line = next(l for l in toml.splitlines() if l.startswith("motd"))
    assert motd_line == 'motd = "Zeile1\\nZeile2\\tTab"'   # literal \n \t, kein roher Umbruch
    assert "\n" not in motd_line[7:]


# --- Backend-Auswahl (nur Bukkit-Typen sind Velocity-Backends) -----------------
def test_velocity_backends_only_bukkit_types(client):
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import proxy_service

    with dbs.SessionLocal() as db:
        lobby = Server(
            name="lobby", slug="vlob", server_type="paper", mc_version="26.2",
            base_path="C:/tmp/vlob", gateway_enabled=True, gateway_hostname="lobby",
            gateway_is_default=True, port=30001,
        )
        atm = Server(
            name="atm", slug="vatm", server_type="neoforge", mc_version="1.21.1",
            base_path="C:/tmp/vatm", gateway_enabled=True, gateway_hostname="atm10",
            port=30002,
        )
        db.add_all([lobby, atm])
        db.commit()

        backends, lobby_name = proxy_service.velocity_backends(db)
        names = {b["name"] for b in backends}
        assert "lobby" in names           # Paper -> Backend
        assert "atm10" not in names       # NeoForge -> KEIN Backend (nativer Transfer)
        assert lobby_name == "lobby"
        lobby_backend = next(b for b in backends if b["name"] == "lobby")
        assert lobby_backend["address"] == "127.0.0.1:30001"


# --- is_velocity_backend -------------------------------------------------------
def test_is_velocity_backend_gating():
    from app.models.server import Server
    from app.services import server_service

    paper = Server(name="p", slug="p", server_type="paper", base_path="C:/tmp/p",
                   gateway_enabled=True, port=1)
    forge = Server(name="f", slug="f", server_type="neoforge", base_path="C:/tmp/f",
                   gateway_enabled=True, port=2)

    assert server_service.is_velocity_backend(paper, network_mode="velocity") is True
    assert server_service.is_velocity_backend(forge, network_mode="velocity") is False
    assert server_service.is_velocity_backend(paper, network_mode="gateway") is False
    paper.gateway_enabled = False
    assert server_service.is_velocity_backend(paper, network_mode="velocity") is False


def test_spigot_is_not_velocity_backend(client):
    """Spigot/Bukkit sind KEINE Velocity-Backends (koennen kein modern forwarding) -> eigenstaendige
    online-mode-Server. Der Forwarding-Modus bleibt daher 'modern' (nur Paper-Lobby als Backend)."""
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import app_setting_service, server_service

    with dbs.SessionLocal() as db:
        app_setting_service.set_network_mode(db, "velocity")
        db.add(Server(name="lob", slug="fmlob", server_type="paper", mc_version="26.2",
                      base_path="C:/tmp/fmlob", gateway_enabled=True, gateway_is_default=True,
                      port=31001))
        spig = Server(name="smp", slug="fmsmp", server_type="spigot", mc_version="1.21.11",
                      base_path="C:/tmp/fmsmp", gateway_enabled=True, port=31002)
        db.add(spig)
        db.commit()
        # Spigot ist kein Backend -> laeuft eigenstaendig (direkt anspringbar).
        assert server_service.is_velocity_backend(spig, network_mode="velocity") is False
        # Nur Paper-Backends -> Modus bleibt modern (kein Legacy-Zwang durch Spigot).
        assert server_service.velocity_forwarding_mode(db) == "modern"


# --- Modern Forwarding schreiben ----------------------------------------------
def test_apply_velocity_backend_forwarding_writes_files(tmp_path):
    import yaml

    from app.models.server import Server
    from app.services import server_service

    base = tmp_path / "backend"
    base.mkdir()
    srv = Server(name="b", slug="b", server_type="paper", base_path=str(base),
                 gateway_enabled=True, port=30010)

    notes = server_service.apply_velocity_backend_forwarding(srv, "s3cr3t")
    assert any("Velocity-Forwarding aktiv" in n for n in notes)

    props = (base / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=false" in props
    assert "server-ip=127.0.0.1" in props

    data = yaml.safe_load((base / "config" / "paper-global.yml").read_text(encoding="utf-8"))
    vel = data["proxies"]["velocity"]
    assert vel["enabled"] is True
    assert vel["online-mode"] is True
    assert vel["secret"] == "s3cr3t"


def test_apply_velocity_backend_forwarding_failsafe_online_mode(tmp_path):
    """Wenn paper-global.yml NICHT schreibbar ist, darf der Server NICHT als offline-mode
    starten (sonst Username-Spoofing) -> Fail-safe online-mode=true."""
    from app.models.server import Server
    from app.services import server_service

    base = tmp_path / "backend2"
    base.mkdir()
    (base / "config").write_text("ich bin eine datei, kein ordner", encoding="utf-8")  # mkdir schlaegt fehl
    srv = Server(name="b2", slug="b2", server_type="paper", base_path=str(base),
                 gateway_enabled=True, port=30012)

    notes = server_service.apply_velocity_backend_forwarding(srv, "s3cr3t")
    props = (base / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=true" in props        # Fail-safe: KEIN offline-mode ohne Forwarding
    assert "online-mode=false" not in props
    assert any("online-mode=true erzwungen" in n for n in notes)


def test_render_velocity_toml_legacy_forwarding():
    """Legacy (BungeeCord) Modus: player-info-forwarding-mode=legacy + KEIN Secret-File."""
    from app.services import proxy_service

    cfg = {"bind_port": 25565, "domain": "mc.example.de", "motd": "Hallo", "max_players": 80}
    backends = [{"name": "smp", "address": "127.0.0.1:30002", "alias": "smp", "is_lobby": False}]
    toml = proxy_service.render_velocity_toml(cfg, backends, "smp", forwarding_mode="legacy")
    assert 'player-info-forwarding-mode = "legacy"' in toml
    assert 'player-info-forwarding-mode = "modern"' not in toml
    assert "forwarding-secret-file" not in toml           # legacy nutzt kein Secret
    assert "online-mode = true" in toml                    # Proxy authentifiziert weiter


def test_apply_legacy_forwarding_writes_spigot_yml(tmp_path):
    """Spigot-Backend: legacy schreibt spigot.yml bungeecord=true + online-mode=false + loopback."""
    import yaml

    from app.models.server import Server
    from app.services import server_service

    base = tmp_path / "spigot-backend"
    base.mkdir()
    srv = Server(name="s", slug="s", server_type="spigot", base_path=str(base),
                 gateway_enabled=True, port=30020)

    notes = server_service.apply_velocity_backend_forwarding(srv, "irrelevant", mode="legacy")
    assert any("legacy/BungeeCord" in n for n in notes)

    spig = yaml.safe_load((base / "spigot.yml").read_text(encoding="utf-8"))
    assert spig["settings"]["bungeecord"] is True

    props = (base / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=false" in props
    assert "server-ip=127.0.0.1" in props


def test_apply_legacy_forwarding_disables_paper_modern(tmp_path):
    """Paper-Backend im legacy-Netz: vorhandenes modern-velocity wird deaktiviert (kein Konflikt)."""
    import yaml

    from app.models.server import Server
    from app.services import server_service

    base = tmp_path / "paper-in-legacy"
    (base / "config").mkdir(parents=True)
    (base / "config" / "paper-global.yml").write_text(
        yaml.safe_dump({"proxies": {"velocity": {"enabled": True, "online-mode": True,
                                                  "secret": "x"}}}), encoding="utf-8")
    srv = Server(name="p", slug="p", server_type="paper", base_path=str(base),
                 gateway_enabled=True, port=30021)

    server_service.apply_velocity_backend_forwarding(srv, "x", mode="legacy")
    spig = yaml.safe_load((base / "spigot.yml").read_text(encoding="utf-8"))
    assert spig["settings"]["bungeecord"] is True
    pdata = yaml.safe_load((base / "config" / "paper-global.yml").read_text(encoding="utf-8"))
    assert pdata["proxies"]["velocity"]["enabled"] is False   # modern aus -> kein Konflikt


def test_cleanup_velocity_leftovers_reverts(tmp_path):
    """Nicht-Backends (Modded-Transfer-Ziele) muessen wieder oeffentlich + online werden."""
    from app.models.server import Server
    from app.services import server_service

    base = tmp_path / "modded"
    base.mkdir()
    (base / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\n", encoding="utf-8")
    srv = Server(name="m", slug="m", server_type="neoforge", base_path=str(base),
                 gateway_enabled=True, port=30011)

    server_service.cleanup_velocity_leftovers(srv)
    props = (base / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=true" in props
    assert "server-ip=\n" in props or props.rstrip().endswith("server-ip=")


# --- Menue-Ziele: Modded direkt, Backend ueber Proxy ---------------------------
def test_menu_targets_velocity_mode(client):
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import app_setting_service as A
    from app.services import lobby_service

    with dbs.SessionLocal() as db:
        A.set_network_domain(db, "mc.example.de")
        A.set_network_port(db, 25565)
        A.set_network_mode(db, "velocity")
        lobby = Server(name="lobby", slug="mlob", server_type="paper", mc_version="26.2",
                       base_path="C:/tmp/mlob", gateway_enabled=True, gateway_hostname="lobby",
                       gateway_is_default=True, port=30001)
        smp = Server(name="smp", slug="msmp", server_type="paper", mc_version="26.2",
                     base_path="C:/tmp/msmp", gateway_enabled=True, gateway_hostname="smp",
                     port=30002)
        atm = Server(name="atm", slug="matm", server_type="neoforge", mc_version="1.21.1",
                     base_path="C:/tmp/matm", gateway_enabled=True, gateway_hostname="atm10",
                     port=30003)
        db.add_all([lobby, smp, atm])
        db.commit()

        menu = {s["key"]: s for s in lobby_service.get_menu_servers(db, exclude_id=lobby.id)}
        # Modded -> DIREKTER Port (nativer Transfer, bypass Proxy)
        assert menu["atm10"]["port"] == 30003
        # Vanilla/Bukkit-Backend -> Proxy-Port (forced-host routet intern)
        assert menu["smp"]["port"] == 25565


def test_menu_targets_gateway_mode_unchanged(client):
    """Regression: im gateway-Modus zeigen ALLE Ziele weiter auf den network_port."""
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import app_setting_service as A
    from app.services import lobby_service

    with dbs.SessionLocal() as db:
        A.set_network_domain(db, "mc.example.de")
        A.set_network_port(db, 25565)
        A.set_network_mode(db, "gateway")
        lobby = Server(name="lobby", slug="glob", server_type="paper", mc_version="1.21.1",
                       base_path="C:/tmp/glob", gateway_enabled=True, gateway_hostname="lobby",
                       gateway_is_default=True, port=30001)
        atm = Server(name="atm", slug="gatm", server_type="forge", mc_version="1.20.1",
                     base_path="C:/tmp/gatm", gateway_enabled=True, gateway_hostname="atm10",
                     port=30003)
        db.add_all([lobby, atm])
        db.commit()

        menu = {s["key"]: s for s in lobby_service.get_menu_servers(db, exclude_id=lobby.id)}
        assert menu["atm10"]["port"] == 25565   # gateway: ueber den network_port


# --- Runtime-Config + Secret ---------------------------------------------------
def test_velocity_config_runtime_enabled_and_secret(client):
    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "velocity")

    cfg = A.get_velocity_config_runtime()
    assert cfg["enabled"] is True
    assert cfg["secret"]                       # wird beim ersten Abruf erzeugt
    assert len(cfg["secret"]) >= 16

    with dbs.SessionLocal() as db:
        # Zweiter Abruf -> selbes Secret (persistiert, nicht neu erzeugt)
        assert A.get_velocity_forwarding_secret(db) == cfg["secret"]


def test_velocity_config_runtime_disabled_off_mode(client):
    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "off")
    cfg = A.get_velocity_config_runtime()
    assert cfg["enabled"] is False


# --- UNIVERSAL-Routing (Gateway vorn, Velocity intern) -------------------------
def test_needs_velocity_vanilla_gating():
    """UNIVERSAL-Modus: nicht-767-Joins -> Velocity; 767 + Status + gateway-Modus nicht."""
    from types import SimpleNamespace

    from app.services import gateway_service as gw
    from app.services.mc_protocol import JOIN_NEXT_STATES

    routes = gw.GatewayRoutes(velocity_enabled=True, velocity_internal_port=25605)
    join = next(iter(JOIN_NEXT_STATES))
    assert gw._needs_velocity_vanilla(routes, SimpleNamespace(protocol_version=774, next_state=join)) is True
    assert gw._needs_velocity_vanilla(routes, SimpleNamespace(protocol_version=767, next_state=join)) is False
    off = gw.GatewayRoutes(velocity_enabled=False, velocity_internal_port=25605)
    assert gw._needs_velocity_vanilla(off, SimpleNamespace(protocol_version=774, next_state=join)) is False
    assert gw._needs_velocity_vanilla(routes, SimpleNamespace(protocol_version=774, next_state=999)) is False


def test_build_gateway_routes_velocity_vs_gateway(client):
    import app.db.session as dbs
    from app.services import app_setting_service as A
    from app.services import gateway_service as gw

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "velocity")
        A.set_velocity_internal_port(db, 25605)
        routes = gw.build_gateway_routes(db)
    assert routes.velocity_enabled is True and routes.velocity_internal_port == 25605

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "gateway")
        routes = gw.build_gateway_routes(db)
    assert routes.velocity_enabled is False and routes.velocity_internal_port == 0


def test_velocity_backend_ids_only_bukkit(client):
    """Alias auf ein Bukkit-Backend (Lobby) laeuft ueber Velocity; modded-Server nicht."""
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import app_setting_service as A
    from app.services import gateway_service as gw

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "velocity")
        A.set_network_domain(db, "mc.example.de")
        lobby = Server(name="lobby", slug="vbid_l", server_type="paper", mc_version="26.2",
                       base_path="C:/tmp/vbidl", gateway_enabled=True, gateway_hostname="lobby",
                       gateway_is_default=True, port=25569)
        atm = Server(name="atm", slug="vbid_a", server_type="neoforge", mc_version="1.21.1",
                     base_path="C:/tmp/vbida", gateway_enabled=True, gateway_hostname="atm10",
                     port=25590)
        db.add_all([lobby, atm])
        db.commit()
        routes = gw.build_gateway_routes(db)
        assert lobby.id in routes.velocity_backend_ids      # Bukkit -> ueber Velocity
        assert atm.id not in routes.velocity_backend_ids    # modded -> nativ/Hub


# --- UI ------------------------------------------------------------------------
def test_settings_page_shows_velocity_button(client):
    _login(client)
    resp = client.get("/settings")
    assert resp.status_code == 200
    assert 'action="/settings/velocity/auto-create"' in resp.text
    assert '<option value="velocity"' in resp.text


def test_apply_lobby_properties(tmp_path):
    """Lobby-Properties: Adventure + friedlich gesetzt, fremde Keys bleiben, level-name gelesen."""
    from app.services import lobby_service

    (tmp_path / "server.properties").write_text(
        "level-name=lobby\ngamemode=survival\nmotd=hi\n", encoding="utf-8")
    lobby_service._apply_lobby_properties(tmp_path)
    txt = (tmp_path / "server.properties").read_text(encoding="utf-8")
    assert "gamemode=adventure" in txt and "gamemode=survival" not in txt
    assert "pvp=false" in txt and "difficulty=peaceful" in txt
    assert "motd=hi" in txt                       # unbeteiligte Keys bleiben erhalten
    assert lobby_service._level_name(tmp_path) == "lobby"


def test_deploy_lobby_world_missing_asset(monkeypatch, tmp_path):
    """Fehlt die Welt im Asset-Ordner, gibt es einen klaren Fehler (kein Crash)."""
    from app.services import lobby_service

    monkeypatch.setattr(lobby_service, "_LOBBY_WORLD_ASSET", tmp_path / "nope")
    ok, msg = lobby_service.deploy_lobby_world(None)   # db unbenutzt, weil Asset zuerst geprueft wird
    assert ok is False and "Keine Lobby-Welt" in msg


def test_config_version_kommt_aus_dem_jar(tmp_path):
    """Fest eingetragen lief sie zwangslaeufig auseinander.

    Der Manager schrieb "2.7", das mitgelieferte Velocity 3.5.1 erwartet "2.8". Eine
    aeltere Version schickt die Datei bei jedem Start durch Velocitys Migration, statt
    sie so zu lesen, wie sie dasteht.
    """
    import zipfile

    from app.services import proxy_service

    jar = tmp_path / "velocity.jar"
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr("default-velocity.toml",
                   '# Kommentar\nconfig-version = "3.1"\n')

    assert proxy_service.velocity_config_version(jar) == "3.1"


def test_config_version_faellt_sauber_zurueck(tmp_path):
    """Ohne lesbares Jar darf nichts krachen - der Start haengt daran."""
    from app.services import proxy_service

    assert proxy_service.velocity_config_version(tmp_path / "fehlt.jar") == "2.8"
    kaputt = tmp_path / "kaputt.jar"
    kaputt.write_bytes(b"kein zip")
    assert proxy_service.velocity_config_version(kaputt) == "2.8"


def test_render_nimmt_die_uebergebene_config_version():
    from app.services import proxy_service

    cfg = {"bind_port": 25605, "domain": "mc.example.de", "motd": "x",
           "max_players": 100, "config_version": "9.9"}
    toml = proxy_service.render_velocity_toml(cfg, [], None)

    assert 'config-version = "9.9"' in toml


# ------------------------------------------------- Jar folgt der Netzwerkversion

def _jar_umgebung(tmp_path, monkeypatch, *, netz="26.3", marke=None, jar=True):
    """Arbeitsordner mit (optional) vorhandenem Jar und Versionsmarke."""
    from app.services import proxy_service

    monkeypatch.setattr(proxy_service, "_work_dir", lambda: tmp_path)
    monkeypatch.setattr(proxy_service, "_default_jar_path", lambda: tmp_path / "velocity.jar")
    monkeypatch.setattr(proxy_service, "_netz_version", lambda: netz)
    monkeypatch.setattr(proxy_service, "_JAR_VERSUCH", {})
    if jar:
        (tmp_path / "velocity.jar").write_bytes(b"altes jar")
    if marke is not None:
        (tmp_path / "velocity.jar.mcversion").write_text(marke, encoding="utf-8")
    return proxy_service


def test_jar_bleibt_wenn_die_version_passt(tmp_path, monkeypatch):
    ps = _jar_umgebung(tmp_path, monkeypatch, netz="26.3", marke="26.3")
    geladen = []
    monkeypatch.setattr(ps, "download_velocity_jar", lambda *a, **k: geladen.append(1))

    ps._resolve_jar({})

    assert geladen == [], "Es wurde ohne Not neu geladen"


def test_jar_wird_bei_versionswechsel_neu_geladen(tmp_path, monkeypatch):
    """Der eigentliche Fehler: der Proxy blieb auf seiner alten Minecraft-Version."""
    ps = _jar_umgebung(tmp_path, monkeypatch, netz="26.3", marke="26.2")
    gerufen = []

    def _laden(ziel, version=""):
        gerufen.append(version)
        Path(ziel).write_bytes(b"neues jar")
        return Path(ziel)

    monkeypatch.setattr(ps, "download_velocity_jar", _laden)

    pfad = ps._resolve_jar({})

    assert len(gerufen) == 1
    assert Path(pfad).read_bytes() == b"neues jar"
    assert (tmp_path / "velocity.jar.mcversion").read_text(encoding="utf-8") == "26.3"


def test_ohne_marke_wird_einmal_nachgeladen(tmp_path, monkeypatch):
    """Ein Jar aus der Zeit vor dieser Reparatur hat noch keine Marke."""
    ps = _jar_umgebung(tmp_path, monkeypatch, netz="26.3", marke=None)
    gerufen = []
    monkeypatch.setattr(ps, "download_velocity_jar",
                        lambda ziel, version="": (gerufen.append(1), Path(ziel))[1])

    ps._resolve_jar({})

    assert len(gerufen) == 1


def test_fehlgeschlagener_download_behaelt_das_alte_jar(tmp_path, monkeypatch):
    """Lieber ein veralteter Proxy als gar keiner - aber es muss im Protokoll stehen."""
    ps = _jar_umgebung(tmp_path, monkeypatch, netz="26.3", marke="26.2")
    monkeypatch.setattr(ps, "download_velocity_jar", lambda *a, **k: None)
    meldungen = []
    monkeypatch.setattr(ps, "_glog", lambda ereignis, text="": meldungen.append(ereignis))

    pfad = ps._resolve_jar({})

    assert Path(pfad).read_bytes() == b"altes jar"
    assert "jar_update_failed" in meldungen
    # Marke NICHT gesetzt - sonst waere der naechste Versuch fuer immer verloren.
    assert not (tmp_path / "velocity.jar.mcversion").read_text(encoding="utf-8") == "26.3"


def test_fehlschlag_wird_nicht_alle_15_sekunden_wiederholt(tmp_path, monkeypatch):
    """Der Reconcile laeuft im Sekundentakt - ohne Bremse waere das Dauerfeuer."""
    ps = _jar_umgebung(tmp_path, monkeypatch, netz="26.3", marke="26.2")
    versuche = []
    monkeypatch.setattr(ps, "download_velocity_jar",
                        lambda *a, **k: versuche.append(1) or None)

    ps._resolve_jar({})
    ps._resolve_jar({})
    ps._resolve_jar({})

    assert len(versuche) == 1, f"{len(versuche)} Versuche statt einem"


def test_ausdruecklich_gesetztes_jar_wird_nie_angefasst(tmp_path, monkeypatch):
    """Wer einen Pfad eintraegt, bestimmt selbst - da laden wir nichts nach."""
    ps = _jar_umgebung(tmp_path, monkeypatch, netz="26.3", marke="26.2")
    eigenes = tmp_path / "eigenes.jar"
    eigenes.write_bytes(b"meins")
    geladen = []
    monkeypatch.setattr(ps, "download_velocity_jar", lambda *a, **k: geladen.append(1))

    pfad = ps._resolve_jar({"jar": str(eigenes)})

    assert pfad == str(eigenes)
    assert geladen == []


def test_ohne_netzversion_wird_nichts_erneuert(tmp_path, monkeypatch):
    """Keine Lobby, keine Zielversion - dann gibt es auch keinen Grund zu laden."""
    ps = _jar_umgebung(tmp_path, monkeypatch, netz="", marke="26.2")
    geladen = []
    monkeypatch.setattr(ps, "download_velocity_jar", lambda *a, **k: geladen.append(1))

    ps._resolve_jar({})

    assert geladen == []


# ------------------------------------------------- Java-Bedarf aus dem Jar

def _jar_mit_klasse(tmp_path, name, klassen_version, *, mit_manifest=True):
    """Ein Mini-Jar mit einer .class-Datei der gewuenschten Klassendatei-Version."""
    import zipfile

    kopf = b"\xca\xfe\xba\xbe" + (0).to_bytes(2, "big") + klassen_version.to_bytes(2, "big")
    jar = tmp_path / f"test-{klassen_version}.jar"
    with zipfile.ZipFile(jar, "w") as z:
        if mit_manifest:
            z.writestr("META-INF/MANIFEST.MF",
                       "Manifest-Version: 1.0\nMain-Class: " + name.replace("/", ".") + "\n")
        z.writestr(name + ".class", kopf + b"restliche bytes")
    return jar


@pytest.mark.parametrize("klassen_version,java", [
    (61, 17),   # Java 17
    (65, 21),   # Java 21
    (67, 23),   # Java 23
    (69, 25),   # Java 25 - daran ist Velocity 4.3.1 gescheitert
])
def test_java_bedarf_kommt_aus_dem_jar(tmp_path, klassen_version, java):
    from app.services.proxy_service import java_major_fuer_jar

    jar = _jar_mit_klasse(tmp_path, "com/beispiel/Haupt", klassen_version)

    assert java_major_fuer_jar(jar) == java


def test_java_bedarf_faellt_nie_unter_das_mindestmass(tmp_path):
    """Velocity 3.x braucht mindestens 17 - eine alte Klasse darf das nicht unterbieten."""
    from app.services.proxy_service import java_major_fuer_jar

    assert java_major_fuer_jar(_jar_mit_klasse(tmp_path, "a/B", 52)) == 17


def test_java_bedarf_ohne_manifest(tmp_path):
    """Ohne Main-Class wird irgendeine normale Klasse genommen."""
    from app.services.proxy_service import java_major_fuer_jar

    jar = _jar_mit_klasse(tmp_path, "irgend/Wo", 69, mit_manifest=False)

    assert java_major_fuer_jar(jar) == 25


def test_java_bedarf_ignoriert_mehrfachfassungen(tmp_path):
    """META-INF/versions/ sind Zusatzfassungen fuer neuere Laufzeiten - KEINE Voraussetzung.

    Wer die mitzaehlt, verlangt ein Java, das das Jar gar nicht braucht.
    """
    import zipfile

    from app.services.proxy_service import java_major_fuer_jar

    def _kopf(v):
        return b"\xca\xfe\xba\xbe" + (0).to_bytes(2, "big") + v.to_bytes(2, "big") + b"xx"

    jar = tmp_path / "mehrfach.jar"
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr("com/beispiel/Haupt.class", _kopf(61))          # Java 17
        z.writestr("META-INF/versions/25/com/beispiel/Haupt.class", _kopf(69))

    assert java_major_fuer_jar(jar) == 17


def test_java_bedarf_bei_kaputtem_jar(tmp_path):
    from app.services.proxy_service import java_major_fuer_jar

    kaputt = tmp_path / "kaputt.jar"
    kaputt.write_bytes(b"kein zip")

    assert java_major_fuer_jar(kaputt) == 17
    assert java_major_fuer_jar(tmp_path / "fehlt.jar") == 17


# ------------------------------------------------- Hoechste Version im Netz

@pytest.mark.parametrize("a,b", [
    ("26.3", "26.2"),
    ("26.10", "26.2"),     # alphabetisch waere das falschherum
    ("26.2", "1.21.11"),
    ("1.21.11", "1.21.9"),
])
def test_versionsrang_zaehlt_zahlen_nicht_buchstaben(a, b):
    from app.services.proxy_service import _version_rang

    assert _version_rang(a) > _version_rang(b)


def test_netzversion_nimmt_den_hoechsten_backend(client, tmp_path):
    """Nicht die Lobby: ein 26.3-Client kommt sonst nicht herein, weil der Proxy
    fuer die 26.2 der Lobby geholt wurde."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import proxy_service

    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby 26.2", slug="nv-lobby", server_type="paper",
                   mc_version="26.2", base_path=str(tmp_path / "a"), port=25569,
                   gateway_enabled=True, gateway_hostname="lobby", gateway_is_default=True),
            Server(name="Paper 26.3", slug="nv-paper", server_type="paper",
                   mc_version="26.3", base_path=str(tmp_path / "b"), port=25573,
                   gateway_enabled=True, gateway_hostname="neu"),
            # Kein Velocity-Backend -> zaehlt nicht mit.
            Server(name="Spigot 99.9", slug="nv-spigot", server_type="spigot",
                   mc_version="99.9", base_path=str(tmp_path / "c"), port=25591,
                   gateway_enabled=True, gateway_hostname="alt"),
        ])
        db.commit()

    assert proxy_service._netz_version() == "26.3"


# ------------------------------------------------- ping-passthrough je Jar-Fassung

def _jar_mit_vorlage(tmp_path, vorlage, name="velocity.jar"):
    import zipfile

    jar = tmp_path / name
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr("default-velocity.toml", vorlage)
    return jar


VORLAGE_V3 = (
    'config-version = "2.8"\n'
    'bind = "0.0.0.0:25565"\n'
    'ping-passthrough = "DISABLED"\n'
    "[advanced]\n"
    "read-timeout = 30000\n"
)

VORLAGE_V4 = (
    'config-version = "2.9"\n'
    'bind = "0.0.0.0:25565"\n'
    "[ping-passthrough]\n"
    "version = false\n"
    "players = false\n"
    "description = false\n"
    "favicon = false\n"
    "modinfo = false\n"
    "[advanced]\n"
    "read-timeout = 30000\n"
)


def test_schreibweise_wird_am_jar_abgelesen(tmp_path):
    """Velocity 3 will einen Schluessel, Velocity 4 einen Abschnitt."""
    from app.services.proxy_service import ping_passthrough_als_abschnitt

    assert ping_passthrough_als_abschnitt(_jar_mit_vorlage(tmp_path, VORLAGE_V3, "v3.jar")) is False
    assert ping_passthrough_als_abschnitt(_jar_mit_vorlage(tmp_path, VORLAGE_V4, "v4.jar")) is True


def test_ohne_jar_bleibt_es_bei_der_alten_schreibweise(tmp_path):
    from app.services.proxy_service import ping_passthrough_als_abschnitt

    assert ping_passthrough_als_abschnitt(tmp_path / "fehlt.jar") is False


def test_velocity4_bekommt_den_abschnitt_und_keinen_schluessel():
    """Die alte Form laesst Velocity 4 beim Lesen der Konfiguration komplett abbrechen:
    "ClassCastException: String cannot be cast to ...Config" - und der Proxy startet nicht."""
    from app.services import proxy_service

    cfg = {"bind_port": 25605, "domain": "mc.example.de", "motd": "x", "max_players": 100,
           "config_version": "2.9", "ping_passthrough_abschnitt": True}
    toml = proxy_service.render_velocity_toml(cfg, [], None)

    assert "[ping-passthrough]" in toml
    assert 'ping-passthrough = "DISABLED"' not in toml
    # Alle fuenf Schalter aus - das ist dasselbe wie das alte DISABLED.
    abschnitt = toml.split("[ping-passthrough]", 1)[1].split("[", 1)[0]
    for schalter in ("version", "players", "description", "favicon", "modinfo"):
        assert f"{schalter} = false" in abschnitt, schalter


def test_velocity3_bekommt_weiterhin_den_schluessel():
    from app.services import proxy_service

    cfg = {"bind_port": 25605, "domain": "mc.example.de", "motd": "x", "max_players": 100,
           "config_version": "2.8", "ping_passthrough_abschnitt": False}
    toml = proxy_service.render_velocity_toml(cfg, [], None)

    assert 'ping-passthrough = "DISABLED"' in toml
    assert "[ping-passthrough]" not in toml


def test_kein_schluessel_steht_ausserhalb_der_vorlage(tmp_path):
    """Regressionsbremse: was der Manager schreibt, muss das Jar auch kennen.

    Genau daran ist der Start gescheitert - ein Schluessel, den es in der neuen Fassung
    so nicht mehr gab.
    """
    from app.services import proxy_service

    def _schluessel(text):
        raus, abschnitt = {}, ""
        for roh in text.splitlines():
            z = roh.strip()
            if not z or z.startswith("#"):
                continue
            if z.startswith("["):
                abschnitt = z.strip("[]")
                raus.setdefault(abschnitt, set())
                continue
            if "=" in z:
                raus.setdefault(abschnitt, set()).add(z.split("=", 1)[0].strip().strip('"'))
        return raus

    vorlage = VORLAGE_V4 + "accepts-transfers = false\nbungee-plugin-message-channel = true\n"
    cfg = {"bind_port": 25605, "domain": "mc.example.de", "motd": "x", "max_players": 100,
           "config_version": "2.9", "ping_passthrough_abschnitt": True}
    erzeugt = _schluessel(proxy_service.render_velocity_toml(cfg, [], None))
    bekannt = _schluessel(vorlage)
    # Alles ausser den frei waehlbaren Namen in [servers]/[forced-hosts].
    for abschnitt in ("", "ping-passthrough", "advanced"):
        unbekannt = erzeugt.get(abschnitt, set()) - bekannt.get(abschnitt, set())
        # "query" und die Namensabschnitte bleiben aussen vor; die Vorlage hier ist
        # absichtlich kurz, deshalb nur die Abschnitte pruefen, die sie fuehrt.
        if abschnitt == "":
            unbekannt -= {"motd", "show-max-players", "online-mode",
                          "force-key-authentication", "prevent-client-proxy-connections",
                          "player-info-forwarding-mode", "forwarding-secret-file",
                          "announce-forge", "kick-existing-players"}
        assert not unbekannt, f"[{abschnitt}]: {sorted(unbekannt)}"


# ------------------------------------------------- Via-Plugins duerfen nicht still veralten

def test_fehlgeschlagener_via_download_wird_gemeldet(monkeypatch):
    """Vorher wurde die Rueckgabe weggeworfen - der Uebersetzer hing dem Netz hinterher
    und niemand erfuhr davon."""
    from app.services import proxy_service, viaversion_service

    monkeypatch.setattr(viaversion_service, "install_velocity_plugins",
                        lambda ordner, version: (False, "Velocity-Via-Download fehlgeschlagen (viaversion)."))
    monkeypatch.setattr("app.providers.server.common.offline_mode_enabled", lambda: False)
    gemeldet = []
    monkeypatch.setattr(proxy_service, "_glog",
                        lambda ereignis, text="": gemeldet.append((ereignis, text)))

    proxy_service._install_via_plugins("26.3")

    assert any(e == "via_veraltet" for e, _ in gemeldet), gemeldet
    assert any("26.3" in txt for _, txt in gemeldet)


def test_teilweise_geladene_via_plugins_werden_gemeldet(monkeypatch):
    from app.services import proxy_service, viaversion_service

    monkeypatch.setattr(viaversion_service, "install_velocity_plugins",
                        lambda ordner, version: (True, "Velocity-Via installiert: A. Nicht geladen: viarewind."))
    monkeypatch.setattr("app.providers.server.common.offline_mode_enabled", lambda: False)
    gemeldet = []
    monkeypatch.setattr(proxy_service, "_glog",
                        lambda ereignis, text="": gemeldet.append((ereignis, text)))

    proxy_service._install_via_plugins("26.3")

    assert any(e == "via_teilweise" for e, _ in gemeldet), gemeldet


def test_vollstaendiger_via_download_meldet_nichts(monkeypatch):
    """Ueberzumelden waere auch falsch - der Start laeuft alle paar Sekunden."""
    from app.services import proxy_service, viaversion_service

    monkeypatch.setattr(viaversion_service, "install_velocity_plugins",
                        lambda ordner, version: (True, "Velocity-Via installiert: A, B, C."))
    monkeypatch.setattr("app.providers.server.common.offline_mode_enabled", lambda: False)
    gemeldet = []
    monkeypatch.setattr(proxy_service, "_glog",
                        lambda ereignis, text="": gemeldet.append((ereignis, text)))

    proxy_service._install_via_plugins("26.3")

    assert gemeldet == []


def test_via_plugins_folgen_der_netzversion(client, tmp_path, monkeypatch):
    """Nicht der Lobby-Version: die Uebersetzer muessen zu dem passen, was der Proxy
    tatsaechlich bedient."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import proxy_service

    with SessionLocal() as db:
        db.add_all([
            Server(name="Via Lobby", slug="via-lobby", server_type="paper",
                   mc_version="26.2", base_path=str(tmp_path / "a"), port=25569,
                   gateway_enabled=True, gateway_hostname="lobby", gateway_is_default=True),
            Server(name="Via Neu", slug="via-neu", server_type="paper",
                   mc_version="26.3", base_path=str(tmp_path / "b"), port=25573,
                   gateway_enabled=True, gateway_hostname="neu"),
        ])
        db.commit()

    gesehen = []
    monkeypatch.setattr(proxy_service, "_work_dir", lambda: tmp_path)
    monkeypatch.setattr(proxy_service, "render_velocity_toml",
                        lambda *a, **k: "config-version = \"2.9\"\n")
    monkeypatch.setattr(proxy_service, "_install_via_plugins", lambda v: gesehen.append(v))

    _sig, _namen, mc_version = proxy_service._write_runtime_config(
        {"bind_port": 25605, "domain": "mc.example.de", "motd": "x",
         "max_players": 100, "secret": ""})

    assert mc_version == "26.3", f"Via wuerde fuer {mc_version} geladen"
