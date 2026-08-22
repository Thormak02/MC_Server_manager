"""ViaProxy-Uebersetzungsstufe (Option A: VOR Dispatcher/Hub, Loopback ins Gateway)."""

from __future__ import annotations

from app.services import viaproxy_service


def test_render_config_option_a():
    yml = viaproxy_service.render_config(
        {"port": 25601, "target_port": 25565, "target_version": "1.21.1"}
    )
    assert "bind-address: 127.0.0.1:25601" in yml       # intern, nicht oeffentlich
    assert "target-address: 127.0.0.1:25565" in yml     # ZURUECK ins Gateway (Loopback)
    assert "target-version: 1.21.1" in yml
    assert "rewrite-handshake-packet: false" in yml     # Original-Host durchreichen -> Routing bleibt
    assert "rewrite-transfer-packets: true" in yml      # Transfer-Emulation (auch <1.20.5)
    assert "proxy-online-mode: false" in yml


def test_viaproxy_config_targets_gateway_port(client):
    """target_port muss der GATEWAY-Port sein (Loopback), NICHT der Hub-Vanilla-Port."""
    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        A.set_network_port(db, 25599)
    cfg = A.get_viaproxy_config_runtime()
    assert cfg["target_port"] == 25599


def test_needs_viaproxy_translation():
    """Gateway-Weiche: nur nicht-767-JOINS bei aktivem ViaProxy werden uebersetzt."""
    from types import SimpleNamespace

    import app.services.gateway_service as gw
    import app.services.mc_protocol as mp

    on = SimpleNamespace(viaproxy_enabled=True, viaproxy_port=25601)
    off = SimpleNamespace(viaproxy_enabled=False, viaproxy_port=0)

    def hs(proto, ns):
        return SimpleNamespace(protocol_version=proto, next_state=ns)

    login = mp.NEXT_STATE_LOGIN
    status = mp.NEXT_STATE_STATUS

    assert gw._needs_viaproxy_translation(on, hs(770, login)) is True   # neuer Client -> uebersetzen
    assert gw._needs_viaproxy_translation(on, hs(47, login)) is True    # 1.8 -> uebersetzen
    assert gw._needs_viaproxy_translation(on, hs(767, login)) is False  # 1.21.1 -> nativer Pfad
    assert gw._needs_viaproxy_translation(on, hs(770, status)) is False # Status-Ping -> nicht
    assert gw._needs_viaproxy_translation(off, hs(770, login)) is False # ViaProxy aus -> nicht
    assert gw._needs_viaproxy_translation(on, hs(None, login)) is False # unbekannt -> nicht


def test_pick_jar_asset_by_java_version():
    """Java 21 -> regulaerer Build; Java 8 -> +java8-Build (der auf 21 crasht)."""
    cands = [{"name": "ViaProxy-3.4.12.jar"}, {"name": "ViaProxy-3.4.12+java8.jar"}]
    assert viaproxy_service._pick_jar_asset(cands, 21)["name"] == "ViaProxy-3.4.12.jar"
    assert viaproxy_service._pick_jar_asset(cands, 8)["name"] == "ViaProxy-3.4.12+java8.jar"
    assert viaproxy_service._pick_jar_asset(cands, None)["name"] == "ViaProxy-3.4.12.jar"  # unbekannt -> regulaer


def test_java_major_version_parsing(monkeypatch):
    import subprocess
    from types import SimpleNamespace

    def make(text):
        return lambda *a, **k: SimpleNamespace(stderr=text, stdout="")

    monkeypatch.setattr(subprocess, "run", make('openjdk version "21.0.6" 2025-01-21'))
    assert viaproxy_service._java_major_version("java") == 21
    monkeypatch.setattr(subprocess, "run", make('openjdk version "1.8.0_482"'))
    assert viaproxy_service._java_major_version("java") == 8
    monkeypatch.setattr(subprocess, "run", make("garbage"))
    assert viaproxy_service._java_major_version("java") is None


def test_maybe_refetch_wrong_jar(monkeypatch, tmp_path):
    """Selbstheilung: falscher (+java8) Auto-Jar wird auf Java 17+ nach Crash EINMAL verworfen."""
    V = viaproxy_service
    jarp = tmp_path / "ViaProxy.jar"
    jarp.write_bytes(b"fake +java8 jar")
    monkeypatch.setattr(V, "_default_jar_path", lambda: jarp)
    monkeypatch.setattr(V, "_glog", lambda *a, **k: None)
    V._jar_refetch_done = False
    V._DOWNLOAD_TRIED = True
    V._last_start_mono = 123.0

    monkeypatch.setattr(V, "_java_major_version", lambda j: 21)     # Java 21 -> verwerfen
    V._maybe_refetch_wrong_jar({"java": "java", "jar": ""})
    assert not jarp.exists()
    assert V._DOWNLOAD_TRIED is False
    assert V._last_start_mono == 0.0
    assert V._jar_refetch_done is True

    jarp.write_bytes(b"again")                                       # 2. Aufruf -> no-op (kein Loop)
    V._maybe_refetch_wrong_jar({"java": "java", "jar": ""})
    assert jarp.exists()

    V._jar_refetch_done = False
    monkeypatch.setattr(V, "_java_major_version", lambda j: 8)      # Java 8 -> +java8 ist ok
    V._maybe_refetch_wrong_jar({"java": "java", "jar": ""})
    assert jarp.exists()

    V._jar_refetch_done = False
    monkeypatch.setattr(V, "_java_major_version", lambda j: 21)
    V._maybe_refetch_wrong_jar({"java": "java", "jar": r"C:\custom\ViaProxy.jar"})  # manuell -> nicht anfassen
    assert jarp.exists()
